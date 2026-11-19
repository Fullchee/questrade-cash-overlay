package com.fullchee.questradecashoverlay.logins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fullchee.questradecashoverlay.R

internal const val QUESTRADE_API_CENTRE = "https://login.questrade.com/APIAccess/UserApps.aspx"

private val steps = listOf(
    R.string.token_steps_1,
    R.string.token_steps_2,
    R.string.token_steps_3,
    R.string.token_steps_4,
    R.string.token_steps_5
)

/** How the owner makes a refresh token on Questrade's site to paste into a login. */
@Composable
fun RefreshTokenStepsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.token_steps_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                steps.forEach { Text(stringResource(it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { uriHandler.openUri(QUESTRADE_API_CENTRE) }) {
                Text(stringResource(R.string.token_steps_open_questrade))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.token_steps_close)) }
        }
    )
}
