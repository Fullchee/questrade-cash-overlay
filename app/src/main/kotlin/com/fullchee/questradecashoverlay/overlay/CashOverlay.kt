package com.fullchee.questradecashoverlay.overlay

import android.content.res.Resources
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.R
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

// The service only hears Questrade's events, so leaving Questrade is noticed by rechecking the active window.
private val RECHECK_WHILE_SHOWN = 1.seconds

private const val TAG = "CashOverlay"

private val CASH_LABELS = mapOf(
    Currency.getInstance("CAD") to R.string.overlay_cad_cash,
    Currency.getInstance("USD") to R.string.overlay_usd_cash
)

/** [cashShort] is true when the order's total exceeds the cash held in its currency. [totalBox] is null when the screen has no "Total amount" box to sit beside. */
data class OverlayContent(val text: String, val cashShort: Boolean, val totalBox: Rect?)

/** Keeps the overlay showing the selected account's cash while Questrade's order screen is open. */
class CashOverlay(
    private val resources: Resources,
    private val readActiveWindow: () -> AccessibilityNodeInfo?,
    private val cashOf: suspend (AccountNumber) -> Map<Currency, BigDecimal>?,
    /** Shows the content, or hides the overlay on null. */
    private val render: (OverlayContent?) -> Unit
) {
    // Questrade shows dollars as $1,234.56 for both currencies, whatever the phone's language.
    private val dollars = DecimalFormat("\$#,##0.00;-\$#,##0.00", DecimalFormatSymbols(Locale.US))

    /** Runs until cancelled, rechecking the screen on each of [wakeUps]. */
    suspend fun run(wakeUps: ReceiveChannel<Unit>) {
        while (true) {
            val content = content()
            render(content)
            if (content == null) {
                wakeUps.receive()
            } else {
                withTimeoutOrNull(RECHECK_WHILE_SHOWN) { wakeUps.receive() }
            }
        }
    }

    private suspend fun content(): OverlayContent? {
        val screen = readActiveWindow()?.let(::readOrderScreen) ?: return null
        val cash = try {
            cashOf(screen.account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A crash switches the accessibility service off until someone re-enables it; hide and retry on the next event.
            Log.w(TAG, "Couldn't read the account's cash", e)
            null
        } ?: return null
        val shown = screen.currency?.let(::listOf) ?: CASH_LABELS.keys
        // Questrade sends both currencies today; a missing one has nothing to show.
        val text = shown.mapNotNull { currency ->
            cash[currency]?.let {
                resources.getString(CASH_LABELS.getValue(currency), dollars.format(it))
            }
        }.joinToString("\n").ifEmpty { null } ?: return null
        val orderCash = screen.currency?.let(cash::get)
        val cashShort = orderCash != null && screen.total != null && orderCash < screen.total
        return OverlayContent(text, cashShort, screen.totalBox)
    }
}
