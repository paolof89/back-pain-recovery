package it.finardi.schiena.ui.paincheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R
import it.finardi.schiena.ui.home.sessionLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PainCheckScreen(sessionId: Long, state: PainCheckUiState, onSave: (Int, Boolean) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    var pain by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var baseline by rememberSaveable(sessionId) { mutableStateOf<Boolean?>(null) }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onBack, enabled = !state.saving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back)) }
            Text(stringResource(R.string.s3_pain_title), style = MaterialTheme.typography.headlineSmall)
            when {
                state.loading -> { CircularProgressIndicator(); Text(stringResource(R.string.plan_loading)) }
                state.stale -> {
                    Text(stringResource(R.string.s3_check_stale))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.s3_refresh)) }
                }
                state.log == null -> {
                    Text(stringResource(R.string.s3_check_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> {
                    Text(stringResource(R.string.s3_check_context, state.log.date.format(formatter), stringResource(sessionLabel(state.log.sessionType))))
                    Text(stringResource(R.string.s3_pain_now, pain))
                    Slider(pain.toFloat(), onValueChange = { pain = it.roundToInt() }, valueRange = 0f..10f, steps = 9, enabled = !state.saving)
                    Text(stringResource(R.string.s3_baseline))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(true, false).forEachIndexed { index, answer ->
                            SegmentedButton(selected = baseline == answer, onClick = { baseline = answer }, enabled = !state.saving,
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                                Text(stringResource(if (answer) R.string.s3_yes else R.string.s3_no))
                            }
                        }
                    }
                    if (state.error) Text(stringResource(R.string.s3_check_error), color = MaterialTheme.colorScheme.error)
                    if (state.saving) CircularProgressIndicator()
                    Button(onClick = { baseline?.let { onSave(pain, it) } }, enabled = baseline != null && !state.saving && !state.saved,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(if (state.error) R.string.retry else R.string.s3_save)) }
                }
            }
        }
    }
}