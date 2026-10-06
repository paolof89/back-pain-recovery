package it.finardi.schiena.notif

import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.PainCheckWindow
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

object AlarmTriggers {
    fun nextOffice(clock: Clock, settings: Settings): Instant? {
        if (settings.workDays.isEmpty() || settings.workStart >= settings.workEnd || settings.breakIntervalMin <= 0) return null
        val now = clock.instant()
        val today = LocalDate.now(clock)
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in settings.workDays || settings.isWorkOffToday(date)) continue
            var slot = settings.workStart.plusMinutes(settings.breakIntervalMin.toLong())
            while (slot > settings.workStart && slot < settings.workEnd) {
                val trigger = date.atTime(slot).atZone(clock.zone).toInstant()
                if (trigger > now) return trigger
                val next = slot.plusMinutes(settings.breakIntervalMin.toLong())
                if (next <= slot) break
                slot = next
            }
        }
        return null
    }

    fun nextSession(clock: Clock, entry: WeekPlanEntry, logged: Set<LocalDate>): Instant {
        val today = LocalDate.now(clock)
        for (offset in 0L..14L) {
            val date = today.plusDays(offset)
            val trigger = date.atTime(entry.reminderTime).atZone(clock.zone).toInstant()
            if (date.dayOfWeek == entry.dayOfWeek && date !in logged && trigger > clock.instant()) return trigger
        }
        error("No future session slot")
    }

    fun recall(clock: Clock, entry: WeekPlanEntry, logged: Boolean, alreadySent: Boolean): Instant? {
        val date = LocalDate.now(clock)
        if (date.dayOfWeek != entry.dayOfWeek || logged || alreadySent) return null
        val trigger = date.atTime(entry.reminderTime).atZone(clock.zone).toInstant().plus(Duration.ofHours(3))
        return trigger.takeIf { it.atZone(clock.zone).toLocalDate() == date && it > clock.instant() }
    }

    fun officeSnooze(clock: Clock, settings: Settings): Instant? {
        val trigger = clock.instant().plus(Duration.ofMinutes(15))
        val local = trigger.atZone(clock.zone)
        return trigger.takeIf {
            local.toLocalDate() == LocalDate.now(clock) && local.dayOfWeek in settings.workDays &&
                !settings.isWorkOffToday(local.toLocalDate()) && local.toLocalTime() >= settings.workStart &&
                local.toLocalTime() < settings.workEnd
        }
    }

    fun sessionSnooze(clock: Clock, date: LocalDate): Instant? =
        clock.instant().plus(Duration.ofHours(1)).takeIf {
            date == LocalDate.now(clock) && it.atZone(clock.zone).toLocalDate() == date
        }

    fun painTrigger(clock: Clock, date: LocalDate, time: LocalTime, alreadyDelivered: Boolean, scheduled: Instant? = null): Instant? {
        val due = PainCheckWindow.due(date, time, clock.zone)
        val expires = PainCheckWindow.expires(date, time, clock.zone)
        if (alreadyDelivered || clock.instant() >= expires) return null
        return if (due > clock.instant()) due else scheduled ?: clock.instant().plusSeconds(1).takeIf { it < expires }
    }

    fun nextWeekly(clock: Clock): Instant {
        val today = LocalDate.now(clock)
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            val trigger = date.atTime(LocalTime.of(19, 0)).atZone(clock.zone).toInstant()
            if (date.dayOfWeek == DayOfWeek.SUNDAY && trigger > clock.instant()) return trigger
        }
        error("No weekly slot")
    }
}