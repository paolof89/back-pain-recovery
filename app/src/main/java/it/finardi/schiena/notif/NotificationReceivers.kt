package it.finardi.schiena.notif

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun BroadcastReceiver.bounded(debug: DebugStore, name: String, action: suspend () -> Unit) {
    val pending = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
        try {
            withTimeout(8_500) {
                debug.event("receiver $name")
                action()
            }
        } catch (error: CancellationException) {
            runCatching { debug.event("receiver timed out $name") }
            throw error
        } catch (error: Exception) {
            runCatching { debug.event("receiver failed $name: ${error.javaClass.simpleName}: ${error.message}") }
        } finally {
            pending.finish()
            scope.cancel()
        }
    }
}

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var debug: DebugStore
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        bounded(debug, "alarm") { scheduler.deliver(token) }
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var debug: DebugStore
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra("token") ?: return
        val command = intent.getStringExtra("command") ?: return
        bounded(debug, command) { scheduler.handleAction(token, command) }
    }
}

@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var debug: DebugStore
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        bounded(debug, intent.action.orEmpty()) {
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) scheduler.updateTimeZone()
            scheduler.reschedule()
        }
    }
}