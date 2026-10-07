package it.finardi.schiena.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R
import it.finardi.schiena.data.SessionLog
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onStart: () -> Unit,
    onMinimal: () -> Unit,
    onLog: (SessionOutcome) -> Unit,
    onPlan: () -> Unit,
    onRedFlags: () -> Unit,
    onWorkOff: (Boolean) -> Unit,
    onSettings: () -> Unit = {},
    onPainCheck: (Long) -> Unit = {},
    notificationsDisabled: Boolean = false,
    notificationRequestError: Boolean = false,
    onDiary: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val today = state.today
    val phase = state.phase
    val entry = state.entry
    var requestedWorkOff by rememberSaveable(today) { mutableStateOf(state.workOff) }
    val ready = !state.loading && !state.error && today != null && phase != null && entry != null
    var otherActions by rememberSaveable(today) { mutableStateOf(false) }
    val recorded = state.history.filter { it.date == today && it.sessionType == entry?.sessionType }.maxByOrNull { it.id }
    val pending = state.pendingChecks > 0 && state.pendingSessionId != null

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_today), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(onClick = onSettings, enabled = ready) {
                            Icon(Icons.Default.Settings, stringResource(R.string.s3_settings))
                        }
                    }
                    today?.let { Text(it.format(dateFormatter), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            when {
                state.loading -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        HomeMessage(stringResource(R.string.home_loading))
                    }
                }
                !ready -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeMessage(stringResource(R.string.home_load_error), MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                else -> {
                    if (pending) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeHeading(stringResource(R.string.home_pending_title))
                                HomeMessage(stringResource(R.string.home_pending_count, state.pendingChecks))
                                Button(onClick = { state.pendingSessionId?.let(onPainCheck) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.guided_check))
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeHeading(stringResource(R.string.home_today_session))
                            Text(stringResource(sessionLabel(entry!!.sessionType)), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.reminder_time, entry.reminderTime.format(timeFormatter)))
                            if (recorded != null) {
                                HomeMessage(stringResource(R.string.guided_recorded))
                                Text(stringResource(outcomeLabel(recorded.outcome)))
                                if (recorded.outcome != SessionOutcome.SKIPPED) {
                                    Text(stringResource(statusLabel(recorded.status)), color = statusColor(recorded.status))
                                }
                                OutlinedButton(onClick = { onLog(recorded.outcome) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.guided_edit_record))
                                }
                            } else {
                                val strength = entry.sessionType in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)
                                val startLabel = if (strength) R.string.home_start else R.string.guided_record_activity
                                if (pending) {
                                    OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(stringResource(startLabel))
                                    }
                                } else {
                                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text(stringResource(startLabel))
                                    }
                                }
                            }
                            OutlinedButton(onClick = onMinimal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.home_minimal))
                            }
                            Text(stringResource(R.string.home_weekly_count, state.completed, state.target))
                            LinearProgressIndicator(
                                progress = { if (state.target > 0) (state.completed.toFloat() / state.target).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (notificationsDisabled) HomeMessage(stringResource(R.string.s3_notifications_off))
                            if (notificationRequestError) HomeMessage(stringResource(R.string.s3_request_invalid), MaterialTheme.colorScheme.error)
                            TextButton(onClick = onPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.weekly_plan_title))
                            }
                            TextButton(onClick = onDiary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.guided_diary))
                            }
                            TextButton(onClick = onRedFlags, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.home_red_flags))
                            }
                            TextButton(onClick = { otherActions = !otherActions }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.guided_other_actions))
                                Icon(if (otherActions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                            }
                        }
                    }
                    if (otherActions || state.actionError) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeHeading(stringResource(R.string.home_quick_log))
                            SessionOutcome.entries.forEach { outcome ->
                                TextButton(onClick = { onLog(outcome) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(outcomeLabel(outcome)))
                                }
                            }
                            Text(stringResource(R.string.current_phase, phase!!.id, phase.name))
                            Text(stringResource(R.string.home_phase_week, state.phaseWeek, phase.minWeeks))
                            HomeHeading(stringResource(R.string.home_office_breaks))
                            Text(stringResource(R.string.home_breaks_count, state.breaksDone, state.breaksTarget))
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(value = state.workOff, role = Role.Switch, onValueChange = { value ->
                                        requestedWorkOff = value
                                        onWorkOff(value)
                                    })
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(stringResource(R.string.home_work_off), modifier = Modifier.weight(1f))
                                Switch(checked = state.workOff, onCheckedChange = null)
                            }
                            if (state.actionError) {
                                HomeMessage(stringResource(R.string.home_action_error), MaterialTheme.colorScheme.error)
                                TextButton(
                                    onClick = { onWorkOff(requestedWorkOff) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiaryScreen(state: HomeUiState, onBack: () -> Unit, onRedFlags: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val history = state.history.filter { log ->
        state.today?.let { log.date >= it.minusDays(13) && log.date <= it } == true
    }.sortedWith(compareByDescending<SessionLog> { it.date }.thenByDescending { it.id })
    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back)) }
                    HomeHeading(stringResource(R.string.guided_diary))
                }
                Text(stringResource(R.string.home_history_title))
            }
            if (history.isEmpty()) item { Text(stringResource(R.string.home_history_empty)) }
            items(history, key = { it.id }) { log ->
                HistoryEntry(log, formatter, onRedFlags)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HomeHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun HomeMessage(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun HistoryEntry(log: SessionLog, formatter: DateTimeFormatter, onRedFlags: () -> Unit) {
    val skipped = log.outcome == SessionOutcome.SKIPPED
    val color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else statusColor(log.status)
    val status = if (skipped) R.string.home_status_not_applicable else statusLabel(log.status)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(log.date.format(formatter), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(sessionLabel(log.sessionType)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(outcomeLabel(log.outcome)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(12.dp).background(color, CircleShape),
                )
                Text(stringResource(status), color = color, modifier = Modifier.weight(1f))
            }
        }
        if (log.status == SessionStatus.RED) {
            TextButton(
                onClick = onRedFlags,
                modifier = Modifier.fillMaxWidth().widthIn(min = 48.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.home_red_flags)) }
        }
    }
}

@Composable
private fun statusColor(status: SessionStatus): Color {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (status) {
        SessionStatus.PENDING, SessionStatus.GREEN -> if (darkSurface) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        SessionStatus.YELLOW -> if (darkSurface) Color(0xFFFFE082) else Color(0xFF6D4C00)
        SessionStatus.RED -> MaterialTheme.colorScheme.error
        SessionStatus.UNVERIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@StringRes
fun sessionLabel(type: SessionType): Int = when (type) {
    SessionType.STRENGTH_A -> R.string.session_strength_a
    SessionType.STRENGTH_B -> R.string.session_strength_b
    SessionType.AEROBIC -> R.string.session_aerobic
    SessionType.PILATES -> R.string.session_pilates
    SessionType.FREE -> R.string.session_free
    SessionType.ACTIVE_REST -> R.string.session_active_rest
}

@StringRes
private fun outcomeLabel(outcome: SessionOutcome): Int = when (outcome) {
    SessionOutcome.DONE -> R.string.home_outcome_done
    SessionOutcome.MINIMAL -> R.string.home_outcome_minimal
    SessionOutcome.SKIPPED -> R.string.home_outcome_skipped
}

@StringRes
private fun statusLabel(status: SessionStatus): Int = when (status) {
    SessionStatus.PENDING -> R.string.home_status_pending
    SessionStatus.GREEN -> R.string.home_status_green
    SessionStatus.YELLOW -> R.string.home_status_yellow
    SessionStatus.RED -> R.string.home_status_red
    SessionStatus.UNVERIFIED -> R.string.home_status_unverified
}