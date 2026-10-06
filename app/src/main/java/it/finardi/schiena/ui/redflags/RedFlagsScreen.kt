package it.finardi.schiena.ui.redflags

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R

@Composable
fun RedFlagsScreen(onBack: () -> Unit) {
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back)) }
            Text(stringResource(R.string.red_flags_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.red_flags_medical), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.red_flags_emergency_title), style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.red_flags_emergency), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.red_flags_disclaimer))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.red_flags_return)) }
        }
    }
}