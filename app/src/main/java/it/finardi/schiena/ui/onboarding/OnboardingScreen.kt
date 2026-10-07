package it.finardi.schiena.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R
import it.finardi.schiena.ui.settings.SettingsDraft
import it.finardi.schiena.ui.settings.SettingsUiState
import it.finardi.schiena.ui.settings.PermissionControls

@Composable
fun OnboardingScreen(
    state: SettingsUiState,
    onEdit: ((SettingsDraft) -> SettingsDraft) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onRedFlags: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    var remindersExpanded by rememberSaveable { mutableStateOf(false) }
    val draft = state.draft
    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.s3_onboarding), style = MaterialTheme.typography.headlineSmall)
            when {
                state.loading -> CircularProgressIndicator()
                state.loadError || draft == null -> {
                    Text(stringResource(R.string.plan_load_error))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    Text(stringResource(R.string.s3_disclaimer))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = draft.accepted, enabled = !state.saving, role = Role.Checkbox,
                        onValueChange = { accepted -> onEdit { it.copy(accepted = accepted) } }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = draft.accepted, enabled = !state.saving, onCheckedChange = null)
                        Text(stringResource(R.string.s3_accept), Modifier.weight(1f))
                    }
                    if (state.saveError) Text(stringResource(R.string.s3_save_error), color = MaterialTheme.colorScheme.error)
                    if (state.invalid) Text(stringResource(R.string.s3_invalid), color = MaterialTheme.colorScheme.error)
                    if (state.saving) CircularProgressIndicator()
                    Button(onClick = onSave, enabled = draft.accepted && !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(if (state.saveError) R.string.retry else R.string.guided_open_today))
                    }
                    TextButton(onClick = { remindersExpanded = !remindersExpanded }, enabled = !state.saving) {
                        Text(stringResource(R.string.guided_reminders))
                    }
                    if (remindersExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s3_notifications), Modifier.weight(1f))
                            Switch(checked = draft.notifications, enabled = !state.saving,
                                onCheckedChange = { enabled -> onEdit { it.copy(notifications = enabled) } })
                        }
                        PermissionControls(state.notificationsAllowed, state.exactAllowed, onPermissionsChanged)
                    }
                    TextButton(onClick = onRedFlags) { Text(stringResource(R.string.home_red_flags)) }
                }
            }
        }
    }
}