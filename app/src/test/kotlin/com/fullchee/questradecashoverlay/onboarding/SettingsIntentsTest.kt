package com.fullchee.questradecashoverlay.onboarding

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettingsIntentsTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val xiaomiAutostart = ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )

    @Test
    fun restrictedSettingsOpensAppInfo() {
        val intent = settingsIntent(SetupStep.RESTRICTED_SETTINGS, app)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }

    @Test
    fun accessibilityOpensAccessibilitySettings() {
        val intent = settingsIntent(SetupStep.ACCESSIBILITY, app)

        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }

    @Test
    fun notificationsOpensThisAppsNotificationSettings() {
        val intent = settingsIntent(SetupStep.NOTIFICATIONS, app)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(app.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun batteryAsksToIgnoreOptimisationForThisApp() {
        val intent = settingsIntent(SetupStep.BATTERY, app)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }

    @Test
    fun autostartOpensXiaomiSecurityCenterWhenInstalled() {
        shadowOf(app.packageManager).addActivityIfNotPresent(xiaomiAutostart)

        val intent = settingsIntent(SetupStep.AUTOSTART, app)

        assertEquals(xiaomiAutostart, intent.component)
    }

    @Test
    fun autostartFallsBackToAppInfoWithoutXiaomiSecurityCenter() {
        val intent = settingsIntent(SetupStep.AUTOSTART, app)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${app.packageName}", intent.dataString)
    }
}
