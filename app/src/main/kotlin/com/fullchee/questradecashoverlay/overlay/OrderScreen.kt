package com.fullchee.questradecashoverlay.overlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.parseAccountNumber
import java.math.BigDecimal
import java.util.Currency

const val QUESTRADE_PACKAGE = "com.questrade.questmobile"

private const val SELECTED_ACCOUNT_ID = "oe-account-selector-selected-account-name"

// The open picker keeps the old selection underneath, so its selected card is the newer answer.
private val selectedPickerCard = Regex("""oe-account-selector-account-card-(\d+)-selected""")

private const val TOTAL_AMOUNT = "Total amount"

private const val TOTAL_AMOUNT_INPUT_ID = "order-entry-setup-dollar-amount"

// Questrade's API holds cash only in CAD and USD.
private val orderCurrency = Regex("""$TOTAL_AMOUNT, \$, (CAD|USD)""")

/**
 * [currency] is null when the screen doesn't say which currency the order is in.
 * [total] is null until the order has an amount. [totalBox] is the "Total amount" box's screen bounds.
 */
data class OrderScreen(
    val account: AccountNumber,
    val currency: Currency?,
    val total: BigDecimal? = null,
    val totalBox: Rect? = null
)

/** Returns null off Questrade's order screen. View IDs are React Native testIDs; see docs/spike/0-questrade-view-tree.md. */
fun readOrderScreen(root: AccessibilityNodeInfo): OrderScreen? {
    if (root.packageName != QUESTRADE_PACKAGE) return null
    val nodes = root.descendants().toList()
    val account = selectedAccount(nodes) ?: return null
    val totalBox = nodes.firstOrNull { it.contentDescription?.startsWith(TOTAL_AMOUNT) == true }
    val currency = totalBox?.contentDescription
        ?.let { orderCurrency.matchEntire(it) }
        ?.let { Currency.getInstance(it.groupValues[1]) }
    val total = nodes.firstOrNull { it.viewIdResourceName == TOTAL_AMOUNT_INPUT_ID }
        ?.text
        ?.let(::parseDollars)
    return OrderScreen(
        account,
        currency,
        total,
        totalBox?.let {
            Rect().also(it::getBoundsInScreen)
        }
    )
}

// Typed amounts may carry thousands separators or a dollar sign; an empty box has no total yet.
private fun parseDollars(text: CharSequence): BigDecimal? =
    text.toString().replace(",", "").removePrefix("$").trim().toBigDecimalOrNull()

private fun selectedAccount(nodes: List<AccessibilityNodeInfo>): AccountNumber? {
    val byId = nodes.filter { it.viewIdResourceName != null }.associateBy { it.viewIdResourceName }
    val pickerCard = nodes.firstNotNullOfOrNull { node ->
        node.viewIdResourceName?.let { selectedPickerCard.matchEntire(it) }?.groupValues?.get(1)
    }
    val labelId =
        pickerCard?.let { "oe-account-selector-account-card-$it-acoount-name" }
            ?: SELECTED_ACCOUNT_ID
    val label = byId[labelId]?.text ?: return onlyAccountLabel(nodes)
    return parseAccountNumber(label.toString())
}

private fun onlyAccountLabel(nodes: List<AccessibilityNodeInfo>): AccountNumber? {
    // Other Questrade screens show account labels too, so only the order form's total marks this as the order screen.
    if (nodes.none { it.contentDescription?.startsWith(TOTAL_AMOUNT) == true }) return null
    // Questrade renamed its IDs: trust the account label only when it can't be another card's.
    return nodes.mapNotNull { node -> node.text?.let { parseAccountNumber(it.toString()) } }
        .distinct()
        .singleOrNull()
}

private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
    yield(this@descendants)
    for (i in 0 until childCount) {
        // A child can vanish between reading the count and fetching it.
        getChild(i)?.let { yieldAll(it.descendants()) }
    }
}
