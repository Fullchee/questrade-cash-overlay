package com.fullchee.questradecashoverlay.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit
import com.fullchee.questradecashoverlay.overlay.CashOverlayService

enum class SetupStep { RESTRICTED_SETTINGS, ACCESSIBILITY, NOTIFICATIONS, BATTERY, AUTOSTART }

class SetupChecks(private val context: Context) {
    private val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)

    fun isDone(step: SetupStep): Boolean = when (step) {
        // Android blocks enabling a sideloaded app's accessibility service until restricted settings are allowed.
        SetupStep.RESTRICTED_SETTINGS, SetupStep.ACCESSIBILITY -> isOverlayServiceEnabled()
        SetupStep.NOTIFICATIONS ->
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        SetupStep.BATTERY ->
            context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
        // No API reports Xiaomi Autostart, so the owner ticks it.
        SetupStep.AUTOSTART -> prefs.getBoolean(AUTOSTART_TICKED, false)
    }

    fun setAutostartTicked(ticked: Boolean) = prefs.edit { putBoolean(AUTOSTART_TICKED, ticked) }

    fun isOnboardingFinished(): Boolean = prefs.getBoolean(ONBOARDING_FINISHED, false)

    fun markOnboardingFinished() = prefs.edit { putBoolean(ONBOARDING_FINISHED, true) }

    private fun isOverlayServiceEnabled(): Boolean {
        val overlayService = ComponentName(context, CashOverlayService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == overlayService }
    }

    private companion object {
        const val AUTOSTART_TICKED = "autostart_ticked"
        const val ONBOARDING_FINISHED = "onboarding_finished"
    }
}
