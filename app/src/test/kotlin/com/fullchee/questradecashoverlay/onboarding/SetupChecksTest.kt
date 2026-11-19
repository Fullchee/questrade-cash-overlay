package com.fullchee.questradecashoverlay.onboarding

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.overlay.CashOverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SetupChecksTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val checks = SetupChecks(app)

    private fun enableAccessibilityServices(value: String) {
        Settings.Secure.putString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            value
        )
    }

    private val overlayService = ComponentName(
        app,
        CashOverlayService::class.java
    ).flattenToString()

    @Test
    fun accessibilityIsNotDoneWhenNoServiceIsEnabled() {
        assertFalse(checks.isDone(SetupStep.ACCESSIBILITY))
    }

    @Test
    fun accessibilityIsNotDoneWhenOnlyAnotherServiceIsEnabled() {
        enableAccessibilityServices("com.example/.OtherService")

        assertFalse(checks.isDone(SetupStep.ACCESSIBILITY))
    }

    @Test
    fun accessibilityIsDoneWhenTheOverlayServiceIsEnabled() {
        enableAccessibilityServices("com.example/.OtherService:$overlayService")

        assertTrue(checks.isDone(SetupStep.ACCESSIBILITY))
    }

    @Test
    fun restrictedSettingsIsNotDoneBeforeAccessibilityIsEnabled() {
        assertFalse(checks.isDone(SetupStep.RESTRICTED_SETTINGS))
    }

    @Test
    fun restrictedSettingsIsDoneOnceAccessibilityIsEnabled() {
        enableAccessibilityServices(overlayService)

        assertTrue(checks.isDone(SetupStep.RESTRICTED_SETTINGS))
    }

    @Test
    fun notificationsIsNotDoneWithoutThePermission() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(checks.isDone(SetupStep.NOTIFICATIONS))
    }

    @Test
    fun notificationsIsDoneWithThePermission() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(checks.isDone(SetupStep.NOTIFICATIONS))
    }

    @Test
    fun batteryIsNotDoneWhileOptimised() {
        assertFalse(checks.isDone(SetupStep.BATTERY))
    }

    @Test
    fun batteryIsDoneWhenOptimisationIsIgnored() {
        val powerManager = app.getSystemService(PowerManager::class.java)
        shadowOf(powerManager).setIgnoringBatteryOptimizations(app.packageName, true)

        assertTrue(checks.isDone(SetupStep.BATTERY))
    }

    @Test
    fun stepsSkipAutostartWithoutXiaomiSecurityCenter() {
        assertEquals(SetupStep.entries - SetupStep.AUTOSTART, checks.steps())
    }

    @Test
    fun stepsIncludeAutostartWithXiaomiSecurityCenter() {
        shadowOf(app.packageManager).addActivityIfNotPresent(xiaomiAutostart)

        assertEquals(SetupStep.entries, checks.steps())
    }

    @Test
    fun autostartIsNotDoneUntilTicked() {
        assertFalse(checks.isDone(SetupStep.AUTOSTART))
    }

    @Test
    fun autostartIsDoneOnceTicked() {
        checks.setAutostartTicked(true)

        assertTrue(SetupChecks(app).isDone(SetupStep.AUTOSTART))
    }

    @Test
    fun onboardingIsNotFinishedOnAFreshInstall() {
        assertFalse(checks.isOnboardingFinished())
    }

    @Test
    fun onboardingIsFinishedOnceMarked() {
        checks.markOnboardingFinished()

        assertTrue(SetupChecks(app).isOnboardingFinished())
    }
}
