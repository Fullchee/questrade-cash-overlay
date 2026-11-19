package com.fullchee.questradecashoverlay.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.fullchee.questradecashoverlay.CashOverlayApp
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Shows the selected account's cash over Questrade's order screen. Events only come from Questrade (see cash_overlay_service.xml). */
class CashOverlayService : AccessibilityService() {
    private val scope = MainScope()
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private var shown: TextView? = null

    public override fun onServiceConnected() {
        val app = application as CashOverlayApp
        val balances = CashBalances(app.loginStore, QuestradeClient(OkHttpClient()))
        val overlay = CashOverlay(resources, { rootInActiveWindow }, balances::of, ::render)
        scope.launch { overlay.run(wakeUps) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        wakeUps.trySend(Unit)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        render(null)
        super.onDestroy()
    }

    /** Shows [content] in the overlay, or removes the overlay on null. */
    internal fun render(content: OverlayContent?) {
        val windows = getSystemService(WindowManager::class.java)
        val view = shown
        when {
            content == null -> view?.let {
                windows.removeView(it)
                shown = null
            }
            view != null -> {
                view.show(content)
                windows.updateViewLayout(view, overlayLayout(content.totalBox))
            }
            else -> shown = TextView(this).also {
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                it.setBackgroundColor(OVERLAY_BACKGROUND)
                it.setPadding(dp(12), dp(6), dp(12), dp(6))
                it.show(content)
                windows.addView(it, overlayLayout(content.totalBox))
            }
        }
    }

    private fun TextView.show(content: OverlayContent) {
        text = content.text
        setTextColor(if (content.cashShort) SHORT_COLOR else Color.WHITE)
        typeface = if (content.cashShort) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    // Not touchable: taps pass through to Questrade's order form underneath.
    private fun overlayLayout(totalBox: Rect?) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            // Screen coordinates, so the overlay lines up with the box's accessibility bounds.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        if (totalBox == null) {
            // Top right sits beside the symbol header (spike 0 bounds).
            x = dp(16)
            y = dp(64)
        } else {
            // The box's top right holds only its currency label, which the overlay repeats (spike 0 bounds).
            x = resources.displayMetrics.widthPixels - totalBox.right + dp(8)
            y = totalBox.top + dp(4)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val OVERLAY_BACKGROUND = Color.argb(235, 33, 33, 33)

        // Bright enough to read on the dark background.
        val SHORT_COLOR = Color.rgb(255, 82, 82)
    }
}
