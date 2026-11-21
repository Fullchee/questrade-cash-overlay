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
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
                it.setBackgroundColor(OVERLAY_BACKGROUND)
                it.setPadding(
                    dp(PADDING_X_DP),
                    dp(PADDING_Y_DP),
                    dp(PADDING_X_DP),
                    dp(PADDING_Y_DP)
                )
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
            x = dp(HEADER_RIGHT_DP)
            y = dp(HEADER_TOP_DP)
        } else {
            // The box's top right holds only its currency label, which the overlay repeats (spike 0 bounds).
            x = resources.displayMetrics.widthPixels - totalBox.right + dp(TOTAL_BOX_INSET_RIGHT_DP)
            y = totalBox.top + dp(TOTAL_BOX_INSET_TOP_DP)
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TEXT_SIZE_SP = 16f
        const val PADDING_X_DP = 12
        const val PADDING_Y_DP = 6
        const val HEADER_RIGHT_DP = 16
        const val HEADER_TOP_DP = 64
        const val TOTAL_BOX_INSET_RIGHT_DP = 8
        const val TOTAL_BOX_INSET_TOP_DP = 4

        val OVERLAY_BACKGROUND = Color.argb(235, 33, 33, 33)

        // Bright enough to read on the dark background.
        val SHORT_COLOR = Color.rgb(255, 82, 82)
    }
}
