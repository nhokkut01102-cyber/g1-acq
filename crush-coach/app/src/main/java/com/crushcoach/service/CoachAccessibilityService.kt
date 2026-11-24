package com.crushcoach.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.crushcoach.Prefs
import com.crushcoach.R
import com.crushcoach.ai.CoachException
import com.crushcoach.ai.CoachRequest
import com.crushcoach.ai.CoachResult
import com.crushcoach.ai.CrushAi
import com.crushcoach.ai.IncomingDetector
import com.crushcoach.ai.Side
import com.crushcoach.memory.DigestScheduler
import com.crushcoach.memory.MemoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Trái tim của app: giữ bong bóng nổi, theo dõi màn hình chat khi đang BẬT,
 * gọi AI và hiện bảng gợi ý.
 */
class CoachAccessibilityService : AccessibilityService(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    BubbleController.Listener,
    PanelController.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private lateinit var prefs: Prefs
    private lateinit var store: MemoryStore
    private var bubble: BubbleController? = null
    private var panel: PanelController? = null

    /** Công tắc trên bong bóng: chỉ khi BẬT app mới đọc màn hình và gọi AI. */
    private var active = false
    private var job: Job? = null
    private val detector = IncomingDetector()
    private var lastAutoAt = 0L
    private var lastResult: CoachResult? = null
    /** Màn hình chat của lần phân tích gần nhất (ứng với gợi ý đang hiện trên bảng). */
    private var lastSnap: ChatSnapshot? = null
    private val autoCheck = Runnable { checkForNewMessage() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        store = MemoryStore.get(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        DigestScheduler.sync(this)
        val themed = ContextThemeWrapper(this, R.style.Theme_CrushCoach_Overlay)
        bubble = BubbleController(this, themed, prefs, this)
        panel = PanelController(this, themed, this)
        if (prefs.bubbleEnabled) bubble?.show()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key != Prefs.KEY_BUBBLE) return
        if (prefs.bubbleEnabled) {
            bubble?.show()
        } else {
            setActive(false)
            panel?.destroy()
            bubble?.hide()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!active || event == null || (!prefs.autoMode && !prefs.memoryEnabled)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!prefs.watchAllApps && !ChatApps.isChat(pkg)) return
        // Gom các thay đổi liên tiếp, chỉ đọc màn hình khi giao diện đã đứng yên.
        main.removeCallbacks(autoCheck)
        main.postDelayed(autoCheck, AUTO_DELAY_MS)
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
        job?.cancel()
        active = false
        panel?.destroy()
        bubble?.hide()
        if (::prefs.isInitialized) prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
    }

    // ---- Bong bóng ----

    override fun onBubbleTap() = setActive(!active)

    override fun onBubbleLongPress() {
        val p = panel ?: return
        when {
            !active -> setActive(true)
            p.isShowing -> p.collapse()
            p.hasContent() -> {
                p.reveal()
                bubble?.setBadge(false)
            }
            else -> analyze()
        }
    }

    private fun setActive(on: Boolean) {
        if (on) {
            turnOn()
            analyze()
        } else {
            active = false
            job?.cancel()
            main.removeCallbacks(autoCheck)
            detector.reset()
            panel?.collapse()
            bubble?.setState(BubbleState.OFF)
        }
    }

    private fun turnOn() {
        if (active) return
        active = true
        bubble?.setState(BubbleState.ON)
        toast("Crush Coach đã BẬT 💘")
    }

    // ---- Bảng gợi ý ----

    override fun onRefresh() = analyze(regenerate = true)

    override fun onAsk(question: String) = analyze(question = question)

    override fun onCopy(text: String) {
        copy(text)
        logChoice(text)
        toast("Đã sao chép ✨")
    }

    override fun onInsert(text: String) {
        logChoice(text)
        if (ChatReader.insertText(this, text)) {
            toast("Đã chèn vào ô chat — xem lại rồi bấm gửi nhé")
            panel?.collapse()
        } else {
            copy(text)
            toast("App này không cho chèn tự động. Đã sao chép, dán vào ô chat nhé!")
        }
    }

    override fun onCollapse() {
        panel?.collapse()
    }

    override fun onToggleMemory() {
        val snap = lastSnap ?: return
        scope.launch {
            val crushName = prefs.crushName
            val nowTracked = withContext(Dispatchers.IO) {
                val tracked = !store.isTracked(snap.chatKey, snap.title, crushName)
                store.setTracked(snap.chatKey, snap.title, tracked)
                if (tracked) store.appendVisible(snap.chatKey, snap.appName, snap.title, snap.lines, System.currentTimeMillis())
                tracked
            }
            panel?.setMemoryState(nowTracked)
            toast(if (nowTracked) "📒 Sẽ ghi nhớ chat này để AI học dần" else "Đã tắt ghi nhớ chat này")
        }
    }

    // ---- Sổ tay về crush ----

    /**
     * Lưu tin mới của chat đang mở nếu đó là chat với crush (trùng tên crush hoặc đã bấm 📌).
     * Trả về true nếu chat này đang được ghi nhớ.
     */
    private suspend fun saveToMemory(snap: ChatSnapshot): Boolean {
        if (!prefs.memoryEnabled || !snap.hasChatInput) return false
        val crushName = prefs.crushName
        val daily = prefs.dailyDigest
        return withContext(Dispatchers.IO) {
            if (!store.isTracked(snap.chatKey, snap.title, crushName)) return@withContext false
            val now = System.currentTimeMillis()
            store.appendVisible(snap.chatKey, snap.appName, snap.title, snap.lines, now)
            // Lịch hằng ngày có thể bị máy hoãn; quá 1 ngày chưa cô đọng thì chạy bù.
            if (daily && store.shouldDigest(snap.chatKey, now)) DigestScheduler.runSoon(applicationContext)
            true
        }
    }

    private fun logChoice(text: String) {
        val snap = lastSnap ?: return
        if (!prefs.memoryEnabled) return
        val crushName = prefs.crushName
        scope.launch(Dispatchers.IO) {
            if (store.isTracked(snap.chatKey, snap.title, crushName)) {
                store.logChoice(snap.chatKey, text, System.currentTimeMillis())
            }
        }
    }

    // ---- Theo dõi tin nhắn mới ----

    private fun checkForNewMessage() {
        if (!active || job?.isActive == true) return
        val wait = AUTO_COOLDOWN_MS - (SystemClock.elapsedRealtime() - lastAutoAt)
        if (wait > 0 && prefs.autoMode) {
            // Vừa gọi AI xong: đợi hết thời gian nghỉ rồi mới xem lại màn hình.
            main.removeCallbacks(autoCheck)
            main.postDelayed(autoCheck, wait)
            return
        }
        val snap = ChatReader.read(this)
        if (!prefs.watchAllApps && !ChatApps.isChat(snap.packageName)) return
        // Chỉ làm việc trong màn hình trò chuyện (có ô nhập tin), không phải danh sách chat.
        if (!snap.hasChatInput) return
        scope.launch { saveToMemory(snap) }
        if (!prefs.autoMode) return
        if (detector.update(snap.chatKey, snap.lines)) analyze(auto = true)
    }

    private fun analyze(question: String? = null, regenerate: Boolean = false, auto: Boolean = false) {
        val p = panel ?: return
        turnOn()
        val key = prefs.apiKey
        if (key.isBlank()) {
            p.showError("Chưa có API key. Mở app Crush Coach và dán API key vào mục 1 nhé.")
            return
        }
        val previous = if (regenerate) lastResult?.suggestions?.map { it.text }.orEmpty() else emptyList()
        job?.cancel()
        job = scope.launch {
            bubble?.setState(BubbleState.LOADING)
            p.showLoading(
                when {
                    question != null -> "Đang hỏi ý kiến AI…"
                    auto -> "Crush vừa nhắn! Đang đọc tin…"
                    else -> "Đang đọc tin nhắn…"
                }
            )
            try {
                val snap = ChatReader.read(this@CoachAccessibilityService)
                lastSnap = snap
                detector.remember(snap.chatKey, snap.lines)
                val tracked = saveToMemory(snap)
                p.setMemoryState(if (prefs.memoryEnabled && snap.hasChatInput) tracked else null)
                // Chỉ đọc phần đã cô đọng, không gửi lại cả lịch sử chat.
                val memory = if (tracked) withContext(Dispatchers.IO) { store.memoryPrompt(snap.chatKey) } else null
                lastAutoAt = SystemClock.elapsedRealtime()
                // Ảnh tốn thêm token: chỉ gửi khi người dùng bật, hoặc khi chữ đọc được không đủ để hiểu.
                val needShot = prefs.useScreenshot || snap.lines.none { it.side != Side.UNKNOWN }
                val shot = if (needShot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    captureScreen()
                } else {
                    null
                }
                val request = CoachRequest(
                    appName = snap.appName,
                    chatTitle = snap.title,
                    lines = snap.lines,
                    draft = snap.draft,
                    screenshotJpegBase64 = shot,
                    question = question,
                    previousSuggestions = previous,
                    memory = memory,
                )
                val model = prefs.model
                val profile = prefs.profile()
                val result = withContext(Dispatchers.IO) { CrushAi.get(key, model).coach(profile, request) }
                lastResult = result
                p.showResult(result, snap.appName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CoachException) {
                p.showError(e.message ?: "Có lỗi xảy ra.")
            } catch (e: Exception) {
                p.showError("Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                bubble?.setState(if (active) BubbleState.ON else BubbleState.OFF)
            }
        }
    }

    /** Chụp màn hình (Android 11+) sau khi ẩn tạm bong bóng và bảng, trả về JPEG base64. */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreen(): String? {
        panel?.setHiddenForCapture(true)
        bubble?.setHiddenForCapture(true)
        val bitmap: Bitmap? = try {
            delay(200) // đợi một khung hình để lớp phủ thật sự biến mất
            suspendCancellableCoroutine { cont ->
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val hb = result.hardwareBuffer
                            val bmp = try {
                                Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.let { hw ->
                                    hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
                                }
                            } catch (e: Exception) {
                                null
                            } finally {
                                hb.close()
                            }
                            if (cont.isActive) cont.resume(bmp)
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        } finally {
            panel?.setHiddenForCapture(false)
            bubble?.setHiddenForCapture(false)
        }
        return bitmap?.let { withContext(Dispatchers.Default) { ImageUtil.toJpegBase64(it) } }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Crush Coach", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val AUTO_DELAY_MS = 1500L
        private const val AUTO_COOLDOWN_MS = 8000L

        @Volatile
        var instance: CoachAccessibilityService? = null
            private set
    }
}
