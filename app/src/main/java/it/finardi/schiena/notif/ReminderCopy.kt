package it.finardi.schiena.notif

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.finardi.schiena.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderCopy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val debug: DebugStore,
) {
    fun next(kind: String): String {
        val resource = when (kind) {
            "recall" -> R.array.reminder_recall
            "pain" -> R.array.reminder_pain
            "office" -> R.array.reminder_office
            "weekly" -> R.array.reminder_weekly
            else -> R.array.reminder_session
        }
        val pool = context.resources.getStringArray(resource)
        return pool[debug.nextCopy(kind, pool.size)]
    }
}