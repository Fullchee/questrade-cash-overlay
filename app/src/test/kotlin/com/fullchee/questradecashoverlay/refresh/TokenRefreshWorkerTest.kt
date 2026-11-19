package com.fullchee.questradecashoverlay.refresh

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.fullchee.questradecashoverlay.MainActivity
import com.fullchee.questradecashoverlay.logins.Login
import com.fullchee.questradecashoverlay.logins.LoginStatus
import com.fullchee.questradecashoverlay.logins.LoginStore
import com.fullchee.questradecashoverlay.logins.memoryDataStore
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
    fun `tapping the notification opens the app`() = runTest {
        store.add("Mom", "mom-1")

        runWorker { throw QuestradeException.InvalidToken() }

        val tap = shadowOf(notifications().single().contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, tap.component?.className)
    }

    private suspend fun runWorker(refresh: suspend (String) -> Session = questrade) =
        TestListenableWorkerBuilder<TokenRefreshWorker>(app)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ) = TokenRefreshWorker(appContext, workerParameters, store, refresh)
                }
            )
            .build()
            .doWork()

    private fun notifications() =
        shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun session(refreshToken: String) =
        Session("access", "https://api01.iq.questrade.com/".toHttpUrl(), refreshToken)
}
