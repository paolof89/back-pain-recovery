package it.finardi.schiena.notif

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRulesTest {
    private val trigger = Instant.parse("2026-10-05T09:00:00Z")
    private val expires = trigger.plusSeconds(3600)

    @Test fun coldProcessReschedulePreservesDueUndeliveredTokenUntilExpiry() {
        val alarm = AlarmRecord("office", "office", trigger.toEpochMilli(), "token", "occurrence",
            expiresMillis = expires.toEpochMilli())
        assertFalse(NotificationRules.awaitingDelivery(alarm, trigger.minusSeconds(1), emptyList()))
        assertTrue(NotificationRules.awaitingDelivery(alarm, trigger, emptyList()))
        assertTrue(NotificationRules.awaitingDelivery(alarm, expires.minusSeconds(1), emptyList()))
        assertFalse(NotificationRules.awaitingDelivery(alarm, expires, emptyList()))
        assertFalse(NotificationRules.awaitingDelivery(alarm, trigger, listOf("occurrence")))
    }

    @Test fun validOfficeCommandsAreAccepted() {
        listOf("office_done", "office_skip", "office_snooze").forEach {
            assertTrue(NotificationRules.actionAllowed("office", it, trigger, expires, trigger, false))
        }
    }

    @Test fun consumedExpiredAndEarlyActionsAreRejected() {
        assertFalse(NotificationRules.actionAllowed("office", "office_done", trigger, expires, trigger, true))
        assertFalse(NotificationRules.actionAllowed("office", "office_done", trigger, expires, expires, false))
        assertFalse(NotificationRules.actionAllowed("office", "office_done", trigger, expires, trigger.minusSeconds(1), false))
    }

    @Test fun commandCannotCrossNotificationKind() {
        assertFalse(NotificationRules.actionAllowed("pain", "session_skip", trigger, expires, trigger, false))
        assertFalse(NotificationRules.actionAllowed("session", "office_done", trigger, expires, trigger, false))
        assertTrue(NotificationRules.actionAllowed("pain", "pain_ok", trigger, expires, trigger, false))
        assertTrue(NotificationRules.actionAllowed("recall", "session_skip", trigger, expires, trigger, false))
        assertTrue(NotificationRules.actionAllowed("session", "session_snooze", trigger, expires, trigger, false))
    }
}