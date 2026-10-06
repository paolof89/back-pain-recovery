package it.finardi.schiena.notif

import java.time.Instant

object NotificationRules {
    internal fun awaitingDelivery(alarm: AlarmRecord, now: Instant, delivered: List<String>): Boolean =
        alarm.triggerMillis <= now.toEpochMilli() && alarm.expiresMillis > now.toEpochMilli() &&
            alarm.occurrence !in delivered

    fun actionAllowed(kind: String, command: String, trigger: Instant, expires: Instant, now: Instant, consumed: Boolean): Boolean {
        if (consumed || now < trigger || now >= expires) return false
        return command in when (kind) {
            "office" -> setOf("office_done", "office_snooze", "office_skip")
            "session" -> setOf("session_snooze")
            "recall" -> setOf("session_skip")
            "pain" -> setOf("pain_ok")
            else -> emptySet()
        }
    }
}