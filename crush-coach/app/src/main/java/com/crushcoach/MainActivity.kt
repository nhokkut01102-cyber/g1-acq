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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.crushcoach.ai.AiModels
import com.crushcoach.ai.ChatLine
import com.crushcoach.ai.CoachException
import com.crushcoach.ai.CoachRequest
import com.crushcoach.ai.CrushAi
import com.crushcoach.ai.Side
import com.crushcoach.databinding.ActivityMainBinding
import com.crushcoach.service.CoachAccessibilityService
import com.crushcoach.ui.ResultView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var quickResult: ResultView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
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

        // Tư vấn nhanh
        b.btnQuickAsk.setOnClickListener { quickAsk() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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
