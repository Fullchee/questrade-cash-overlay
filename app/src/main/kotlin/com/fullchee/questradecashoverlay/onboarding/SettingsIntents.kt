package com.fullchee.questradecashoverlay.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

private val xiaomiAutostart = ComponentName(
    "com.miui.securitycenter",
    "com.miui.permcenter.autostart.AutoStartManagementActivity"
)

fun settingsIntent(step: SetupStep, context: Context): Intent = when (step) {
    SetupStep.RESTRICTED_SETTINGS -> appInfoIntent(context)
    SetupStep.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    SetupStep.NOTIFICATIONS ->
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    SetupStep.BATTERY ->
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
    SetupStep.AUTOSTART -> autostartIntent(context)
}

// Only Xiaomi phones ship the security center; elsewhere (the emulator) App info is the closest page.
private fun autostartIntent(context: Context): Intent {
    val xiaomi = Intent().setComponent(xiaomiAutostart)
    return if (context.packageManager.resolveActivity(xiaomi, 0) !=
        null
    ) {
        xiaomi
    } else {
        appInfoIntent(context)
    }
}

private fun appInfoIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)
