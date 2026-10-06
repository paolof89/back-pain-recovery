package it.finardi.schiena.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.finardi.schiena.R

@Composable
fun PermissionControls(notificationsAllowed: Boolean, exactAllowed: Boolean, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    var linkError by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRefresh() }
    fun open(intent: Intent, fallback: Intent = appSettings(context)) {
        linkError = !openSystemSettings(context, intent, fallback)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!notificationsAllowed) {
            Text(stringResource(R.string.s3_notifications_off), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !requested) {
                    requested = true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.s3_enable_notifications))
            }
        }
        if (!exactAllowed) {
            Text(stringResource(R.string.s3_exact_warning))
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.s3_exact)) }
        }
        Text(stringResource(R.string.s3_battery), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.s3_battery_link)) }
        Text(stringResource(R.string.s3_xiaomi))
        if (linkError) Text(stringResource(R.string.s3_link_error), color = MaterialTheme.colorScheme.error)
    }
}

private fun appSettings(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun openSystemSettings(context: Context, intent: Intent, fallback: Intent): Boolean {
    fun attempt(target: Intent): Boolean = try {
        context.startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false }
    catch (_: SecurityException) { false }
    return attempt(intent) || attempt(fallback)
}