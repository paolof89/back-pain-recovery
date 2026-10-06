package it.finardi.schiena.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R

@Composable
fun DebugScreen(state: DebugUiState, onRefresh: () -> Unit, onTest: () -> Unit, onBack: () -> Unit) {
    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back)) }
                    IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, stringResource(R.string.s3_refresh)) }
                }
                Text(stringResource(R.string.s3_debug), style = MaterialTheme.typography.headlineSmall)
                if (state.loading) CircularProgressIndicator()
                if (state.error) Text(stringResource(R.string.s3_debug_error), color = MaterialTheme.colorScheme.error)
                if (state.sent) Text(stringResource(R.string.s3_test_sent))
                Button(onClick = onTest, enabled = !state.loading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.s3_test_notification)) }
                Text(stringResource(R.string.s3_alarms), style = MaterialTheme.typography.titleMedium)
                if (state.alarms.isEmpty()) Text(stringResource(R.string.s3_none))
            }
            items(state.alarms) { (kind, trigger) -> Text("$kind\n$trigger") }
            item {
                Text(stringResource(R.string.s3_events), style = MaterialTheme.typography.titleMedium)
                if (state.events.isEmpty()) Text(stringResource(R.string.s3_none))
            }
            items(state.events) { event -> SelectionContainer { Text(event) } }
            item {
                Text(stringResource(R.string.s3_crash), style = MaterialTheme.typography.titleMedium)
                SelectionContainer { Text(state.lastCrash ?: stringResource(R.string.s3_none)) }
            }
        }
    }
}