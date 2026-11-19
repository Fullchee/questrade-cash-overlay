package com.fullchee.questradecashoverlay.logins

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fullchee.questradecashoverlay.R
import com.fullchee.questradecashoverlay.questrade.QuestradeClient
import com.fullchee.questradecashoverlay.questrade.QuestradeException
import kotlinx.coroutines.launch

/** What the token form is for: a new login, or a fresh token for a broken one. */
private sealed interface TokenForm {
    data object Adding : TokenForm

    data class Reconnecting(val login: Login) : TokenForm
}

@Composable
fun LoginsScreen(store: LoginStore, questrade: QuestradeClient, openMenu: () -> Unit) {
    val logins by store.logins.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<TokenForm?>(null) }
    var removing by remember { mutableStateOf<Login?>(null) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var showingTokenSteps by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        checking = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: QuestradeException) {
                error = messageFor(e)
            } finally {
                checking = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = openMenu) {
                Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.menu_open))
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
        logins.forEach { login ->
            LoginRow(
                login = login,
                enabled = !checking,
                onCheck = { run { check(store, questrade, login.id) } },
                onReconnect = { form = TokenForm.Reconnecting(login) },
                onRemove = { removing = login }
            )
        }
        if (checking) Text(stringResource(R.string.logins_checking))
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        when (val current = form) {
            null -> Button(onClick = { form = TokenForm.Adding }, enabled = !checking) {
                Text(stringResource(R.string.logins_add))
            }
            else -> TokenEditor(
                form = current,
                enabled = !checking,
                onCancel = { form = null },
                onHelp = { showingTokenSteps = true },
                onSave = { name, token ->
                    run {
                        save(store, questrade, current, name, token, onTokenKept = { form = null })
                    }
                }
            )
        }
    }

    if (showingTokenSteps) RefreshTokenStepsDialog(onDismiss = { showingTokenSteps = false })

    removing?.let { login ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.logins_remove_title, login.name)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch { store.remove(login.id) }
                }) { Text(stringResource(R.string.logins_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.logins_cancel))
                }
            }
        )
    }
}

@Composable
private fun LoginRow(
    login: Login,
    enabled: Boolean,
    onCheck: () -> Unit,
    onReconnect: () -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(login.name, style = MaterialTheme.typography.titleMedium)
        when (login.status) {
            LoginStatus.Ok -> Text(
                stringResource(R.string.logins_status_ok),
                color = MaterialTheme.colorScheme.primary
            )
            LoginStatus.NeedsReconnecting -> Text(
                stringResource(R.string.logins_status_needs_reconnecting),
                color = MaterialTheme.colorScheme.error
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCheck, enabled = enabled) {
                Text(stringResource(R.string.logins_check))
            }
            if (login.status == LoginStatus.NeedsReconnecting) {
                OutlinedButton(onClick = onReconnect, enabled = enabled) {
                    Text(stringResource(R.string.logins_paste_new_token))
                }
            }
            OutlinedButton(onClick = onRemove, enabled = enabled) {
                Text(stringResource(R.string.logins_remove))
            }
        }
    }
}

@Composable
private fun TokenEditor(
    form: TokenForm,
    enabled: Boolean,
    onCancel: () -> Unit,
    onHelp: () -> Unit,
    onSave: (name: String, token: String) -> Unit
) {
    var name by remember(form) {
        mutableStateOf((form as? TokenForm.Reconnecting)?.login?.name.orEmpty())
    }
    var token by remember(form) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (form == TokenForm.Adding) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.logins_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.logins_refresh_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onHelp) { Text(stringResource(R.string.logins_token_help)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(name.trim(), token.trim())
                    // The token lives only in the store once saved, never in the form.
                    token = ""
                },
                enabled = enabled && name.isNotBlank() && token.isNotBlank()
            ) { Text(stringResource(R.string.logins_save)) }
            OutlinedButton(onClick = onCancel, enabled = enabled) {
                Text(stringResource(R.string.logins_cancel))
            }
        }
    }
}

/** Rotates the token once to prove it works, then loads the accounts it can read. */
private suspend fun check(store: LoginStore, questrade: QuestradeClient, id: String) {
    val session = store.rotate(id, questrade::refresh)
    store.setAccounts(id, questrade.accounts(session).map { it.number })
}

/**
 * Stores the pasted token and checks it. Calls [onTokenKept] once the login holds a token that may be live,
 * because Questrade may have spent the pasted one: saving the form again would overwrite the replacement.
 */
private suspend fun save(
    store: LoginStore,
    questrade: QuestradeClient,
    form: TokenForm,
    name: String,
    token: String,
    onTokenKept: () -> Unit
) {
    val id = when (form) {
        TokenForm.Adding -> store.add(name, token)
        is TokenForm.Reconnecting -> form.login.id.also { store.reconnect(it, token) }
    }
    val session = try {
        store.rotate(id, questrade::refresh)
    } catch (e: QuestradeException) {
        when {
            e.isTemporary -> onTokenKept()
            // Questrade answered and the token never worked for this login, so dropping it loses nothing.
            form == TokenForm.Adding -> store.remove(id)
        }
        throw e
    }
    onTokenKept()
    store.setAccounts(id, questrade.accounts(session).map { it.number })
}

@StringRes
private fun messageFor(e: QuestradeException): Int = when (e) {
    is QuestradeException.InvalidToken -> R.string.logins_error_invalid_token
    is QuestradeException.NetworkFailure -> R.string.logins_error_network
    is QuestradeException.HttpError, is QuestradeException.UnexpectedResponse ->
        R.string.logins_error_questrade
}
