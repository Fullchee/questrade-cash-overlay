package com.fullchee.questradecashoverlay.overlay

import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.logins.LoginStatus
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.logins.memoryDataStore
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class CashBalancesTest {
    private val server = MockWebServer()
    private val store = LoginStore(memoryDataStore())
    private var now = 0L
    private lateinit var balances: CashBalances
    private lateinit var mom: String

    private val tfsa = AccountNumber("22222222")
    private val cash = AccountNumber("11111111")

    @Before
    fun setUp() = runBlocking {
        server.start()
        mom = store.add("Mom", "mom-1")
        store.setAccounts(mom, listOf(tfsa, cash))
        balances = CashBalances(store, QuestradeClient(OkHttpClient(), server.url("/"))) { now }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetches the account's cash with its login's token and keeps the rotated token`() =
        runTest {
            server.enqueue(token("mom-2"))
            server.enqueue(cash(cad = "1234.56", usd = "310"))

            val result = balances.of(tfsa)

            assertEquals(mapOf(CAD to BigDecimal("1234.56"), USD to BigDecimal("310")), result)
            assertEquals("mom-1", server.takeRequest().url.queryParameter("refresh_token"))
            assertEquals("/v1/accounts/22222222/balances", server.takeRequest().url.encodedPath)
            server.enqueue(token("mom-3"))
            store.rotate(mom, QuestradeClient(OkHttpClient(), server.url("/"))::refresh)
            assertEquals("mom-2", server.takeRequest().url.queryParameter("refresh_token"))
        }

    @Test
    fun `cash stays cached for 30 seconds, then is fetched again with the same session`() =
        runTest {
            server.enqueue(token("mom-2"))
            server.enqueue(cash(cad = "1", usd = "2"))
            server.enqueue(cash(cad = "3", usd = "4"))

            balances.of(tfsa)
            now = 29_999
            val cached = balances.of(tfsa)
            now = 30_000
            val fresh = balances.of(tfsa)

            assertEquals(BigDecimal("1"), cached?.get(CAD))
            assertEquals(BigDecimal("3"), fresh?.get(CAD))
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `each account has its own cache entry`() = runTest {
        server.enqueue(token("mom-2"))
        server.enqueue(cash(cad = "1", usd = "2"))
        server.enqueue(cash(cad = "5", usd = "6"))

        balances.of(tfsa)
        val other = balances.of(cash)

        assertEquals(BigDecimal("5"), other?.get(CAD))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `an expired access token is exchanged once and the fetch retried`() = runTest {
        server.enqueue(token("mom-2"))
        server.enqueue(cash(cad = "1", usd = "2"))
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(token("mom-3"))
        server.enqueue(cash(cad = "7", usd = "8"))

        balances.of(tfsa)
        now = 30_000
        val result = balances.of(tfsa)

        assertEquals(BigDecimal("7"), result?.get(CAD))
        assertEquals(LoginStatus.Ok, store.logins.first().single().status)
    }

    @Test
    fun `a rejected refresh token marks the login and throws`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).build())

        assertThrows(QuestradeException.InvalidToken::class.java) {
            runBlocking { balances.of(tfsa) }
        }
        assertEquals(LoginStatus.NeedsReconnecting, store.logins.first().single().status)
    }

    @Test
    fun `an account no login can read has no cash and makes no request`() = runTest {
        assertNull(balances.of(AccountNumber("99999999")))
        assertEquals(0, server.requestCount)
    }

    private fun token(refreshToken: String) = json(
        """
        {"access_token": "access", "refresh_token": "$refreshToken", "api_server": "${server.url(
            "/"
        )}"}
        """
    )

    private fun cash(cad: String, usd: String) = json(
        """
        {"perCurrencyBalances": [{"currency": "CAD", "cash": $cad}, {"currency": "USD", "cash": $usd}]}
        """
    )

    private fun json(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(body.trimIndent())
        .build()

    private companion object {
        val CAD: Currency = Currency.getInstance("CAD")
        val USD: Currency = Currency.getInstance("USD")
    }
}
