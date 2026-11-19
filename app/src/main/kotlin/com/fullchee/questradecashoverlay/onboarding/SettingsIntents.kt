package com.fullchee.questradecashoverlay.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun settingsIntent(step: SetupStep, context: Context): Intent = when (step) {
    SetupStep.RESTRICTED_SETTINGS -> appInfoIntent(context)
    SetupStep.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    SetupStep.NOTIFICATIONS ->
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    SetupStep.BATTERY ->
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
    SetupStep.AUTOSTART -> Intent().setComponent(xiaomiAutostart)
}

private fun appInfoIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)
