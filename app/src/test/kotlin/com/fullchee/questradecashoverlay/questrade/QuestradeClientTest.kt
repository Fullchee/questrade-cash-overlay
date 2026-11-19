package com.fullchee.questradecashoverlay.questrade

import com.fullchee.questradecashoverlay.AccountNumber
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class QuestradeClientTest {
    private val server = MockWebServer()
    private lateinit var client: QuestradeClient

    @Before
    fun setUp() {
        server.start()
        client = QuestradeClient(OkHttpClient(), loginServer = server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `refresh exchanges the refresh token for a session holding the new refresh token`() =
        runTest {
            server.enqueue(
                json(
                    """
                {
                  "access_token": "access-1",
                  "token_type": "Bearer",
                  "expires_in": 1800,
                  "refresh_token": "refresh-2",
                  "api_server": "https://api01.iq.questrade.com/"
                }
                """
                )
            )

            val session = client.refresh("refresh-1")

            assertEquals("access-1", session.accessToken)
            assertEquals("https://api01.iq.questrade.com/", session.apiServer.toString())
            assertEquals("refresh-2", session.refreshToken)
            val request = server.takeRequest()
            assertEquals("/oauth2/token", request.url.encodedPath)
            assertEquals("refresh_token", request.url.queryParameter("grant_type"))
            assertEquals("refresh-1", request.url.queryParameter("refresh_token"))
        }

    @Test
    fun `accounts lists each account's number and type with the access token`() = runTest {
        server.enqueue(
            json(
                """
                {
                  "accounts": [
                    {"type": "Margin", "number": "26598145", "status": "Active", "isPrimary": true},
                    {"type": "TFSA", "number": "26598146", "status": "Active", "isPrimary": false}
                  ],
                  "userId": 3000124
                }
                """
            )
        )

        val accounts = client.accounts(session())

        assertEquals(
            listOf(
                Account(AccountNumber("26598145"), "Margin"),
                Account(AccountNumber("26598146"), "TFSA")
            ),
            accounts
        )
        val request = server.takeRequest()
        assertEquals("/v1/accounts", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers["Authorization"])
    }

    @Test
    fun `cashBalances reads each currency's cash without losing precision`() = runTest {
        server.enqueue(
            json(
                """
                {
                  "perCurrencyBalances": [
                    {"currency": "CAD", "cash": 243971.7, "marketValue": 6017, "totalEquity": 249988.7},
                    {"currency": "USD", "cash": 0.10000000000000001, "marketValue": 0, "totalEquity": 198.1}
                  ],
                  "combinedBalances": [
                    {"currency": "CAD", "cash": 999, "marketValue": 0, "totalEquity": 0}
                  ]
                }
                """
            )
        )

        val balances = client.cashBalances(session(), AccountNumber("26598145"))

        assertEquals(
            mapOf(
                Currency.getInstance("CAD") to BigDecimal("243971.7"),
                Currency.getInstance("USD") to BigDecimal("0.10000000000000001")
            ),
            balances
        )
        val request = server.takeRequest()
        assertEquals("/v1/accounts/26598145/balances", request.url.encodedPath)
        assertEquals("Bearer access-1", request.headers["Authorization"])
    }

    @Test
    fun `refresh rejects a used or invalid refresh token`() {
        server.enqueue(MockResponse.Builder().code(400).body("Bad Request").build())

        val error = assertThrows(QuestradeException.InvalidToken::class.java) {
            runBlocking { client.refresh("refresh-1") }
        }

        assertTokenAbsent(error, "refresh-1")
    }

    @Test
    fun `refresh rejects an unauthorized refresh token`() {
        server.enqueue(MockResponse.Builder().code(401).build())

        assertThrows(QuestradeException.InvalidToken::class.java) {
            runBlocking { client.refresh("refresh-1") }
        }
    }

    @Test
    fun `a data call with an expired access token fails as an invalid token`() {
        server.enqueue(
            MockResponse.Builder().code(
                401
            ).body("""{"code": 1017, "message": "Access token is invalid"}""").build()
        )

        val error = assertThrows(QuestradeException.InvalidToken::class.java) {
            runBlocking { client.accounts(session()) }
        }

        assertTokenAbsent(error, "access-1")
    }

    @Test
    fun `a server error reports its status code`() {
        server.enqueue(MockResponse.Builder().code(503).build())

        val error = assertThrows(QuestradeException.HttpError::class.java) {
            runBlocking { client.cashBalances(session(), AccountNumber("26598145")) }
        }

        assertEquals(503, error.code)
    }

    @Test
    fun `server errors and rate limits are temporary but other HTTP errors are not`() {
        val temporary = listOf(404, 429, 500, 503).associateWith {
            QuestradeException.HttpError(it).isTemporary
        }

        assertEquals(mapOf(404 to false, 429 to true, 500 to true, 503 to true), temporary)
    }

    @Test
    fun `an unreachable server fails as a network failure`() {
        server.close()

        val error = assertThrows(QuestradeException.NetworkFailure::class.java) {
            runBlocking { client.refresh("refresh-1") }
        }

        assertTokenAbsent(error, "refresh-1")
    }

    @Test
    fun `refresh never resends a refresh token the server may have already used`() {
        server.enqueue(json("""{"accounts": []}"""))
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse.Builder().code(400).build())
        runBlocking { client.accounts(session()) }

        assertThrows(QuestradeException.NetworkFailure::class.java) {
            runBlocking { client.refresh("refresh-1") }
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a malformed token response fails without quoting the tokens`() {
        server.enqueue(json("""{"access_token": "access-1", "refresh_token": "refresh-2", """))

        val error = assertThrows(QuestradeException.UnexpectedResponse::class.java) {
            runBlocking { client.refresh("refresh-1") }
        }

        assertTokenAbsent(error, "access-1")
        assertTokenAbsent(error, "refresh-2")
    }

    @Test
    fun `a balance without a numeric cash value fails as an unexpected response`() {
        server.enqueue(json("""{"perCurrencyBalances": [{"currency": "CAD", "cash": null}]}"""))

        assertThrows(QuestradeException.UnexpectedResponse::class.java) {
            runBlocking { client.cashBalances(session(), AccountNumber("26598145")) }
        }
    }

    @Test
    fun `session never prints its tokens`() {
        val text = session().toString()

        assertFalse(text.contains("access-1"))
        assertFalse(text.contains("refresh-2"))
    }

    private fun session() = Session("access-1", server.url("/"), "refresh-2")

    private fun assertTokenAbsent(error: Throwable, token: String) {
        generateSequence(error) { it.cause }.forEach {
            assertFalse(it.toString(), it.toString().contains(token))
        }
    }

    private fun json(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(body.trimIndent())
        .build()
}
