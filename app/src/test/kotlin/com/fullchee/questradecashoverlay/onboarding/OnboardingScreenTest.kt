package com.fullchee.questradecashoverlay.onboarding

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val checks by lazy { SetupChecks(composeRule.activity) }
    private val opened = mutableListOf<SetupStep>()
    private var finished = false

    private fun showOnboarding() {
        composeRule.setContent {
            OnboardingScreen(
                checks = checks,
                openSettings = { opened += it },
                onFinished = { finished = true }
            )
        }
    }

    private fun clickNext(times: Int) {
        repeat(times) { composeRule.onNodeWithText("Next").performClick() }
    }

    @Test
    fun startsAtRestrictedSettings() {
        showOnboarding()

        composeRule.onNodeWithText("Allow restricted settings").assertIsDisplayed()
    }

    @Test
    fun openSettingsOpensTheCurrentStepsPage() {
        showOnboarding()

        composeRule.onNodeWithText("Open settings").performClick()

        assertEquals(listOf(SetupStep.RESTRICTED_SETTINGS), opened)
    }

    @Test
    fun hidesTheTickWhileTheStepIsNotDone() {
        showOnboarding()
        clickNext(2)

        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onNodeWithText("✓ Done").assertDoesNotExist()
    }

    @Test
    fun showsTheTickWhenTheStepIsDone() {
        shadowOf(
            composeRule.activity.application
        ).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showOnboarding()
        clickNext(2)

        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()
    }

    @Test
    fun rechecksTheStepOnResume() {
        showOnboarding()
        clickNext(2)

        shadowOf(
            composeRule.activity.application
        ).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()
    }

    @Test
    fun backReturnsToThePreviousStep() {
        showOnboarding()
        clickNext(1)

        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithText("Allow restricted settings").assertIsDisplayed()
    }

    @Test
    fun tickingAutostartMarksItDone() {
        showOnboarding()
        clickNext(4)

        composeRule.onNodeWithText("I turned on Autostart").performClick()

        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()
        assertTrue(checks.isDone(SetupStep.AUTOSTART))
    }

    @Test
    fun finishOnTheLastStepEndsOnboarding() {
        showOnboarding()
        clickNext(4)

        composeRule.onNodeWithText("Finish").performClick()

        assertTrue(finished)
    }
}
