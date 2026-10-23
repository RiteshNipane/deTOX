package com.detox.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Draggable floating counter. Uses TYPE_ACCESSIBILITY_OVERLAY when attached from the
 * accessibility service, so it needs no SYSTEM_ALERT_WINDOW grant.
 */
class CounterOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onMoved: (x: Int, y: Int) -> Unit,
) {
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = view != null

    /** [x]: offset from horizontal centre in px. [y]: px from top, or negative for the default spot. */
    fun show(x: Int, y: Int, count: Int) {
        if (view != null) { update(count); return }

        val tv = TextView(context).apply {
            text = format(count)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(36, 20, 36, 20)
            background = GradientDrawable().apply {
                cornerRadius = 64f
                setColor(0xCC1B1B1F.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Horizontally centred by default: x is an offset from the centre, so dragging still works.
            // y < 0 means "never moved": sit just below the status bar.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            this.x = x
            this.y = if (y < 0) (context.resources.displayMetrics.density * 48).toInt() else y
        }
        tv.setOnTouchListener(DragListener(lp))
        windowManager.addView(tv, lp)
        view = tv
        params = lp
    }

    fun update(count: Int) { view?.text = format(count) }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
    }

    private fun format(count: Int) = "${CounterMood.emojiFor(count)} $count"

    private inner class DragListener(private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> onMoved(lp.x, lp.y)
            }
            return true
        }
    }
}
