package com.fullchee.questradecashoverlay.refresh

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.MainActivity
import com.fullchee.questradecashoverlay.logins.Login
import com.fullchee.questradecashoverlay.logins.LoginStatus
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.logins.memoryDataStore
import com.fullchee.questradecashoverlay.questrade.Account
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import com.fullchee.questradecashoverlay.questrade.Session
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TokenRefreshWorkerTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val store = LoginStore(memoryDataStore())

    /** Hands out the next token for each login's current one, as Questrade does. */
    private val spent = mutableListOf<String>()
    private val questrade: suspend (String) -> Session = { token ->
        spent += token
        session(refreshToken = "$token+")
    }

    @Test
    fun `all logins rotate and no notification is posted`() = runTest {
        val mom = store.add("Mom", "mom-1")
        val dad = store.add("Dad", "dad-1")

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf("mom-1", "dad-1"), spent)
        assertEquals(
            listOf(Login(mom, "Mom", LoginStatus.Ok), Login(dad, "Dad", LoginStatus.Ok)),
            store.logins.first()
        )
        assertEquals(0, notifications().size)
    }

    @Test
    fun `a rejected login needs reconnecting and posts one notification naming it`() = runTest {
        val mom = store.add("Mom", "mom-1")
        val dad = store.add("Dad", "dad-1")

        val result = runWorker { token ->
            if (token == "mom-1") throw QuestradeException.InvalidToken()
            questrade(token)
        }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            listOf(
                Login(mom, "Mom", LoginStatus.NeedsReconnecting),
                Login(dad, "Dad", LoginStatus.Ok)
            ),
            store.logins.first()
        )
        assertEquals(listOf("dad-1"), spent)
        val notification = notifications().single()
        assertEquals("Mom needs reconnecting", shadowOf(notification).contentTitle)
    }

    @Test
    fun `a network failure retries later without marking or notifying`() = runTest {
        val mom = store.add("Mom", "mom-1")
        val dad = store.add("Dad", "dad-1")

        val result = runWorker { token ->
            if (token == "mom-1") throw QuestradeException.NetworkFailure(IOException())
            questrade(token)
        }

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(
            listOf(Login(mom, "Mom", LoginStatus.Ok), Login(dad, "Dad", LoginStatus.Ok)),
            store.logins.first()
        )
        assertEquals(listOf("dad-1"), spent)
        assertEquals(0, notifications().size)
    }

    @Test
    fun `a Questrade outage retries later without marking or notifying`() = runTest {
        val mom = store.add("Mom", "mom-1")

        val result = runWorker { throw QuestradeException.HttpError(503) }

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf(Login(mom, "Mom", LoginStatus.Ok)), store.logins.first())
        assertEquals(0, notifications().size)
    }

    @Test
    fun `each login's accounts are reloaded`() = runTest {
        val mom = store.add("Mom", "mom-1")
        store.setAccounts(mom, listOf(AccountNumber("11111111")))

        runWorker(accounts = { listOf(account("11111111"), account("22222222")) })

        assertEquals(
            listOf(AccountNumber("11111111"), AccountNumber("22222222")),
            store.logins.first().single().accounts
        )
    }

    @Test
    fun `a failed account reload keeps the old accounts and the new token`() = runTest {
        val mom = store.add("Mom", "mom-1")
        store.setAccounts(mom, listOf(AccountNumber("11111111")))

        val result = runWorker(accounts = { throw QuestradeException.HttpError(503) })

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(
            listOf(Login(mom, "Mom", LoginStatus.Ok, listOf(AccountNumber("11111111")))),
            store.logins.first()
        )
        assertEquals(listOf("mom-1"), spent)
        assertEquals(0, notifications().size)
    }

    @Test
    fun `tapping the notification opens the app`() = runTest {
        store.add("Mom", "mom-1")

        runWorker { throw QuestradeException.InvalidToken() }

        val tap = shadowOf(notifications().single().contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
    }

    private suspend fun runWorker(
        accounts: suspend (Session) -> List<Account> = { emptyList() },
        refresh: suspend (String) -> Session = questrade
    ) = TestListenableWorkerBuilder<TokenRefreshWorker>(app)
        .setWorkerFactory(
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ) = TokenRefreshWorker(appContext, workerParameters, store, refresh, accounts)
            }
        )
        .build()
        .doWork()

    private fun notifications() =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun account(number: String) = Account(AccountNumber(number), "TFSA")

    private fun session(refreshToken: String) =
        Session("access", "https://api01.iq.questrade.com/".toHttpUrl(), refreshToken)
}
