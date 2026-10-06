package it.finardi.schiena.domain

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

data class ProgressEntry(val date: LocalDate, val outcome: SessionOutcome)

fun weeklyCompleted(entries: List<ProgressEntry>, today: LocalDate): Int {
    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return entries.filter {
        it.date >= start && it.date <= today && it.outcome != SessionOutcome.SKIPPED
    }.map { it.date }.distinct().size
}