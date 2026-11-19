package com.fullchee.questradecashoverlay.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fullchee.questradecashoverlay.R

private val steps = SetupStep.entries

private class StepText(@param:StringRes val title: Int, @param:StringRes val body: Int)

private fun textOf(step: SetupStep) = when (step) {
    SetupStep.RESTRICTED_SETTINGS -> StepText(
        R.string.onboarding_restricted_settings_title,
        R.string.onboarding_restricted_settings_body
    )
    SetupStep.ACCESSIBILITY -> StepText(
        R.string.onboarding_accessibility_title,
        R.string.onboarding_accessibility_body
    )
    SetupStep.NOTIFICATIONS -> StepText(
        R.string.onboarding_notifications_title,
        R.string.onboarding_notifications_body
    )
    SetupStep.BATTERY -> StepText(
        R.string.onboarding_battery_title,
        R.string.onboarding_battery_body
    )
    SetupStep.AUTOSTART -> StepText(
        R.string.onboarding_autostart_title,
        R.string.onboarding_autostart_body
    )
}

@Composable
fun OnboardingScreen(
    checks: SetupChecks,
    openSettings: (SetupStep) -> Unit,
    onFinished: () -> Unit
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Bumped on every resume so each check reruns after the owner returns from a settings page.
    var checkRound by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checkRound++
        onPauseOrDispose {}
    }
    val step = steps[index]
    val text = textOf(step)
    val done = remember(step, checkRound) { checks.isDone(step) }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(text.title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(text.body))
        Button(onClick = {
            openSettings(step)
        }) { Text(stringResource(R.string.onboarding_open_settings)) }
        if (step == SetupStep.AUTOSTART) {
            AutostartTick(ticked = done) { ticked ->
                checks.setAutostartTicked(ticked)
                checkRound++
            }
        }
        if (done) {
            Text(
                stringResource(R.string.onboarding_done),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (index > 0) {
                OutlinedButton(onClick = {
                    index--
                }) { Text(stringResource(R.string.onboarding_back)) }
            } else {
                Spacer(Modifier)
            }
            if (index < steps.lastIndex) {
                Button(onClick = { index++ }) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                Button(onClick = onFinished) { Text(stringResource(R.string.onboarding_finish)) }
            }
        }
    }
}

@Composable
private fun AutostartTick(ticked: Boolean, onTickedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.toggleable(
            value = ticked,
            role = Role.Checkbox,
            onValueChange = onTickedChange
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = ticked, onCheckedChange = null)
        Text(stringResource(R.string.onboarding_autostart_ticked))
    }
}
