package it.finardi.schiena.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.sessionLabel
import java.time.DayOfWeek

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onboarding: Boolean,
    onEdit: ((SettingsDraft) -> SettingsDraft) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onRedFlags: () -> Unit,
    onDebug: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName.orEmpty()
    }
    val draft = state.draft
    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                if (!onboarding) IconButton(onClick = onBack, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back))
                }
                Text(stringResource(if (onboarding) R.string.s3_onboarding else R.string.s3_settings), style = MaterialTheme.typography.headlineSmall)
            }
            when {
                state.loading -> item { CircularProgressIndicator(); Text(stringResource(R.string.plan_loading)) }
                state.loadError || draft == null -> item {
                    Text(stringResource(R.string.plan_load_error))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    if (onboarding) item {
                        Text(stringResource(R.string.s3_disclaimer))
                        CheckRow(stringResource(R.string.s3_accept), draft.accepted, !state.saving) { selected -> onEdit { it.copy(accepted = selected) } }
                    }
                    item { Text(stringResource(R.string.weekly_plan_title), style = MaterialTheme.typography.titleMedium) }
                    draft.plan.forEach { entry -> item(key = entry.day.name) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(dayLabel(entry.day)), style = MaterialTheme.typography.titleMedium)
                            SessionTypeMenu(entry.type, !state.saving) { type ->
                                onEdit { it.copy(plan = it.plan.map { row -> if (row.day == entry.day) row.copy(type = type) else row }) }
                            }
                            TimeField(entry.time, R.string.s3_reminder, !state.saving) { time ->
                                onEdit { it.copy(plan = it.plan.map { row -> if (row.day == entry.day) row.copy(time = time) else row }) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 12.dp))
                    } }
                    item { TimeField(draft.checkTime, R.string.s3_check_time, !state.saving) { value -> onEdit { it.copy(checkTime = value) } } }
                    item {
                        Text(stringResource(R.string.s3_work_days), style = MaterialTheme.typography.titleMedium)
                        DayOfWeek.entries.forEach { day ->
                            CheckRow(stringResource(dayLabel(day)), day in draft.workDays, !state.saving) { selected ->
                                onEdit { it.copy(workDays = if (selected) it.workDays + day else it.workDays - day) }
                            }
                        }
                    }
                    item { TimeField(draft.workStart, R.string.s3_work_start, !state.saving) { value -> onEdit { it.copy(workStart = value) } } }
                    item { TimeField(draft.workEnd, R.string.s3_work_end, !state.saving) { value -> onEdit { it.copy(workEnd = value) } } }
                    item {
                        Text(stringResource(R.string.s3_interval, draft.interval))
                        Slider(value = draft.interval.toFloat(), onValueChange = { value -> onEdit { it.copy(interval = value.toInt()) } },
                            valueRange = 60f..90f, steps = 29, enabled = !state.saving)
                    }
                    item {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s3_notifications), Modifier.weight(1f))
                            Switch(checked = draft.notifications, onCheckedChange = { value -> onEdit { it.copy(notifications = value) } }, enabled = !state.saving)
                        }
                        PermissionControls(state.notificationsAllowed, state.exactAllowed, onPermissionsChanged)
                        if (state.schedulingError) {
                            Text(stringResource(R.string.s3_scheduling_error), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onPermissionsChanged) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    item {
                        if (state.invalid) Text(stringResource(R.string.s3_invalid), color = MaterialTheme.colorScheme.error)
                        if (state.saveError) Text(stringResource(R.string.s3_save_error), color = MaterialTheme.colorScheme.error)
                        if (state.saved) Text(stringResource(R.string.s3_saved))
                        if (state.saving) CircularProgressIndicator()
                        Button(onClick = onSave, enabled = !state.saving && (!onboarding || draft.accepted), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(if (state.saveError) R.string.retry else if (onboarding) R.string.s3_finish else R.string.s3_save))
                        }
                    }
                    item { TextButton(onClick = onRedFlags, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.home_red_flags)) } }
                    if (!onboarding) item {
                        Text(stringResource(R.string.s3_version, version), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .combinedClickable(onClick = {}, onLongClick = onDebug).padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeField(value: String, @StringRes label: Int, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(stringResource(label)) }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SessionTypeMenu(type: SessionType, enabled: Boolean, onChange: (SessionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(sessionLabel(type))) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            SessionType.entries.forEach { option -> DropdownMenuItem(text = { Text(stringResource(sessionLabel(option))) }, onClick = { expanded = false; onChange(option) }) }
        }
    }
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