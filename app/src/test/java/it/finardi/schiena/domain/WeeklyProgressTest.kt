package it.finardi.schiena.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyProgressTest {
    private val monday = LocalDate.of(2026, 10, 5)

    @Test
    fun emptyWeekHasNoCompletedDays() {
        assertEquals(0, weeklyCompleted(emptyList(), monday))
    }

    @Test
    fun doneAndMinimalCountButSkippedDoesNot() {
        val entries = listOf(
            ProgressEntry(monday, SessionOutcome.DONE),
            ProgressEntry(monday.plusDays(1), SessionOutcome.MINIMAL),
            ProgressEntry(monday.plusDays(2), SessionOutcome.SKIPPED),
        )
        assertEquals(2, weeklyCompleted(entries, monday.plusDays(2)))
    }

    @Test
    fun multipleLogsOnOneDateCountOnceAndSkippedDoesNotCancelCompletion() {
        val entries = listOf(
            ProgressEntry(monday, SessionOutcome.MINIMAL),
            ProgressEntry(monday, SessionOutcome.DONE),
            ProgressEntry(monday, SessionOutcome.DONE),
            ProgressEntry(monday, SessionOutcome.SKIPPED),
        )
        assertEquals(1, weeklyCompleted(entries, monday))
    }

    @Test
    fun minimalCountsOnEachDistinctDate() {
        val entries = (0L..6L).flatMap { offset ->
            List(2) { ProgressEntry(monday.plusDays(offset), SessionOutcome.MINIMAL) }
        }
        assertEquals(7, weeklyCompleted(entries, monday.plusDays(6)))
    }

    @Test
    fun mondayResetsWeekAndSundayStillBelongsToPreviousWeek() {
        val entries = listOf(
            ProgressEntry(monday.minusDays(1), SessionOutcome.DONE),
            ProgressEntry(monday, SessionOutcome.MINIMAL),
            ProgressEntry(monday.plusDays(6), SessionOutcome.DONE),
            ProgressEntry(monday.plusDays(7), SessionOutcome.DONE),
        )
        assertEquals(1, weeklyCompleted(entries, monday.minusDays(1)))
        assertEquals(1, weeklyCompleted(entries, monday))
        assertEquals(2, weeklyCompleted(entries, monday.plusDays(6)))
        assertEquals(1, weeklyCompleted(entries, monday.plusDays(7)))
    }

    @Test
    fun isoWeekCrossesYearBoundaryWithoutResettingOnJanuaryFirst() {
        val start = LocalDate.of(2025, 12, 29)
        val entries = (-1L..7L).map { ProgressEntry(start.plusDays(it), SessionOutcome.DONE) }
        assertEquals(4, weeklyCompleted(entries, LocalDate.of(2026, 1, 1)))
        assertEquals(7, weeklyCompleted(entries, LocalDate.of(2026, 1, 4)))
        assertEquals(1, weeklyCompleted(entries, LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun futureDatesAndPastWeeksAreExcludedEvenForUnsortedInput() {
        val entries = listOf(
            ProgressEntry(monday.plusWeeks(1), SessionOutcome.DONE),
            ProgressEntry(monday.plusDays(3), SessionOutcome.MINIMAL),
            ProgressEntry(monday.minusWeeks(1), SessionOutcome.DONE),
            ProgressEntry(monday.plusDays(2), SessionOutcome.DONE),
            ProgressEntry(monday, SessionOutcome.MINIMAL),
        )
        assertEquals(2, weeklyCompleted(entries, monday.plusDays(2)))
    }
}