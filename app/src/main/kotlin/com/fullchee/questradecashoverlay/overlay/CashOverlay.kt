package com.fullchee.questradecashoverlay.overlay

import android.content.res.Resources
import android.view.accessibility.AccessibilityNodeInfo
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.R
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeoutOrNull

// The service only hears Questrade's events, so leaving Questrade is noticed by rechecking the active window.
private val RECHECK_WHILE_SHOWN = 1.seconds

private val CASH_LABELS = mapOf(
    Currency.getInstance("CAD") to R.string.overlay_cad_cash,
    Currency.getInstance("USD") to R.string.overlay_usd_cash
)

/** Keeps the overlay showing the selected account's cash while Questrade's order screen is open. */
class CashOverlay(
    private val resources: Resources,
    private val readActiveWindow: () -> AccessibilityNodeInfo?,
    private val cashOf: suspend (AccountNumber) -> Map<Currency, BigDecimal>?,
    /** Shows the text, or hides the overlay on null. */
    private val render: (String?) -> Unit
) {
    // Questrade shows dollars as $1,234.56 for both currencies, whatever the phone's language.
    private val dollars = DecimalFormat("\$#,##0.00;-\$#,##0.00", DecimalFormatSymbols(Locale.US))

    /** Runs until cancelled, rechecking the screen on each of [wakeUps]. */
    suspend fun run(wakeUps: ReceiveChannel<Unit>) {
        while (true) {
            val text = cashText()
            render(text)
            if (text == null) {
                wakeUps.receive()
            } else {
                withTimeoutOrNull(RECHECK_WHILE_SHOWN) { wakeUps.receive() }
            }
        }
    }

    private suspend fun cashText(): String? {
        val screen = readActiveWindow()?.let(::readOrderScreen) ?: return null
        val cash = try {
            cashOf(screen.account)
        } catch (e: QuestradeException) {
            null
        } ?: return null
        val shown = screen.currency?.let(::listOf) ?: CASH_LABELS.keys
        // Questrade sends both currencies today; a missing one has nothing to show.
        return shown.mapNotNull { currency ->
            cash[currency]?.let {
                resources.getString(CASH_LABELS.getValue(currency), dollars.format(it))
            }
        }.joinToString("\n").ifEmpty { null }
    }
}
