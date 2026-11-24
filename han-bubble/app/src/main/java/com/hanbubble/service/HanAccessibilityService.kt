package com.hanbubble.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.hanbubble.Prefs
import com.hanbubble.R
import com.hanbubble.ai.HanAi
import com.hanbubble.ai.HanException
import com.hanbubble.ai.SentenceTracker
import com.hanbubble.ai.TranslateRequest
import com.hanbubble.data.HistoryStore
import com.hanbubble.ui.Speaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Giữ bong bóng nổi; khi BẬT thì theo dõi chữ trong ô nhập đang gõ ở mọi app,
 * mỗi câu viết xong đem đi dịch sang tiếng Hàn, hiện lên khung dịch và lưu vào Sổ học.
 */
class HanAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    BubbleController.Listener,
    TranslationBox.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private lateinit var store: HistoryStore
    private var bubble: BubbleController? = null
    private var box: TranslationBox? = null
    private var speaker: Speaker? = null

    /** Công tắc trên bong bóng: chỉ khi BẬT app mới đọc chữ và gọi AI. */
    private var active = false
    private val tracker = SentenceTracker()
    private val queue = ArrayDeque<Pending>()
    private var worker: Job? = null

    private var currentField: String? = null
    private val shortPause = Runnable { currentField?.let { enqueue(tracker.onPause(it, includeTail = false)) } }
    private val longPause = Runnable { currentField?.let { enqueue(tracker.onPause(it, includeTail = true)) } }

    /** Tên app (gói) của từng ô nhập, để ghi vào Sổ học. */
    private val fieldApps = HashMap<String, String>()

    private data class Pending(val task: SentenceTracker.Task, val app: String?)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        store = HistoryStore.get(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        val themed = ContextThemeWrapper(this, R.style.Theme_HanBubble_Overlay)
        bubble = BubbleController(this, themed, prefs, this)
        box = TranslationBox(this, themed, prefs, this)
        speaker = Speaker(this)
        if (prefs.bubbleEnabled) bubble?.show()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != Prefs.KEY_BUBBLE) return
        if (prefs.bubbleEnabled) {
            bubble?.show()
        } else {
            setActive(false)
            box?.destroy()
            bubble?.hide()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || event.isPassword) return
        val node = event.source
        if (node?.isPassword == true) return
        val text = if (node != null && node.isShowingHintText) "" else node?.text?.toString() ?: event.text.joinToString("")
        val field = "$pkg:${node?.viewIdResourceName ?: event.className}"
        currentField = field
        fieldApps[field] = pkg

        enqueue(tracker.onText(field, text))
        // Ngừng gõ một chút: dịch câu đã có dấu kết thúc. Ngừng lâu hơn: dịch cả câu còn dở.
        main.removeCallbacks(shortPause)
        main.removeCallbacks(longPause)
        if (text.isNotBlank()) {
            main.postDelayed(shortPause, SHORT_PAUSE_MS)
            main.postDelayed(longPause, LONG_PAUSE_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance === this) instance = null
        main.removeCallbacksAndMessages(null)
        worker?.cancel()
        queue.clear()
        active = false
        box?.destroy()
        bubble?.hide()
        speaker?.shutdown()
        if (::prefs.isInitialized) prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
    }

    // ---- Bong bóng & khung dịch ----

    override fun onBubbleTap() = setActive(!active)

    override fun onBubbleLongPress() {
        val b = box ?: return
        if (!active) {
            setActive(true)
            return
        }
        if (b.hasResult) b.reveal() else toast("Chưa có câu nào. Cứ nhắn tin như bình thường nhé!")
    }

    override fun onSpeak(text: String) {
        speaker?.speak(text)
    }

    override fun onClose() {
        box?.hide()
    }

    private fun setActive(on: Boolean) {
        active = on
        if (on) {
            bubble?.setState(BubbleState.ON)
            toast("Hàn Bubble đã BẬT — gõ câu nào dịch câu đó 🇰🇷")
        } else {
            main.removeCallbacks(shortPause)
            main.removeCallbacks(longPause)
            worker?.cancel()
            queue.clear()
            box?.hide()
            bubble?.setState(BubbleState.OFF)
        }
    }

    // ---- Hàng đợi dịch ----

    private fun enqueue(tasks: List<SentenceTracker.Task>) {
        if (!active || tasks.isEmpty()) return
        tasks.forEach { queue.addLast(Pending(it, fieldApps[it.field])) }
        if (worker?.isActive == true) return
        worker = scope.launch { drain() }
    }

    /** Dịch lần lượt từng câu trong hàng đợi (chạy trên luồng chính, gọi AI ở luồng nền). */
    private suspend fun drain() {
        while (active) {
            val next = queue.removeFirstOrNull() ?: break
            val task = next.task
            bubble?.setState(BubbleState.LOADING)
            box?.showLoading(task.text, queue.size)
            try {
                val key = prefs.apiKey
                if (key.isBlank()) throw HanException("Chưa có API key. Mở app Hàn Bubble và dán API key vào mục 1 nhé.")
                val request = TranslateRequest(task.text, appName = next.app?.let(::appLabel), style = prefs.style)
                val model = prefs.model
                val result = withContext(Dispatchers.IO) { HanAi.get(key, model).translate(request) }
                val now = System.currentTimeMillis()
                val id = withContext(Dispatchers.IO) {
                    val old = task.replacesEntry
                    if (old != null && store.update(old, result, now)) old else store.insert(next.app?.let(::appLabel), result, now)
                }
                tracker.bind(task.field, task.text, id)
                box?.showResult(result, queue.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HanException) {
                box?.showError(e.message ?: "Có lỗi xảy ra.")
            } catch (e: Exception) {
                box?.showError("Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                bubble?.setState(if (active) BubbleState.ON else BubbleState.OFF)
            }
        }
    }

    private fun appLabel(pkg: String): String = KNOWN_APPS[pkg] ?: pkg

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val SHORT_PAUSE_MS = 1200L
        private const val LONG_PAUSE_MS = 3000L

        private val KNOWN_APPS = mapOf(
            "com.facebook.orca" to "Messenger",
            "com.facebook.katana" to "Facebook",
            "com.zing.zalo" to "Zalo",
            "com.instagram.android" to "Instagram",
            "org.telegram.messenger" to "Telegram",
            "com.whatsapp" to "WhatsApp",
            "com.google.android.apps.messaging" to "Tin nhắn",
            "com.samsung.android.messaging" to "Tin nhắn Samsung",
            "com.kakao.talk" to "KakaoTalk",
            "jp.naver.line.android" to "LINE",
            "com.discord" to "Discord",
            "com.google.android.gm" to "Gmail",
            "com.google.android.keep" to "Google Keep",
            "com.google.android.apps.docs.editors.docs" to "Google Docs",
            "com.samsung.android.app.notes" to "Samsung Notes",
        )

        @Volatile
        var instance: HanAccessibilityService? = null
            private set
    }
}
