package it.finardi.schiena.notif

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DebugAlarm(val kind: String, val trigger: String)
data class DebugSnapshot(val alarms: List<DebugAlarm>, val events: List<String>, val lastCrash: String?)

@Serializable
internal data class AlarmRecord(
    val key: String,
    val kind: String,
    val triggerMillis: Long,
    val token: String,
    val occurrence: String,
    val date: String = "",
    val type: String = "",
    val sessionId: Long = 0,
    val expiresMillis: Long,
    val signature: String = "",
)

@Serializable
internal data class NotificationState(
    val alarms: List<AlarmRecord> = emptyList(),
    val active: List<AlarmRecord> = emptyList(),
    val delivered: List<String> = emptyList(),
    val consumed: List<String> = emptyList(),
    val snoozes: Map<String, Long> = emptyMap(),
    val copyIndices: Map<String, Int> = emptyMap(),
    val events: List<String> = emptyList(),
)

@Singleton
class DebugStore @Inject constructor(
    @ApplicationContext context: Context,
    private val clock: Clock,
) {
    private val preferences = context.getSharedPreferences("notification_backend", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var crashHandlerInstalled = false

    @Synchronized
    internal fun state(): NotificationState = preferences.getString("state", null)?.let {
        runCatching { json.decodeFromString<NotificationState>(it) }.getOrNull()
    } ?: NotificationState()

    @Synchronized
    internal fun update(transform: (NotificationState) -> NotificationState) {
        check(preferences.edit().putString("state", json.encodeToString(transform(state()))).commit()) {
            "Notification state could not be persisted"
        }
    }

    fun snapshot(): DebugSnapshot = synchronized(this) {
        val state = state()
        DebugSnapshot(
            alarms = state.alarms.sortedBy { it.triggerMillis }.map {
                DebugAlarm(it.kind, Instant.ofEpochMilli(it.triggerMillis).toString())
            },
            events = state.events.toList(),
            lastCrash = preferences.getString("last_crash", null),
        )
    }

    fun event(message: String) {
        update { it.copy(events = (it.events + "${clock.instant()} $message").takeLast(50)) }
    }

    @Synchronized
    internal fun nextCopy(category: String, size: Int): Int {
        require(size > 0)
        val next = ((state().copyIndices[category] ?: -1) + 1) % size
        update { it.copy(copyIndices = it.copyIndices + (category to next)) }
        return next
    }

    @Synchronized
    fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val delegate = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                preferences.edit().putString("last_crash", "${clock.instant()} ${thread.name}\n${error.stackTraceToString()}").commit()
            }
            delegate?.uncaughtException(thread, error)
        }
    }
}