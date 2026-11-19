package com.fullchee.questradecashoverlay

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.logins.QUESTRADE_API_CENTRE
import com.fullchee.questradecashoverlay.onboarding.SetupChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun freshInstallStartsOnboarding() {
        ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Allow restricted settings").assertIsDisplayed()
    }

    @Test
    fun openSettingsStartsTheStepsSettingsPage() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Open settings").performClick()

        scenario.onActivity { activity ->
            val started = shadowOf(activity).nextStartedActivity
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        }
    }

    @Test
    fun finishingOnboardingShowsTheAppAndIsRemembered() {
        ActivityScenario.launch(MainActivity::class.java)
        repeat(3) { composeRule.onNodeWithText("Next").performClick() }

        composeRule.onNodeWithText("Finish").performClick()

        composeRule.onNodeWithText("Add login").assertIsDisplayed()
        assertTrue(SetupChecks(app).isOnboardingFinished())
    }

    @Test
    fun finishedOnboardingSkipsStraightToTheLogins() {
        SetupChecks(app).markOnboardingFinished()

        ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Add login").assertIsDisplayed()
    }

    @Test
    fun setupStepsReopensOnboardingFromTheMenu() {
        launchPastOnboarding()

        openMenuItem("Setup steps")

        composeRule.onNodeWithText("Allow restricted settings").assertIsDisplayed()
    }

    @Test
    fun theMenuShowsHowToGetARefreshTokenAndOpensQuestrade() {
        val scenario = launchPastOnboarding()

        openMenuItem("Get a refresh token")
        composeRule.onNodeWithText("Open Questrade").performClick()

        scenario.onActivity { activity ->
            val started = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, started.action)
            assertEquals(QUESTRADE_API_CENTRE, started.dataString)
        }
    }

    @Test
    fun choosingALanguageFromTheMenuSetsTheAppsLanguage() {
        launchPastOnboarding()

        openMenuItem("Language · 语言")
        composeRule.onNodeWithText("English").performClick()

        val locales = app.getSystemService(LocaleManager::class.java).applicationLocales
        assertEquals("en", locales.toLanguageTags())
    }

    private fun launchPastOnboarding(): ActivityScenario<MainActivity> {
        SetupChecks(app).markOnboardingFinished()
        return ActivityScenario.launch(MainActivity::class.java)
    }

    private fun openMenuItem(label: String) {
        composeRule.onNodeWithContentDescription("Open menu").performClick()
        composeRule.onNodeWithText(label).performClick()
    }
}
