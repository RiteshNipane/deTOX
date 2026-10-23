package com.detox.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Two accessibility-overlay windows:
 *  1. an opaque, NOT_TOUCHABLE cover, so swipes pass straight through to the app underneath;
 *  2. a small touchable "Peek anyway" button on top of it.
 * Dismissal on scroll is handled by SlopBlockController, not by touch handling here.
 *
 * The cover shows "The Slop has been Blocked!" and a different "did you know?" fact each time.
 */
class SlopBlockOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onPeek: () -> Unit,
) {
    private var cover: View? = null
    private var button: View? = null
    private var lastFact = -1

    val isShown: Boolean get() = cover != null

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    fun show() {
        if (cover != null) return

        lastFact = SlopFacts.pickIndex(avoid = lastFact)
        val c = buildCover(SlopFacts.all[lastFact])

        val coverParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )

        val b = Button(context).apply {
            text = "Peek anyway"
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(WHITE)
            stateListAnimator = null // no Material shadow/elevation: keeps the pill flat
            setPadding(dp(28), dp(12), dp(28), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(0x22FFFFFF)
                setStroke(dp(1), 0x66FFFFFF)
            }
            setOnClickListener { onPeek() }
        }
        val buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(110)
        }

        windowManager.addView(c, coverParams)
        windowManager.addView(b, buttonParams)
        cover = c
        button = b
    }

    private fun buildCover(fact: String): View {
        val shield = TextView(context).apply {
            text = "🛡️" // 🛡️
            textSize = 54f
            gravity = Gravity.CENTER
        }

        val full = "The Slop has been Blocked!"
        val title = TextView(context).apply {
            text = SpannableString(full).apply {
                val i = full.indexOf("Slop")
                setSpan(ForegroundColorSpan(ACCENT), i, i + 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize = 34f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            setLineSpacing(0f, 1.05f)
        }

        val subtitle = TextView(context).apply {
            text = "Swipe up to keep scrolling"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(MUTED)
        }

        val label = TextView(context).apply {
            text = "DID YOU KNOW?"
            textSize = 12f
            letterSpacing = 0.15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
        }
        val factText = TextView(context).apply {
            text = fact
            textSize = 16f
            setTextColor(SOFT_WHITE)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0x1AFFFFFF)
                setStroke(dp(1), 0x33FFFFFF)
            }
            addView(label)
            addView(factText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) })
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(shield)
            addView(title, lp(top = 8))
            addView(subtitle, lp(top = 10))
            addView(card, lp(top = 36))
        }

        return FrameLayout(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF0B0B14.toInt(), 0xFF1B0F2E.toInt(), 0xFF000000.toInt()),
            )
            setPadding(dp(28), 0, dp(28), dp(70))
            addView(
                column,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(top) }

    fun hide() {
        button?.let { runCatching { windowManager.removeView(it) } }
        cover?.let { runCatching { windowManager.removeView(it) } }
        button = null
        cover = null
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SOFT_WHITE = 0xFFE6E6EA.toInt()
        const val MUTED = 0xFF9A9AA8.toInt()
        const val ACCENT = 0xFFFF4D5E.toInt()
    }
}
