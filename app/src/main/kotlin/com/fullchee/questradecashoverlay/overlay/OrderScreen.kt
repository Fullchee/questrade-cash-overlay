package com.fullchee.questradecashoverlay.overlay

import android.view.accessibility.AccessibilityNodeInfo
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.parseAccountNumber
import java.util.Currency

const val QUESTRADE_PACKAGE = "com.questrade.questmobile"

private const val SELECTED_ACCOUNT_ID = "oe-account-selector-selected-account-name"

// The open picker keeps the old selection underneath, so its selected card is the newer answer.
private val selectedPickerCard = Regex("""oe-account-selector-account-card-(\d+)-selected""")

// Questrade's API holds cash only in CAD and USD.
private val orderCurrency = Regex("""Total amount, \$, (CAD|USD)""")

/** [currency] is null when the screen doesn't say which currency the order is in. */
data class OrderScreen(val account: AccountNumber, val currency: Currency?)

/** Returns null off Questrade's order screen. View IDs are React Native testIDs; see docs/spike/0-questrade-view-tree.md. */
fun readOrderScreen(root: AccessibilityNodeInfo): OrderScreen? {
    if (root.packageName != QUESTRADE_PACKAGE) return null
    val nodes = root.descendants().toList()
    val account = selectedAccount(nodes) ?: return null
    val currency = nodes.firstNotNullOfOrNull { node ->
        node.contentDescription?.let { orderCurrency.matchEntire(it) }
    }?.let { Currency.getInstance(it.groupValues[1]) }
    return OrderScreen(account, currency)
}

private fun selectedAccount(nodes: List<AccessibilityNodeInfo>): AccountNumber? {
    val byId = nodes.filter { it.viewIdResourceName != null }.associateBy { it.viewIdResourceName }
    val pickerCard = nodes.firstNotNullOfOrNull { node ->
        node.viewIdResourceName?.let { selectedPickerCard.matchEntire(it) }?.groupValues?.get(1)
    }
    val labelId =
        pickerCard?.let { "oe-account-selector-account-card-$it-acoount-name" }
            ?: SELECTED_ACCOUNT_ID
    byId[labelId]?.text?.let { return parseAccountNumber(it.toString()) }
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
