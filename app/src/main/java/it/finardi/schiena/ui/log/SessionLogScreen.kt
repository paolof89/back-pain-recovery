package it.finardi.schiena.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.SessionUiState
import it.finardi.schiena.ui.home.sessionLabel
import kotlin.math.roundToInt

@Composable
fun SessionLogScreen(
    state: SessionUiState,
    initialOutcome: SessionOutcome,
    onSave: (SessionOutcome, Int?, Boolean, Int?, String) -> Unit,
    onBack: () -> Unit,
    onRedFlags: () -> Unit,
    onMinimalPlayer: () -> Unit,
) {
    val context = state.context ?: return
    var outcomeName by rememberSaveable { mutableStateOf(initialOutcome.name) }
    val outcome = SessionOutcome.valueOf(outcomeName)
    var pain by rememberSaveable { mutableFloatStateOf(state.previous?.painDuring?.toFloat() ?: 0f) }
    var radiating by rememberSaveable { mutableStateOf(state.previous?.radiating ?: false) }
    var duration by rememberSaveable { mutableStateOf(state.previous?.durationMin?.toString() ?: "") }
    var note by rememberSaveable { mutableStateOf(state.previous?.note ?: "") }
    val skipped = outcome == SessionOutcome.SKIPPED
    val needsDuration = outcome == SessionOutcome.DONE && context.type !in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)
    val durationValue = duration.toIntOrNull()
    val validDuration = !needsDuration || durationValue != null && durationValue in 1..1440
    BackHandler(enabled = state.saving) {}
    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !state.saving) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back))
                }
                Text(stringResource(R.string.log_title), style = MaterialTheme.typography.headlineSmall)
            }
            Text(stringResource(sessionLabel(context.type)), style = MaterialTheme.typography.titleLarge)
            Text(context.date.toString())
            if (state.previous != null) Text(stringResource(R.string.log_replace))
            SessionOutcome.entries.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = outcome == option, enabled = !state.saving, role = Role.RadioButton,
                        onClick = { outcomeName = option.name },
                    ),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    RadioButton(selected = outcome == option, onClick = null, enabled = !state.saving)
                    Text(stringResource(when (option) {
                        SessionOutcome.DONE -> R.string.log_done
                        SessionOutcome.MINIMAL -> R.string.log_minimal
                        SessionOutcome.SKIPPED -> R.string.log_skipped
                    }), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (!skipped) {
                Text(stringResource(R.string.log_pain, pain.roundToInt()), style = MaterialTheme.typography.titleMedium)
                Slider(value = pain, onValueChange = { pain = it }, valueRange = 0f..10f, steps = 9, enabled = !state.saving)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.log_radiating), modifier = Modifier.weight(1f))
                    Switch(checked = radiating, enabled = !state.saving, onCheckedChange = {
                        radiating = it
                        if (it) onRedFlags()
                    })
                }
                if (radiating || pain > 5) Text(stringResource(R.string.log_red_warning), color = MaterialTheme.colorScheme.error)
                if (needsDuration) {
                    OutlinedTextField(
                        value = duration, onValueChange = { duration = it },
                        label = { Text(stringResource(R.string.log_duration)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                        isError = duration.isNotEmpty() && !validDuration,
                        supportingText = { Text(stringResource(R.string.log_duration_range)) },
                    )
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.log_note)) },
                modifier = Modifier.fillMaxWidth(), enabled = !state.saving, minLines = 2, maxLines = 5)
            if (state.saveError) Text(stringResource(R.string.log_save_error), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { onSave(outcome, if (skipped) null else pain.roundToInt(), !skipped && radiating,
                    if (needsDuration) durationValue else null, note) },
                enabled = !state.saving && !state.saved && validDuration,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (state.saving) R.string.log_saving else R.string.log_save)) }
            TextButton(onClick = onMinimalPlayer, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.log_start_minimal))
            }
        }
    }
}