package it.finardi.schiena.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object PainCheckWindow {
    fun required(type: SessionType, outcome: SessionOutcome): Boolean =
        outcome != SessionOutcome.SKIPPED && type in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B, SessionType.PILATES)

    fun due(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.plusDays(1).atTime(time).atZone(zone).toInstant()

    fun expires(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        due(date, time, zone).plusSeconds(48 * 60 * 60)

    fun pending(date: LocalDate, time: LocalTime, zone: ZoneId, now: Instant): Boolean =
        now >= due(date, time, zone) && now < expires(date, time, zone)
}