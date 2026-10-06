package it.finardi.schiena.ui.onboarding

import androidx.compose.runtime.Composable
import it.finardi.schiena.ui.settings.SettingsDraft
import it.finardi.schiena.ui.settings.SettingsScreen
import it.finardi.schiena.ui.settings.SettingsUiState

@Composable
fun OnboardingScreen(
    state: SettingsUiState,
    onEdit: ((SettingsDraft) -> SettingsDraft) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onRedFlags: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    SettingsScreen(state, onboarding = true, onEdit = onEdit, onSave = onSave, onRetry = onRetry,
        onBack = {}, onRedFlags = onRedFlags, onDebug = {}, onPermissionsChanged = onPermissionsChanged)
}