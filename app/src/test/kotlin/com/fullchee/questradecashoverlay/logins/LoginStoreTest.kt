package com.fullchee.questradecashoverlay.logins

import com.fullchee.questradecashoverlay.questrade.QuestradeException
import com.fullchee.questradecashoverlay.questrade.Session
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LoginStoreTest {
    @Test
    fun `rotate exchanges the stored token and stores the new one for the next rotation`() =
        runTest {
            val store = LoginStore(memoryDataStore())
            val id = store.add("Mom", "refresh-1")
            val spent = mutableListOf<String>()
            val refresh: suspend (String) -> Session = { token ->
                spent += token
                session(refreshToken = "refresh-${spent.size + 1}")
            }

            val session = store.rotate(id, refresh)
            store.rotate(id, refresh)

            assertEquals("refresh-2", session.refreshToken)
            assertEquals(listOf("refresh-1", "refresh-2"), spent)
            assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
        }

    @Test
    fun `a crash after the exchange but before the save leaves the login needing reconnecting`() =
        runTest {
            val dataStore = memoryDataStore()
            val store = LoginStore(dataStore)
            val id = store.add("Mom", "refresh-1")
            dataStore.writesBeforeCrash = 1

            assertThrows(IOException::class.java) {
                runBlocking { store.rotate(id) { session(refreshToken = "refresh-2") } }
            }

            assertEquals(
                listOf(Login(id, "Mom", LoginStatus.NeedsReconnecting)),
                store.logins.first()
            )
        }

    @Test
    fun `a caller cancelled during the exchange still saves the new token`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")

        launch {
            store.rotate(id) {
                currentCoroutineContext().cancel()
                session(refreshToken = "refresh-2")
            }
        }.join()

        assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
        assertEquals("refresh-2", store.rotate(id) { session(refreshToken = it) }.refreshToken)
    }

    @Test
    fun `a failed refresh marks only that login as needing reconnecting`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        val dad = store.add("Dad", "refresh-dad")

        assertThrows(QuestradeException.InvalidToken::class.java) {
            runBlocking { store.rotate(mom) { throw QuestradeException.InvalidToken() } }
        }

        assertEquals(
            listOf(
                Login(mom, "Mom", LoginStatus.NeedsReconnecting),
                Login(dad, "Dad", LoginStatus.Ok)
            ),
            store.logins.first()
        )
        assertEquals("refresh-dad", store.rotate(dad) { session(refreshToken = it) }.refreshToken)
    }

    @Test
    fun `a login needing reconnecting is ok again once a rotation succeeds`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")
        runCatching { store.rotate(id) { throw QuestradeException.InvalidToken() } }

        store.rotate(id) { session(refreshToken = "refresh-2") }

        assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
    }

    @Test
    fun `a network failure keeps the login's status and token for a retry`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")

        assertThrows(QuestradeException.NetworkFailure::class.java) {
            runBlocking {
                store.rotate(id) { throw QuestradeException.NetworkFailure(IOException()) }
            }
        }

        assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
        assertEquals("refresh-1", store.rotate(id) { session(refreshToken = it) }.refreshToken)
    }

    @Test
    fun `a Questrade outage keeps the login's status for a retry`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")

        runCatching { store.rotate(id) { throw QuestradeException.HttpError(503) } }

        assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
    }

    @Test
    fun `a client HTTP error leaves the login needing reconnecting`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")

        runCatching { store.rotate(id) { throw QuestradeException.HttpError(404) } }

        assertEquals(listOf(Login(id, "Mom", LoginStatus.NeedsReconnecting)), store.logins.first())
    }

    @Test
    fun `two rotations of the same login run one after the other`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")
        val firstExchange = CompletableDeferred<Unit>()
        val spent = mutableListOf<String>()
        var running = 0
        var mostRunning = 0
        val refresh: suspend (String) -> Session = { token ->
            spent += token
            mostRunning = maxOf(mostRunning, ++running)
            if (token == "refresh-1") firstExchange.await()
            running--
            session(refreshToken = "refresh-${spent.size + 1}")
        }

        val first = launch { store.rotate(id, refresh) }
        val second = launch { store.rotate(id, refresh) }
        testScheduler.advanceUntilIdle()
        firstExchange.complete(Unit)
        joinAll(first, second)

        assertEquals(1, mostRunning)
        assertEquals(listOf("refresh-1", "refresh-2"), spent)
    }

    @Test
    fun `rename changes only the name`() = runTest {
        val store = LoginStore(memoryDataStore())
        val id = store.add("Mom", "refresh-1")

        store.rename(id, "Mother")

        assertEquals(listOf(Login(id, "Mother", LoginStatus.Ok)), store.logins.first())
        assertEquals("refresh-1", store.rotate(id) { session(refreshToken = it) }.refreshToken)
    }

    @Test
    fun `reconnect replaces a broken login's token so the next rotation spends the new one`() =
        runTest {
            val store = LoginStore(memoryDataStore())
            val id = store.add("Mom", "refresh-1")
            runCatching { store.rotate(id) { throw QuestradeException.InvalidToken() } }

            store.reconnect(id, "pasted")

            assertEquals("pasted", store.rotate(id) { session(refreshToken = it) }.refreshToken)
            assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
        }

    @Test
    fun `remove drops the login and keeps the others`() = runTest {
        val store = LoginStore(memoryDataStore())
        val mom = store.add("Mom", "refresh-mom")
        val dad = store.add("Dad", "refresh-dad")

        store.remove(mom)

        assertEquals(listOf(Login(dad, "Dad", LoginStatus.Ok)), store.logins.first())
    }

    private fun session(refreshToken: String) =
        Session("access", "https://api01.iq.questrade.com/".toHttpUrl(), refreshToken)
}
