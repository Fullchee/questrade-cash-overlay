package com.fullchee.questradecashoverlay.logins

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.AccountNumber
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import com.fullchee.questradecashoverlay.questrade.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val server = MockWebServer()
    private val store = LoginStore(memoryDataStore())

    @Before
    fun setUp() {
        server.start()
        composeRule.setContent {
            LoginsScreen(
                store,
                QuestradeClient(OkHttpClient(), loginServer = server.url("/")),
                openSetup = {}
            )
        }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun addingALoginChecksItsTokenThenListsItAsOkWithItsAccountsIndexed() {
        enqueueWorkingToken(accounts = listOf("11111111", "22222222"))

        addLogin("Mom", "refresh-secret")

        waitForText("OK")
        composeRule.onNodeWithText("Mom").assertIsDisplayed()
        composeRule.onAllNodesWithText("refresh-secret").assertCountEquals(0)
        val login = runBlocking { store.logins.first() }.single()
        val index = runBlocking { store.accountIndex.first() }
        assertEquals(login.id, index.loginFor(AccountNumber("11111111")))
        assertEquals(login.id, index.loginFor(AccountNumber("22222222")))
        assertEquals("refresh-secret", server.takeRequest().url.queryParameter("refresh_token"))
    }

    @Test
    fun addingAnInvalidTokenShowsAnErrorAndSavesNothing() {
        server.enqueue(MockResponse.Builder().code(400).build())

        addLogin("Mom", "refresh-bad")

        waitForText(REJECTED)
        composeRule.onAllNodesWithText("refresh-bad").assertCountEquals(0)
        composeRule.onNodeWithText("Save").assertIsDisplayed()
        assertEquals(emptyList<Login>(), runBlocking { store.logins.first() })
    }

    @Test
    fun aNetworkFailureWhileAddingKeepsTheLoginForALaterCheck() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())

        addLogin("Mom", "refresh-1")

        waitForText("Couldn't reach Questrade. Check the connection and try again.")
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        assertEquals(listOf("Mom"), runBlocking { store.logins.first() }.map { it.name })
    }

    @Test
    fun failingToLoadAccountsAfterTheRotationKeepsTheNewToken() {
        enqueueRotatedToken()
        server.enqueue(MockResponse.Builder().code(500).build())

        addLogin("Mom", "refresh-1")

        waitForText("Questrade sent an error. Try again later.")
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        val login = runBlocking { store.logins.first() }.single()
        assertEquals(LoginStatus.Ok, login.status)
        var stored = ""
        runBlocking {
            store.rotate(login.id) {
                stored = it
                Session("access", server.url("/"), it)
            }
        }
        assertEquals("refresh-next", stored)
    }

    @Test
    fun aQuestradeServerErrorAsksToTryAgainLater() {
        server.enqueue(MockResponse.Builder().code(500).build())

        addLogin("Mom", "refresh-1")

        waitForText("Questrade sent an error. Try again later.")
    }

    @Test
    fun saveStaysOffUntilTheLoginHasANameAndAToken() {
        composeRule.onNodeWithText("Add login").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Mom")

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Refresh token").performTextInput("refresh-1")
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun cancellingTheAddFormSavesNothing() {
        composeRule.onNodeWithText("Add login").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Mom")

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        assertEquals(emptyList<Login>(), runBlocking { store.logins.first() })
    }

    @Test
    fun aBrokenLoginOffersAPastedTokenThatReconnectsIt() {
        val id = brokenLogin("Mom")
        waitForText("Needs reconnecting")
        enqueueWorkingToken(accounts = listOf("33333333"))

        composeRule.onNodeWithText("Paste new token").performClick()
        composeRule.onNodeWithText("Refresh token").performTextInput("refresh-new")
        composeRule.onNodeWithText("Save").performClick()

        waitForText("OK")
        composeRule.onAllNodesWithText("refresh-new").assertCountEquals(0)
        assertEquals(
            id,
            runBlocking {
                store.accountIndex.first()
            }.loginFor(AccountNumber("33333333"))
        )
    }

    @Test
    fun aRejectedPastedTokenKeepsTheLoginAndTheForm() {
        brokenLogin("Mom")
        waitForText("Needs reconnecting")
        server.enqueue(MockResponse.Builder().code(400).build())

        composeRule.onNodeWithText("Paste new token").performClick()
        composeRule.onNodeWithText("Refresh token").performTextInput("refresh-bad")
        composeRule.onNodeWithText("Save").performClick()

        waitForText(REJECTED)
        composeRule.onNodeWithText("Save").assertIsDisplayed()
        assertEquals(listOf("Mom"), runBlocking { store.logins.first() }.map { it.name })
    }

    @Test
    fun anOkLoginDoesNotOfferAPastedToken() {
        runBlocking { store.add("Mom", "refresh-1") }

        waitForText("OK")

        composeRule.onAllNodesWithText("Paste new token").assertCountEquals(0)
    }

    @Test
    fun checkingALoginWhoseTokenStoppedWorkingMarksItBroken() {
        runBlocking { store.add("Mom", "refresh-1") }
        waitForText("OK")
        server.enqueue(MockResponse.Builder().code(400).build())

        composeRule.onNodeWithText("Check").performClick()

        waitForText("Needs reconnecting")
        composeRule.onNodeWithText(REJECTED).assertIsDisplayed()
    }

    @Test
    fun removingALoginAsksFirstThenDropsIt() {
        runBlocking { store.add("Mom", "refresh-1") }
        waitForText("Mom")

        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Yes, remove").performClick()

        composeRule.waitUntil { runBlocking { store.logins.first() }.isEmpty() }
        composeRule.onAllNodesWithText("Mom").assertCountEquals(0)
    }

    @Test
    fun cancellingTheRemovalKeepsTheLogin() {
        runBlocking { store.add("Mom", "refresh-1") }
        waitForText("Mom")

        composeRule.onNodeWithText("Remove").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onAllNodesWithText("Yes, remove").assertCountEquals(0)
        assertEquals(1, runBlocking { store.logins.first() }.size)
    }

    private fun addLogin(name: String, token: String) {
        composeRule.onNodeWithText("Add login").performClick()
        composeRule.onNodeWithText("Name").performTextInput(name)
        composeRule.onNodeWithText("Refresh token").performTextInput(token)
        composeRule.onNodeWithText("Save").performClick()
    }

    private fun brokenLogin(name: String): String = runBlocking {
        val id = store.add(name, "refresh-spent")
        runCatching { store.rotate(id) { throw QuestradeException.InvalidToken() } }
        id
    }

    private fun enqueueWorkingToken(accounts: List<String>) {
        enqueueRotatedToken()
        val numbers = accounts.joinToString { """{"type": "Margin", "number": "$it"}""" }
        server.enqueue(json("""{"accounts": [$numbers]}"""))
    }

    private fun enqueueRotatedToken() {
        server.enqueue(
            json(
                """{"access_token": "access", "refresh_token": "refresh-next", "api_server": "${server.url(
                    "/"
                )}"}"""
            )
        )
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun json(body: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private companion object {
        const val REJECTED = "Questrade rejected this token. Generate a new one and paste it here."
    }
}
