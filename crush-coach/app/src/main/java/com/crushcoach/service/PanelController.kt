package com.crushcoach.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.crushcoach.R
import com.crushcoach.ai.CoachResult
import com.crushcoach.databinding.OverlayPanelBinding
import com.crushcoach.ui.ResultView

/** Bảng gợi ý nổi ở phía trên màn hình, đè lên app chat. */
class PanelController(
    private val context: Context,
    private val themed: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onRefresh()
        fun onAsk(question: String)
        fun onCopy(text: String)
        fun onInsert(text: String)
        fun onCollapse()
        fun onToggleMemory()
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private var binding: OverlayPanelBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private var resultView: ResultView? = null
    private var focusable = false

    val isShowing get() = binding?.root?.visibility == View.VISIBLE

    private fun ensure(): OverlayPanelBinding {
        binding?.let { return it }
        val b = OverlayPanelBinding.inflate(LayoutInflater.from(themed))
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            baseFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = statusBarHeight() + dp(4)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        b.btnRefresh.setOnClickListener { listener.onRefresh() }
        b.btnCollapse.setOnClickListener { listener.onCollapse() }
        b.memoryToggle.setOnClickListener { listener.onToggleMemory() }
        b.btnAsk.setOnClickListener { submitQuestion() }
        b.askInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitQuestion()
                true
            } else {
                false
            }
        }
        // Bảng mặc định không giành bàn phím của app chat; chỉ khi chạm vào ô hỏi mới bật nhập chữ.
        b.askInput.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                setFocusable(true)
                v.requestFocus()
                showKeyboard(v)
                v.performClick()
            }
            false
        }
        b.root.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) setFocusable(false)
            false
        }

        QUICK_QUESTIONS.forEach { q ->
            val chip = LayoutInflater.from(themed).inflate(R.layout.item_question_chip, b.quickQuestions, false) as TextView
            chip.text = q
            chip.setOnClickListener { listener.onAsk(q) }
            b.quickQuestions.addView(chip)
        }

        wm.addView(b.root, p)
        binding = b
        params = p
        resultView = ResultView(b.result)
        return b
    }

    fun showLoading(message: String, appLabel: String? = null) {
        val b = ensure()
        appLabel?.let { b.appLabel.text = "· $it" }
        resultView?.showLoading(message)
        reveal()
    }

    fun showError(message: String) {
        ensure()
        resultView?.showError(message)
        reveal()
    }

    fun showResult(result: CoachResult, appLabel: String) {
        val b = ensure()
        b.appLabel.text = "· $appLabel"
        resultView?.show(result, listener::onCopy, listener::onInsert)
        b.scroll.scrollTo(0, 0)
        reveal()
    }

    /**
     * Nút ghi nhớ của chat đang mở: true = đang ghi nhớ, false = chưa, null = ẩn (không phải màn hình chat).
     */
    fun setMemoryState(tracked: Boolean?) {
        val b = binding ?: return
        when (tracked) {
            null -> b.memoryToggle.visibility = View.GONE
            true -> {
                b.memoryToggle.visibility = View.VISIBLE
                b.memoryToggle.text = context.getString(R.string.memory_on)
                b.memoryToggle.setBackgroundResource(R.drawable.chip_bg)
            }
            false -> {
                b.memoryToggle.visibility = View.VISIBLE
                b.memoryToggle.text = context.getString(R.string.memory_off)
                b.memoryToggle.setBackgroundResource(R.drawable.chip_question)
            }
        }
    }

    /** Hiện lại bảng với nội dung cũ. */
    fun reveal() {
        binding?.root?.visibility = View.VISIBLE
    }

    fun collapse() {
        val b = binding ?: return
        setFocusable(false)
        b.root.visibility = View.GONE
    }

    fun hasContent() = binding != null

    fun setHiddenForCapture(hidden: Boolean) {
        val b = binding ?: return
        if (b.root.visibility == View.GONE) return
        b.root.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun destroy() {
        binding?.let { runCatching { wm.removeView(it.root) } }
        binding = null
        params = null
        resultView = null
        focusable = false
    }

    private fun submitQuestion() {
        val b = binding ?: return
        val q = b.askInput.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return
        b.askInput.setText("")
        hideKeyboard(b.askInput)
        setFocusable(false)
        listener.onAsk(q)
    }

    private fun setFocusable(value: Boolean) {
        val b = binding ?: return
        val p = params ?: return
        if (focusable == value) return
        focusable = value
        p.flags = if (value) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            baseFlags()
        }
        if (!value) b.askInput.clearFocus()
        runCatching { wm.updateViewLayout(b.root, p) }
    }

    private fun baseFlags() =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

    private fun showKeyboard(v: View) {
        v.post {
            context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(v: View) {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    companion object {
        val QUICK_QUESTIONS = listOf(
            "Crush có thích mình không?",
            "Nên rủ đi chơi chưa?",
            "Làm sao để chuyện đỡ nhạt?",
            "Mình có đang nhắn dồn không?",
            "Crush đang giận à?",
            "Nên trả lời ngay hay đợi chút?",
        )
    }
}
