package com.hanbubble.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.hanbubble.Prefs
import com.hanbubble.ai.KoreanResult
import com.hanbubble.databinding.OverlayBoxBinding
import com.hanbubble.ui.KoreanView

/** Khung dịch nổi ở phía trên màn hình, có nút ✕ để đóng và 🔊 để nghe. */
class TranslationBox(
    private val context: Context,
    private val themed: Context,
    private val prefs: Prefs,
    private val listener: Listener,
) {
    interface Listener {
        fun onSpeak(text: String)
        fun onClose()
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private var binding: OverlayBoxBinding? = null
    private var current: KoreanResult? = null

    val hasResult get() = current != null

    private fun ensure(): OverlayBoxBinding {
        binding?.let { return it }
        val b = OverlayBoxBinding.inflate(LayoutInflater.from(themed))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Không giành bàn phím: người dùng vẫn gõ tiếp trong app bên dưới.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = statusBarHeight() + dp(4)
        }
        b.btnClose.setOnClickListener { listener.onClose() }
        b.btnSpeak.setOnClickListener { current?.let { listener.onSpeak(it.korean) } }
        wm.addView(b.root, p)
        binding = b
        return b
    }

    fun showLoading(source: String, queued: Int) {
        val b = ensure()
        b.loadingRow.visibility = View.VISIBLE
        b.loadingText.text = "Đang dịch: “$source”"
        b.errorText.visibility = View.GONE
        b.queueLabel.text = if (queued > 0) "· còn $queued câu chờ" else ""
        // Giữ bản dịch trước trên khung trong lúc chờ, để không bị giật.
        b.scroll.visibility = if (current != null) View.VISIBLE else View.GONE
        b.btnSpeak.visibility = if (current != null) View.VISIBLE else View.GONE
        b.root.visibility = View.VISIBLE
    }

    fun showResult(result: KoreanResult, queued: Int) {
        val b = ensure()
        current = result
        b.loadingRow.visibility = View.GONE
        b.errorText.visibility = View.GONE
        b.queueLabel.text = if (queued > 0) "· còn $queued câu chờ" else ""
        KoreanView.bind(b.result, result, prefs.showRoman, prefs.expandDetails)
        b.scroll.visibility = View.VISIBLE
        b.scroll.scrollTo(0, 0)
        b.btnSpeak.visibility = View.VISIBLE
        b.root.visibility = View.VISIBLE
    }

    fun showError(message: String) {
        val b = ensure()
        b.loadingRow.visibility = View.GONE
        b.errorText.text = message
        b.errorText.visibility = View.VISIBLE
        b.root.visibility = View.VISIBLE
    }

    /** Hiện lại bản dịch gần nhất (khi giữ lâu bong bóng). */
    fun reveal() {
        val r = current ?: return
        showResult(r, 0)
    }

    fun hide() {
        binding?.root?.visibility = View.GONE
    }

    fun destroy() {
        binding?.let { runCatching { wm.removeView(it.root) } }
        binding = null
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}
