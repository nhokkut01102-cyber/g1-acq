package com.hanbubble

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.hanbubble.ai.AiModels
import com.hanbubble.ai.HanAi
import com.hanbubble.ai.HanException
import com.hanbubble.ai.StyleSetting
import com.hanbubble.ai.TranslateRequest
import com.hanbubble.data.HistoryStore
import com.hanbubble.databinding.ActivityMainBinding
import com.hanbubble.service.HanAccessibilityService
import com.hanbubble.ui.KoreanView
import com.hanbubble.ui.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker

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
        speaker = Speaker(this)

        // Bong bóng
        b.switchBubble.isChecked = prefs.bubbleEnabled
        b.switchBubble.setOnCheckedChangeListener { _, checked ->
            prefs.bubbleEnabled = checked
            if (checked) {
                if (!isAccessibilityEnabled()) askToEnableAccessibility()
                else Toast.makeText(this, "Bong bóng đã hiện! Chạm vào để BẬT dịch 🇰🇷", Toast.LENGTH_SHORT).show()
            }
        }

        // Sổ học
        b.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        // 1. AI
        b.apiKey.setText(prefs.apiKey)
        b.apiKey.doAfterTextChanged { prefs.apiKey = it?.toString().orEmpty() }
        setupDropdown(b.model, AiModels.options.map { it.label }, AiModels.options.indexOfFirst { it.id == prefs.model }) {
            prefs.model = AiModels.options[it].id
        }
        b.btnGetKey.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))) }
        }

        // 2. Quyền
        b.btnAccessibility.setOnClickListener { openAccessibilitySettings() }
        b.btnAppInfo.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }

        // 3. Tuỳ chọn
        val styles = StyleSetting.entries
        setupDropdown(b.style, styles.map { it.label }, styles.indexOf(prefs.style)) { prefs.style = styles[it] }
        b.showRoman.isChecked = prefs.showRoman
        b.showRoman.setOnCheckedChangeListener { _, c -> prefs.showRoman = c }
        b.expandDetails.isChecked = prefs.expandDetails
        b.expandDetails.setOnCheckedChangeListener { _, c -> prefs.expandDetails = c }

        KoreanView.fillLegend(b.legend)

        // Dịch thử
        b.quickResult.root.visibility = View.GONE
        b.btnTranslate.setOnClickListener { quickTranslate() }
    }

    override fun onResume() {
        super.onResume()
        val on = isAccessibilityEnabled()
        b.accessStatus.text = getString(if (on) R.string.status_on else R.string.status_off)
        b.accessStatus.setTextColor(ContextCompat.getColor(this, if (on) R.color.ok_green else R.color.warn_ink))
        b.btnAccessibility.visibility = if (on) View.GONE else View.VISIBLE
        refreshCount()
    }

    private fun refreshCount() {
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) { HistoryStore.get(this@MainActivity).count() }
            b.historyCount.text = if (n == 0) "Chưa có câu nào." else "Đã lưu $n câu để ôn lại."
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun quickTranslate() {
        val text = b.quickInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        b.btnTranslate.isEnabled = false
        b.btnTranslate.text = "Đang dịch…"
        b.quickError.visibility = View.GONE
        val key = prefs.apiKey
        val model = prefs.model
        val request = TranslateRequest(text, appName = "Hàn Bubble", style = prefs.style)
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    HanAi.get(key, model).translate(request).also {
                        HistoryStore.get(this@MainActivity).insert("Dịch thử", it, System.currentTimeMillis())
                    }
                }
                KoreanView.bind(b.quickResult, result, prefs.showRoman, expanded = true)
                b.quickResult.root.visibility = View.VISIBLE
                b.quickResult.koreanText.setOnClickListener { speaker.speak(result.korean) }
                refreshCount()
            } catch (e: HanException) {
                showQuickError(e.message ?: "Có lỗi xảy ra.")
            } catch (e: Exception) {
                showQuickError("Có lỗi xảy ra: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                b.btnTranslate.isEnabled = true
                b.btnTranslate.setText(R.string.translate)
            }
        }
    }

    private fun showQuickError(msg: String) {
        b.quickError.text = msg
        b.quickError.visibility = View.VISIBLE
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (HanAccessibilityService.instance != null) return true
        val me = ComponentName(this, HanAccessibilityService::class.java)
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
        Toast.makeText(this, "Tìm \"Hàn Bubble – dịch câu sang tiếng Hàn\" và bật lên nhé", Toast.LENGTH_LONG).show()
    }

    private fun setupDropdown(view: MaterialAutoCompleteTextView, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        view.setSimpleItems(items.toTypedArray())
        if (selected in items.indices) view.setText(items[selected], false)
        view.setOnItemClickListener { _, _, position, _ -> onPick(position) }
    }
}
