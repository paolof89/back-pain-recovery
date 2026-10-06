package it.finardi.schiena.notif

import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionType
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTriggersTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun clock(local: String): Clock = Clock.fixed(java.time.LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
    private fun settings() = Settings(LocalTime.of(9, 0), DayOfWeek.entries.take(5).toSet(), LocalTime.of(9, 0), LocalTime.of(18, 30), 75, true)
    private fun local(trigger: Instant?) = trigger?.atZone(zone)?.toLocalDateTime()?.toString()

    @Test fun firstOfficeBreakIsAfterOneInterval() {
        assertEquals("2026-10-05T10:15", local(AlarmTriggers.nextOffice(clock("2026-10-05T08:00"), settings())))
    }

    @Test fun exactSlotIsNotReplayed() {
        assertEquals("2026-10-05T11:30", local(AlarmTriggers.nextOffice(clock("2026-10-05T10:15"), settings())))
    }

    @Test fun windowEndIsExclusiveAndWeekendIsSkipped() {
        assertEquals("2026-10-12T10:15", local(AlarmTriggers.nextOffice(clock("2026-10-09T18:30"), settings())))
    }

    @Test fun workOffOnlySkipsItsOwnDate() {
        val settings = settings().copy(workOffToday = LocalDate.parse("2026-10-05"))
        assertEquals("2026-10-06T10:15", local(AlarmTriggers.nextOffice(clock("2026-10-05T08:00"), settings)))
        assertEquals("2026-10-12T10:15", local(AlarmTriggers.nextOffice(clock("2026-10-12T08:00"), settings)))
    }

    @Test fun customWorkDaysAndIntervalAreRespected() {
        val settings = settings().copy(workDays = setOf(DayOfWeek.SUNDAY), breakIntervalMin = 60)
        assertEquals("2026-10-11T10:00", local(AlarmTriggers.nextOffice(clock("2026-10-05T08:00"), settings)))
    }

    @Test fun emptyOrTooShortWindowHasNoOfficeSlot() {
        assertNull(AlarmTriggers.nextOffice(clock("2026-10-05T08:00"), settings().copy(workDays = emptySet())))
        assertNull(AlarmTriggers.nextOffice(clock("2026-10-05T08:00"), settings().copy(workEnd = LocalTime.of(10, 0))))
    }

    @Test fun zoneIsTakenFromClockNotDevice() {
        val tokyo = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneId.of("Asia/Tokyo"))
        assertEquals(Instant.parse("2026-10-05T01:15:00Z"), AlarmTriggers.nextOffice(tokyo, settings()))
    }

    @Test fun weekendDstChangeKeepsMondayWallTime() {
        assertEquals(Instant.parse("2026-10-26T09:15:00Z"), AlarmTriggers.nextOffice(clock("2026-10-23T19:00"), settings()))
    }

    @Test fun sessionSkipsLoggedOccurrence() {
        val entry = WeekPlanEntry(DayOfWeek.MONDAY, SessionType.STRENGTH_A, LocalTime.of(7, 30))
        assertEquals("2026-10-12T07:30", local(AlarmTriggers.nextSession(clock("2026-10-05T06:00"), entry, setOf(LocalDate.parse("2026-10-05")))))
    }

    @Test fun recallIsThreeHoursLaterAndSuppressedAfterLogOrDelivery() {
        val entry = WeekPlanEntry(DayOfWeek.MONDAY, SessionType.STRENGTH_A, LocalTime.of(7, 30))
        assertEquals("2026-10-05T10:30", local(AlarmTriggers.recall(clock("2026-10-05T08:00"), entry, false, false)))
        assertNull(AlarmTriggers.recall(clock("2026-10-05T08:00"), entry, true, false))
        assertNull(AlarmTriggers.recall(clock("2026-10-05T08:00"), entry, false, true))
        assertNull(AlarmTriggers.recall(clock("2026-10-05T10:30"), entry, false, false))
    }

    @Test fun recallDoesNotSpillIntoNextDay() {
        val entry = WeekPlanEntry(DayOfWeek.MONDAY, SessionType.PILATES, LocalTime.of(22, 0))
        assertNull(AlarmTriggers.recall(clock("2026-10-05T20:00"), entry, false, false))
    }

    @Test fun officeSnoozeCannotEscapeWorkWindowOrWorkOff() {
        assertEquals("2026-10-05T10:30", local(AlarmTriggers.officeSnooze(clock("2026-10-05T10:15"), settings())))
        assertNull(AlarmTriggers.officeSnooze(clock("2026-10-05T18:15"), settings()))
        assertNull(AlarmTriggers.officeSnooze(clock("2026-10-05T10:15"), settings().copy(workOffToday = LocalDate.parse("2026-10-05"))))
    }

    @Test fun weeklyAdvancesAtSundayBoundary() {
        assertEquals("2026-10-11T19:00", local(AlarmTriggers.nextWeekly(clock("2026-10-11T18:59"))))
        assertEquals("2026-10-18T19:00", local(AlarmTriggers.nextWeekly(clock("2026-10-11T19:00"))))
    }

    @Test fun sessionSnoozeIsOneHourAndCannotReachAnotherDate() {
        val date = LocalDate.parse("2026-10-05")
        assertEquals("2026-10-05T11:15", local(AlarmTriggers.sessionSnooze(clock("2026-10-05T10:15"), date)))
        assertNull(AlarmTriggers.sessionSnooze(clock("2026-10-05T23:15"), date))
        assertNull(AlarmTriggers.sessionSnooze(clock("2026-10-06T10:15"), date))
    }

    @Test fun painAlarmSchedulesDueThenCatchupButNeverAfterExpiry() {
        val date = LocalDate.parse("2026-10-05")
        val time = LocalTime.of(9, 0)
        assertEquals("2026-10-06T09:00", local(AlarmTriggers.painTrigger(clock("2026-10-05T20:00"), date, time, false)))
        assertEquals("2026-10-06T10:00:01", local(AlarmTriggers.painTrigger(clock("2026-10-06T10:00"), date, time, false)))
        assertNull(AlarmTriggers.painTrigger(clock("2026-10-08T09:00"), date, time, false))
        assertNull(AlarmTriggers.painTrigger(clock("2026-10-06T10:00"), date, time, true))
    }

    @Test fun painCatchupKeepsExistingIdentityAndExpiresAtFortyEightElapsedHoursAcrossDst() {
        val date = LocalDate.parse("2026-10-23")
        val time = LocalTime.of(9, 0)
        val scheduled = Instant.parse("2026-10-24T08:00:01Z")
        assertEquals(scheduled, AlarmTriggers.painTrigger(clock("2026-10-24T10:05"), date, time, false, scheduled))
        assertNull(AlarmTriggers.painTrigger(clock("2026-10-26T08:00"), date, time, false))
    }
}