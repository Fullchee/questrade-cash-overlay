package com.fullchee.questradecashoverlay.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fullchee.questradecashoverlay.R
import com.fullchee.questradecashoverlay.logins.RefreshTokenStepsDialog
import kotlinx.coroutines.launch

private enum class MenuDialog { REFRESH_TOKEN_STEPS, LANGUAGE }

/** Side menu around the main screen; [content] gets the action that opens the menu. */
@Composable
fun MainMenu(openSetup: () -> Unit, content: @Composable (openMenu: () -> Unit) -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<MenuDialog?>(null) }

    fun choose(action: () -> Unit) {
        scope.launch { drawer.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp)
                )
                MenuItem(R.string.menu_setup_steps) { choose(openSetup) }
                MenuItem(R.string.token_steps_title) {
                    choose {
                        dialog =
                            MenuDialog.REFRESH_TOKEN_STEPS
                    }
                }
                MenuItem(R.string.menu_language) { choose { dialog = MenuDialog.LANGUAGE } }
            }
        }
    ) {
        content { scope.launch { drawer.open() } }
    }

    when (dialog) {
        MenuDialog.REFRESH_TOKEN_STEPS -> RefreshTokenStepsDialog(onDismiss = { dialog = null })
        MenuDialog.LANGUAGE -> LanguageDialog(onDismiss = { dialog = null })
        null -> {}
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { currentLanguage(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(
                                selected = language == current,
                                role = Role.RadioButton,
                                onClick = {
                                    onDismiss()
                                    setLanguage(context, language)
                                }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == current, onClick = null)
                        Text(stringResource(language.label), Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_cancel)) }
        }
    )
}
