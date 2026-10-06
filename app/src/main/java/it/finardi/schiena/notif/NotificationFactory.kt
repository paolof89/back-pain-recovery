package it.finardi.schiena.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.finardi.schiena.R
import it.finardi.schiena.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copy: ReminderCopy,
    private val debug: DebugStore,
) {
    internal fun allowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    internal fun channels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            "sessions" to R.string.notif_channel_sessions,
            "pain_check" to R.string.notif_channel_pain,
            "office_breaks" to R.string.notif_channel_office,
            "weekly" to R.string.notif_channel_weekly,
        ).forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    internal fun show(record: AlarmRecord, neutral: Boolean = false): Boolean {
        val channel = channel(record.kind)
        channels()
        if (!allowed() || context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) {
            debug.event("notification blocked ${record.kind}")
            return false
        }
        val title = when (record.kind) {
            "pain" -> R.string.notif_title_pain
            "office" -> R.string.notif_title_office
            "weekly" -> R.string.notif_title_weekly
            else -> R.string.notif_title_session
        }
        val text = if (neutral) context.getString(R.string.notif_pain_neutral) else copy.next(record.kind)
        val open = when (record.kind) {
            "pain" -> "pain"
            "weekly" -> "weekly"
            else -> "start"
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(activity(record, open))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setTimeoutAfter((record.expiresMillis - System.currentTimeMillis()).coerceAtLeast(1))
        when (record.kind) {
            "session" -> {
                builder.addAction(0, context.getString(R.string.notif_start), activity(record, "start"))
                builder.addAction(0, context.getString(R.string.notif_minimal), activity(record, "minimal"))
                builder.addAction(0, context.getString(R.string.notif_snooze_hour), action(record, "session_snooze"))
            }
            "recall" -> {
                builder.addAction(0, context.getString(R.string.notif_minimal), activity(record, "minimal"))
                builder.addAction(0, context.getString(R.string.notif_skip_today), action(record, "session_skip"))
            }
            "pain" -> {
                if (!neutral) builder.addAction(0, context.getString(R.string.notif_all_ok), action(record, "pain_ok"))
                builder.addAction(0, context.getString(R.string.notif_open), activity(record, "pain"))
            }
            "office" -> {
                builder.addAction(0, context.getString(R.string.notif_done), action(record, "office_done"))
                builder.addAction(0, context.getString(R.string.notif_snooze_quarter), action(record, "office_snooze"))
                builder.addAction(0, context.getString(R.string.notif_skip), action(record, "office_skip"))
            }
            "weekly" -> builder.addAction(0, context.getString(R.string.notif_open), activity(record, "weekly"))
        }
        return try {
            NotificationManagerCompat.from(context).notify(record.key, 1, builder.build())
            debug.event("notification sent ${record.kind}")
            true
        } catch (error: SecurityException) {
            debug.event("notification denied ${record.kind}: ${error.javaClass.simpleName}")
            false
        }
    }

    internal fun cancel(record: AlarmRecord) {
        NotificationManagerCompat.from(context).cancel(record.key, 1)
    }

    internal fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    fun sendTest() {
        channels()
        if (!allowed()) {
            debug.event("test notification blocked")
            return
        }
        val intent = Intent(context, MainActivity::class.java).putExtra(NotificationNavigation.ACTION, "start")
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "sessions")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            NotificationManagerCompat.from(context).notify("test", 1, notification)
            debug.event("test notification sent")
        } catch (error: SecurityException) {
            debug.event("test notification denied")
        }
    }

    private fun channel(kind: String): String = when (kind) {
        "pain" -> "pain_check"
        "office" -> "office_breaks"
        "weekly" -> "weekly"
        else -> "sessions"
    }

    private fun activity(record: AlarmRecord, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("schiena://notification/${record.token}/$action"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotificationNavigation.ACTION, action)
            .putExtra(NotificationNavigation.SESSION_ID, record.sessionId)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(record: AlarmRecord, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setData(Uri.parse("schiena://action/${record.token}/$action"))
            .putExtra("token", record.token).putExtra("command", action)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}