package com.fullchee.questradecashoverlay.overlay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.AccountNumber
import java.util.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderScreenTest {
    private val cad = Currency.getInstance("CAD")
    private val usd = Currency.getInstance("USD")

    @Test
    fun `the order screen fixture gives the selected account and the order currency`() {
        assertEquals(
            OrderScreen(AccountNumber("22222222"), cad),
            readOrderScreen(viewTreeFixture("order-screen"))
        )
    }

    @Test
    fun `the open account picker gives the card marked selected`() {
        assertEquals(
            OrderScreen(AccountNumber("22222222"), cad),
            readOrderScreen(viewTreeFixture("account-picker"))
        )
    }

    @Test
    fun `a picker card selected after opening wins over the account shown underneath`() {
        val screen = node(
            children = listOf(
                node(
                    viewId = "oe-account-selector-selected-account-name",
                    text = "TFSA - 22222222"
                ),
                node(viewId = "oe-account-selector-account-card-0-selected"),
                node(
                    viewId = "oe-account-selector-account-card-0-acoount-name",
                    text = "Cash - 11111111 "
                )
            )
        )

        assertEquals(AccountNumber("11111111"), readOrderScreen(screen)?.account)
    }

    @Test
    fun `a USD order reads USD`() {
        val screen = node(
            children = listOf(
                node(
                    viewId = "oe-account-selector-selected-account-name",
                    text = "Cash - 11111111"
                ),
                node(contentDescription = "Total amount, $, USD")
            )
        )

        assertEquals(usd, readOrderScreen(screen)?.currency)
    }

    @Test
    fun `an unreadable currency leaves the currency unknown`() {
        val screen = node(
            children = listOf(
                node(
                    viewId = "oe-account-selector-selected-account-name",
                    text = "Cash - 11111111"
                ),
                node(contentDescription = "Total amount, $, EUR")
            )
        )

        assertEquals(OrderScreen(AccountNumber("11111111"), null), readOrderScreen(screen))
    }

    @Test
    fun `without the selector IDs the only account label on screen is used`() {
        val screen = node(
            children = listOf(
                node(text = "RRSP - 33333333"),
                node(contentDescription = "Total amount, $, CAD")
            )
        )

        assertEquals(AccountNumber("33333333"), readOrderScreen(screen)?.account)
    }

    @Test
    fun `without the selector IDs two account labels are ambiguous`() {
        val screen = node(
            children = listOf(
                node(text = "Cash - 11111111"),
                node(text = "RRSP - 33333333"),
                node(contentDescription = "Total amount, $, CAD")
            )
        )

        assertNull(readOrderScreen(screen))
    }

    @Test
    fun `an account label off the order form is not the order screen`() {
        assertNull(readOrderScreen(node(children = listOf(node(text = "TFSA - 22222222")))))
    }

    @Test
    fun `a Questrade screen without an account is not the order screen`() {
        assertNull(readOrderScreen(node(children = listOf(node(text = "Portfolio")))))
    }

    @Test
    fun `another app's window is not the order screen`() {
        val screen = node(packageName = "com.android.launcher", text = "TFSA - 22222222")

        assertNull(readOrderScreen(screen))
    }
}
