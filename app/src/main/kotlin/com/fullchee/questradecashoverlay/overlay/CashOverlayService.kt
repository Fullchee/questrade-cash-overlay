package com.fullchee.questradecashoverlay.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
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

    /** Shows [text] in the overlay, or removes the overlay on null. */
    internal fun render(text: String?) {
        val windows = getSystemService(WindowManager::class.java)
        val view = shown
        when {
            text == null -> view?.let {
                windows.removeView(it)
                shown = null
            }
            view != null -> view.text = text
            else -> shown = overlayView(text).also { windows.addView(it, overlayLayout()) }
        }
    }

    private fun overlayView(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setBackgroundColor(OVERLAY_BACKGROUND)
        val padding = dp(8)
        setPadding(padding, padding, padding, padding)
    }

    // Not touchable: taps pass through to Questrade's order form underneath.
    private fun overlayLayout() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Top right sits beside the symbol header, clear of the order fields (spike 0 bounds).
        gravity = Gravity.TOP or Gravity.END
        x = dp(16)
        y = dp(64)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val OVERLAY_BACKGROUND = Color.argb(220, 33, 33, 33)
    }
}
