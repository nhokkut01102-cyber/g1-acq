package com.crushcoach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.crushcoach.ai.AiModels
import com.crushcoach.ai.ChatLine
import com.crushcoach.ai.CoachException
import com.crushcoach.ai.CoachRequest
import com.crushcoach.ai.CrushAi
import com.crushcoach.ai.Side
import com.crushcoach.databinding.ActivityMainBinding
import com.crushcoach.memory.DigestScheduler
import com.crushcoach.memory.Digester
import com.crushcoach.memory.MemoryStore
import com.crushcoach.service.CoachAccessibilityService
import com.crushcoach.ui.ResultView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var quickResult: ResultView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // Android 15 vẽ tràn viền: chừa chỗ cho thanh trạng thái, thanh điều hướng và bàn phím.
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        prefs = Prefs(this)
        quickResult = ResultView(b.quickResult)

        // 1. AI
        b.apiKey.setText(prefs.apiKey)
        b.apiKey.doAfterTextChanged { prefs.apiKey = it?.toString().orEmpty() }
        setupDropdown(
            b.model,
            AiModels.options.map { it.label },
            AiModels.options.indexOfFirst { it.id == prefs.model },
        ) { i -> prefs.model = AiModels.options[i].id }
        b.btnGetKey.setOnClickListener { openUrl("https://console.anthropic.com/settings/keys") }

        // 2. Quyền
        b.btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        b.btnAppInfo.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        }

        // 3. Hồ sơ crush
        bindText(b.myName, prefs.myName) { prefs.myName = it }
        bindText(b.crushName, prefs.crushName) { prefs.crushName = it }
        bindText(b.pronouns, prefs.pronouns) { prefs.pronouns = it }
        bindText(b.notes, prefs.notes) { prefs.notes = it }
        setupDropdown(b.stage, Prefs.STAGES, Prefs.STAGES.indexOf(prefs.stage)) { i ->
            prefs.stage = Prefs.STAGES[i]
        }
        setupDropdown(b.style, Prefs.STYLES, Prefs.STYLES.indexOf(prefs.style).coerceAtLeast(0)) { i ->
            prefs.style = Prefs.STYLES[i]
        }
        if (prefs.style.isBlank()) prefs.style = Prefs.STYLES[0]

        // 4. Bong bóng
        b.switchBubble.isChecked = prefs.bubbleEnabled
        b.switchBubble.setOnCheckedChangeListener { _, checked ->
            prefs.bubbleEnabled = checked
            if (checked) {
                if (!isAccessibilityEnabled()) {
                    askToEnableAccessibility()
                } else {
                    Toast.makeText(this, "Bong bóng đã hiện! Chạm vào để BẬT AI 💘", Toast.LENGTH_SHORT).show()
                }
            }
        }
        b.autoMode.isChecked = prefs.autoMode
        b.autoMode.setOnCheckedChangeListener { _, c -> prefs.autoMode = c }
        b.useScreenshot.isChecked = prefs.useScreenshot
        b.useScreenshot.setOnCheckedChangeListener { _, c -> prefs.useScreenshot = c }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            b.useScreenshot.isEnabled = false
            b.screenshotNote.visibility = View.VISIBLE
        }
        b.watchAll.isChecked = prefs.watchAllApps
        b.watchAll.setOnCheckedChangeListener { _, c -> prefs.watchAllApps = c }

        // 5. Sổ tay về crush
        b.memoryEnabled.isChecked = prefs.memoryEnabled
        b.memoryEnabled.setOnCheckedChangeListener { _, c ->
            prefs.memoryEnabled = c
            b.dailyDigest.isEnabled = c
            DigestScheduler.sync(this)
        }
        b.dailyDigest.isChecked = prefs.dailyDigest
        b.dailyDigest.isEnabled = prefs.memoryEnabled
        b.dailyDigest.setOnCheckedChangeListener { _, c ->
            prefs.dailyDigest = c
            DigestScheduler.sync(this)
        }
        b.btnDigestNow.setOnClickListener { digestNow() }
        b.btnClearMemory.setOnClickListener { confirmClearMemory() }
        DigestScheduler.sync(this)

        // Tư vấn nhanh
        b.btnQuickAsk.setOnClickListener { quickAsk() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshMemory()
    }

    private fun refreshMemory() {
        lifecycleScope.launch {
            val store = MemoryStore.get(this@MainActivity)
            val (stats, memories) = withContext(Dispatchers.IO) {
                store.stats() to store.allMemories().map { it to store.journal(it.chatKey, 3) }
            }
            val last = stats.lastDigestAt?.let { "lần cô đọng gần nhất: ${TIME_FMT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}" }
                ?: "chưa cô đọng lần nào"
            b.memoryStats.text = "Đã lưu ${stats.messages} tin · ${stats.undigested} tin chưa cô đọng · $last"
            b.memoryList.removeAllViews()
            if (memories.isEmpty()) {
                b.memoryList.addView(memoryText(getString(R.string.memory_empty), muted = true))
            }
            memories.forEach { (m, notes) ->
                b.memoryList.addView(memoryText("💬 ${m.title ?: "Cuộc trò chuyện"}", bold = true))
                val body = buildString {
                    append(m.memory.toDisplayText())
                    if (notes.isNotEmpty()) {
                        append("\n\n📔 Nhật ký gần đây:")
                        notes.forEach { append("\n• ${it.day}: ${it.note}") }
                    }
                }
                b.memoryList.addView(memoryText(body))
            }
        }
    }

    private fun memoryText(text: String, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = if (bold) 15f else 14f
        setLineSpacing(0f, 1.15f)
        setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, 0)
        setTextColor(ContextCompat.getColor(context, if (muted) R.color.ink_muted else R.color.ink))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextIsSelectable(true)
    }

    private fun digestNow() {
        if (prefs.apiKey.isBlank()) {
            Toast.makeText(this, "Chưa có API key ở mục 1", Toast.LENGTH_SHORT).show()
            return
        }
        b.btnDigestNow.isEnabled = false
        b.btnDigestNow.text = "Đang cô đọng…"
        lifecycleScope.launch {
            val msg = try {
                val n = Digester(this@MainActivity).runAll()
                if (n == 0) "Không có tin mới để cô đọng." else "Đã cô đọng $n tin vào sổ tay 📒"
            } catch (e: CoachException) {
                e.message ?: "Có lỗi xảy ra."
            } catch (e: Exception) {
                "Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}"
            }
            b.btnDigestNow.isEnabled = true
            b.btnDigestNow.setText(R.string.digest_now)
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            refreshMemory()
        }
    }

    private fun confirmClearMemory() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_memory_title)
            .setMessage(R.string.clear_memory_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { MemoryStore.get(this@MainActivity).clearAll() }
                    Toast.makeText(this@MainActivity, "Đã xoá dữ liệu đã lưu", Toast.LENGTH_SHORT).show()
                    refreshMemory()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshStatus() {
        val on = isAccessibilityEnabled()
        b.accessStatus.text = getString(if (on) R.string.status_on else R.string.status_off)
        b.accessStatus.setTextColor(ContextCompat.getColor(this, if (on) R.color.ok_green else R.color.warn_ink))
        b.btnAccessibility.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (CoachAccessibilityService.instance != null) return true
        val me = ComponentName(this, CoachAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun askToEnableAccessibility() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.need_accessibility_title)
            .setMessage(getString(R.string.need_accessibility_msg) + "\n\n" + getString(R.string.restricted_hint))
            .setPositiveButton(R.string.go_settings) { _, _ -> openAccessibilitySettings() }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        Toast.makeText(this, "Tìm \"Crush Coach – bong bóng gợi ý\" và bật lên nhé", Toast.LENGTH_LONG).show()
    }

    private fun quickAsk() {
        val chat = b.quickChat.text?.toString()?.trim().orEmpty()
        val question = b.quickQuestion.text?.toString()?.trim().orEmpty()
        if (chat.isEmpty() && question.isEmpty()) {
            Toast.makeText(this, "Dán đoạn chat hoặc nhập câu hỏi trước nhé", Toast.LENGTH_SHORT).show()
            return
        }
        val key = prefs.apiKey
        if (key.isBlank()) {
            quickResult.showError("Chưa có API key. Dán API key vào mục 1 ở trên nhé.")
            return
        }
        b.btnQuickAsk.isEnabled = false
        quickResult.showLoading("AI đang suy nghĩ…")
        val request = CoachRequest(
            appName = "Đoạn chat do mình dán vào",
            lines = chat.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { ChatLine(Side.UNKNOWN, it) },
            question = question.ifEmpty { null },
        )
        val profile = prefs.profile()
        val model = prefs.model
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { CrushAi.get(key, model).coach(profile, request) }
                quickResult.show(result, onCopy = ::copy, onInsert = null)
            } catch (e: CoachException) {
                quickResult.showError(e.message ?: "Có lỗi xảy ra.")
            } catch (e: Exception) {
                quickResult.showError("Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                b.btnQuickAsk.isEnabled = true
            }
        }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Crush Coach", text))
        Toast.makeText(this, "Đã sao chép ✨", Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun bindText(field: EditText, value: String, save: (String) -> Unit) {
        field.setText(value)
        field.doAfterTextChanged { save(it?.toString().orEmpty()) }
    }

    private companion object {
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM")
    }

    private fun setupDropdown(
        view: MaterialAutoCompleteTextView,
        items: List<String>,
        selected: Int,
        onPick: (Int) -> Unit,
    ) {
        view.setSimpleItems(items.toTypedArray())
        if (selected in items.indices) view.setText(items[selected], false)
        view.setOnItemClickListener { _, _, position, _ -> onPick(position) }
    }
}
