package it.finardi.schiena.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.finardi.schiena.R
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reminderFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

@Composable
fun WeeklyPlanRoute(viewModel: WeeklyPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeeklyPlanScreen(state, viewModel::retry, viewModel::restoreDefaults)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyPlanScreen(
    state: WeeklyPlanUiState,
    onRetry: () -> Unit,
    onRestoreDefaults: () -> Unit,
) {
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.weekly_plan_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.restore_defaults)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = { confirmRestore = true },
                            enabled = !state.isLoading && !state.isRestoring,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.restore_defaults))
                        }
                    }
                    when {
                        state.isLoading -> {
                            CircularProgressIndicator()
                            StatusText(stringResource(R.string.plan_loading))
                        }
                        state.hasLoadError -> {
                            StatusText(stringResource(R.string.plan_load_error))
                            Button(onClick = onRetry, enabled = !state.isRestoring) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                        else -> state.currentPhase?.let { phase ->
                            Text(
                                text = stringResource(R.string.current_phase, phase.id, phase.name),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    if (state.isRestoring) {
                        CircularProgressIndicator()
                        StatusText(stringResource(R.string.plan_restoring))
                    }
                    if (state.hasRestoreError) {
                        StatusText(stringResource(R.string.plan_restore_error))
                        Button(
                            onClick = { confirmRestore = true },
                            enabled = !state.isLoading && !state.isRestoring,
                        ) {
                            Text(stringResource(R.string.retry_restore))
                        }
                    }
                }
            }
            if (!state.isLoading && !state.hasLoadError) {
                items(state.plan, key = { it.dayOfWeek.value }) { entry ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(dayLabel(entry.dayOfWeek)), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(sessionLabel(entry.sessionType)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.reminder_time, entry.reminderTime.format(reminderFormatter)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.restore_defaults)) },
            text = { Text(stringResource(R.string.restore_defaults_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        onRestoreDefaults()
                    },
                    enabled = !state.isLoading && !state.isRestoring,
                ) { Text(stringResource(R.string.restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun StatusText(message: String) {
    Text(
        text = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@StringRes
private fun sessionLabel(type: SessionType): Int = when (type) {
    SessionType.STRENGTH_A -> R.string.session_strength_a
    SessionType.STRENGTH_B -> R.string.session_strength_b
    SessionType.AEROBIC -> R.string.session_aerobic
    SessionType.PILATES -> R.string.session_pilates
    SessionType.FREE -> R.string.session_free
    SessionType.ACTIVE_REST -> R.string.session_active_rest
}

@StringRes
private fun dayLabel(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.day_monday
    DayOfWeek.TUESDAY -> R.string.day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday
    DayOfWeek.THURSDAY -> R.string.day_thursday
    DayOfWeek.FRIDAY -> R.string.day_friday
    DayOfWeek.SATURDAY -> R.string.day_saturday
    DayOfWeek.SUNDAY -> R.string.day_sunday
}