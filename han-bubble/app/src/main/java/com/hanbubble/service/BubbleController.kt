package com.hanbubble.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.hanbubble.Prefs
import com.hanbubble.R
import com.hanbubble.databinding.OverlayBubbleBinding
import kotlin.math.abs
import kotlin.math.hypot

enum class BubbleState { OFF, ON, LOADING }

/**
 * Bong bóng tròn nổi trên mọi app: chạm để BẬT/TẮT, giữ lâu để hiện lại câu dịch gần nhất, kéo để di chuyển.
 * Dùng cửa sổ TYPE_ACCESSIBILITY_OVERLAY nên không cần quyền "hiển thị trên ứng dụng khác".
 */
class BubbleController(
    private val context: Context,
    private val themed: Context,
    private val prefs: Prefs,
    private val listener: Listener,
) {
    interface Listener {
        fun onBubbleTap()
        fun onBubbleLongPress()
    }

    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var binding: OverlayBubbleBinding? = null
    private var params: WindowManager.LayoutParams? = null
    private var pulse: ObjectAnimator? = null
    private var snapAnim: ValueAnimator? = null
    private var state = BubbleState.OFF

    val isShowing get() = binding != null

    fun show() {
        if (binding != null) return
        val b = OverlayBubbleBinding.inflate(LayoutInflater.from(themed))
        val size = screenSize()
        val p = WindowManager.LayoutParams(
            dp(BUBBLE_DP),
            dp(BUBBLE_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX.takeIf { it >= 0 } ?: (size.first - dp(BUBBLE_DP))
            y = prefs.bubbleY.takeIf { it >= 0 } ?: (size.second * 0.35f).toInt()
        }
        clamp(p)
        b.root.setOnTouchListener(DragTouchListener())
        wm.addView(b.root, p)
        binding = b
        params = p
        render()
    }

    fun hide() {
        pulse?.cancel()
        pulse = null
        snapAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
        binding?.let { runCatching { wm.removeView(it.root) } }
        binding = null
        params = null
    }

    fun setState(newState: BubbleState) {
        state = newState
        render()
    }

    private fun render() {
        val b = binding ?: return
        val on = state != BubbleState.OFF
        b.core.setBackgroundResource(if (on) R.drawable.bubble_on else R.drawable.bubble_off)
        b.icon.setTextColor(ContextCompat.getColor(context, if (on) R.color.white else R.color.primary))
        b.icon.visibility = if (state == BubbleState.LOADING) View.INVISIBLE else View.VISIBLE
        b.loading.visibility = if (state == BubbleState.LOADING) View.VISIBLE else View.GONE
        b.stateLabel.text = when (state) {
            BubbleState.OFF -> "OFF"
            BubbleState.ON -> "ON"
            BubbleState.LOADING -> "AI…"
        }
        b.stateLabel.setBackgroundResource(if (on) R.drawable.pill_on else R.drawable.pill_off)

        if (on && pulse == null) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                b.pulse,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1.25f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1.25f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.9f, 0f),
            ).apply {
                duration = 1400
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!on) {
            pulse?.cancel()
            pulse = null
            b.pulse.alpha = 0f
        }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            binding?.root?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            listener.onBubbleLongPress()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val p = params ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnim?.cancel()
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    dragging = false
                    longPressed = false
                    handler.postDelayed(longPress, longPressMs)
                    binding?.core?.animate()?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(90)?.start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging && !longPressed) {
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        clamp(p)
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    binding?.core?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                    if (dragging) {
                        snapToEdge(v, p)
                    } else if (!longPressed && e.actionMasked == MotionEvent.ACTION_UP &&
                        abs(e.rawX - downX) <= slop && abs(e.rawY - downY) <= slop
                    ) {
                        v.performClick()
                        listener.onBubbleTap()
                    }
                }
            }
            return true
        }
    }

    private fun snapToEdge(v: View, p: WindowManager.LayoutParams) {
        val (w, _) = screenSize()
        val bubbleW = v.width.takeIf { it > 0 } ?: dp(BUBBLE_DP)
        val target = if (p.x + bubbleW / 2 < w / 2) 0 else w - bubbleW
        snapAnim = ValueAnimator.ofInt(p.x, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                p.x = it.animatedValue as Int
                if (binding != null) runCatching { wm.updateViewLayout(v, p) }
            }
            start()
        }
        prefs.bubbleX = target
        prefs.bubbleY = p.y
    }

    private fun clamp(p: WindowManager.LayoutParams) {
        val (w, h) = screenSize()
        p.x = p.x.coerceIn(0, (w - dp(BUBBLE_DP)).coerceAtLeast(0))
        p.y = p.y.coerceIn(0, (h - dp(96)).coerceAtLeast(0))
    }

    private fun screenSize(): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val BUBBLE_DP = 76
    }
}
