package com.fullchee.questradecashoverlay.overlay

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import java.io.IOException
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class CashOverlayTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private val renders = mutableListOf<OverlayContent?>()
    private val rendered get() = renders.map { it?.text }

    private var root: AccessibilityNodeInfo? = viewTreeFixture("order-screen")
    private var cash: Map<Currency, BigDecimal>? = mapOf(
        CAD to BigDecimal("1234.56"),
        USD to BigDecimal("310")
    )
    private val asked = mutableListOf<AccountNumber>()

    @Test
    fun `the order screen shows the selected account's cash in the order's currency`() = runTest {
        start()

        assertEquals(listOf(AccountNumber("22222222")), asked)
        assertEquals(listOf<String?>("CAD cash: $1,234.56"), rendered)
    }

    @Test
    fun `the overlay sits beside the Total amount box`() = runTest {
        start()

        assertEquals(Rect(42, 706, 1038, 880), renders.single()?.totalBox)
    }

    @Test
    fun `a total above the cash in its currency marks the cash short`() = runTest {
        root = orderScreen(totalAmount = "Total amount, $, CAD", total = "1,234.57")

        start()

        assertTrue(renders.single()!!.cashShort)
    }

    @Test
    fun `a total equal to the cash is not short`() = runTest {
        root = orderScreen(totalAmount = "Total amount, $, CAD", total = "1234.56")

        start()

        assertFalse(renders.single()!!.cashShort)
    }

    @Test
    fun `the total is compared with cash in the order's currency only`() = runTest {
        root = orderScreen(totalAmount = "Total amount, $, USD", total = "500")

        start()

        assertTrue(renders.single()!!.cashShort)
    }

    @Test
    fun `without a total positive cash is not short`() = runTest {
        start()

        assertFalse(renders.single()!!.cashShort)
    }

    @Test
    fun `without a total negative cash is short`() = runTest {
        cash = mapOf(CAD to BigDecimal("-5"))

        start()

        assertTrue(renders.single()!!.cashShort)
    }

    @Test
    fun `with an unknown currency the cash is not short`() = runTest {
        root = orderScreen(totalAmount = null, total = "1000000")

        start()

        assertFalse(renders.single()!!.cashShort)
    }

    @Test
    fun `a USD order shows USD cash`() = runTest {
        root = orderScreen(totalAmount = "Total amount, $, USD")

        start()

        assertEquals(listOf<String?>("USD cash: $310.00"), rendered)
    }

    @Test
    fun `an unreadable currency shows both currencies`() = runTest {
        root = orderScreen(totalAmount = null)

        start()

        assertEquals(listOf<String?>("CAD cash: $1,234.56\nUSD cash: $310.00"), rendered)
    }

    @Test
    fun `negative cash keeps its sign`() = runTest {
        cash = mapOf(CAD to BigDecimal("-5"))

        start()

        assertEquals(listOf<String?>("CAD cash: -$5.00"), rendered)
    }

    @Test
    fun `a currency Questrade sent no balance for is left out`() = runTest {
        root = orderScreen(totalAmount = null)
        cash = mapOf(USD to BigDecimal("310"))

        start()

        assertEquals(listOf<String?>("USD cash: $310.00"), rendered)
    }

    @Test
    fun `off the order screen the overlay is hidden`() = runTest {
        root = node(text = "Portfolio")

        start()

        assertEquals(listOf<String?>(null), rendered)
    }

    @Test
    fun `with no window the overlay is hidden`() = runTest {
        root = null

        start()

        assertEquals(listOf<String?>(null), rendered)
    }

    @Test
    fun `an account no login can read hides the overlay`() = runTest {
        cash = null

        start()

        assertEquals(listOf<String?>(null), rendered)
    }

    @Test
    fun `a Questrade failure hides the overlay`() = runTest {
        start { throw QuestradeException.HttpError(503) }

        assertEquals(listOf<String?>(null), rendered)
    }

    @Test
    fun `a storage failure hides the overlay and the next event tries again`() = runTest {
        var failing = true
        start {
            if (failing) throw IOException("disk")
            cash
        }
        failing = false

        wakeUps.send(Unit)
        runCurrent()

        assertEquals(listOf(null, "CAD cash: $1,234.56"), rendered)
    }

    @Test
    fun `while shown it rechecks every second, so leaving Questrade hides it`() = runTest {
        start()
        root = node(packageName = "com.android.launcher")

        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, rendered.size)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("CAD cash: $1,234.56", null), rendered)
    }

    @Test
    fun `an event rechecks at once`() = runTest {
        start()
        root = orderScreen(totalAmount = "Total amount, $, USD")

        wakeUps.trySend(Unit)
        runCurrent()

        assertEquals(listOf("CAD cash: $1,234.56", "USD cash: $310.00"), rendered)
    }

    @Test
    fun `while hidden it waits for the next event`() = runTest {
        root = null
        start()
        root = viewTreeFixture("order-screen")

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf<String?>(null), rendered)
        wakeUps.trySend(Unit)
        runCurrent()

        assertEquals(listOf(null, "CAD cash: $1,234.56"), rendered)
    }

    private fun TestScope.start(
        cashOf: suspend (AccountNumber) -> Map<Currency, BigDecimal>? = {
            asked += it
            cash
        }
    ) {
        val overlay = CashOverlay(resources, { root }, cashOf) { renders += it }
        backgroundScope.launch { overlay.run(wakeUps) }
        runCurrent()
    }

    private fun orderScreen(totalAmount: String?, total: String? = null) = node(
        children = listOfNotNull(
            node(viewId = "oe-account-selector-selected-account-name", text = "TFSA - 22222222"),
            totalAmount?.let { node(contentDescription = it) },
            total?.let { node(viewId = "order-entry-setup-dollar-amount", text = it) }
        )
    )

    private companion object {
        val CAD: Currency = Currency.getInstance("CAD")
        val USD: Currency = Currency.getInstance("USD")
    }
}
