package it.finardi.schiena.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class PainCheckWindowTest {
    @Test fun expirationIsExactly48HoursAcrossDaylightSavingChange() {
        val zone = ZoneId.of("Europe/Rome")
        val date = LocalDate.of(2026, 10, 23)
        val time = LocalTime.of(9, 0)
        val due = PainCheckWindow.due(date, time, zone)
        val expires = PainCheckWindow.expires(date, time, zone)
        assertEquals(48L, Duration.between(due, expires).toHours())
        assertFalse(PainCheckWindow.pending(date, time, zone, due.minusNanos(1)))
        assertTrue(PainCheckWindow.pending(date, time, zone, due))
        assertTrue(PainCheckWindow.pending(date, time, zone, expires.minusNanos(1)))
        assertFalse(PainCheckWindow.pending(date, time, zone, expires))
    }
}