package it.finardi.schiena.ui.home

import it.finardi.schiena.data.OfficeBreakEvent
import it.finardi.schiena.data.PainCheck
import it.finardi.schiena.data.Phase
import it.finardi.schiena.data.ProgramSeed
import it.finardi.schiena.data.ProgramState
import it.finardi.schiena.data.SessionLog
import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-07T07:00:00Z"), ZoneId.of("Europe/Rome"))
    private val today = LocalDate.now(clock)
    private val phase = Phase(12, "Database phase", 6)
    private val program = ProgramState(phase.id, today.minusDays(15), 5)
    private val plan = DayOfWeek.entries.map { day ->
        WeekPlanEntry(day, SessionType.FREE, LocalTime.of(14, day.value))
    }
    private val settings = Settings(
        checkTime = LocalTime.of(9, 0),
        workDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        workStart = LocalTime.of(9, 0), workEnd = LocalTime.of(18, 30), breakIntervalMin = 75,
    )

    @Test
    fun customCurrentPhaseAndTodaysPlanAreUsedWithoutDefaults() {
        val entry = WeekPlanEntry(today.dayOfWeek, SessionType.STRENGTH_B, LocalTime.of(21, 17))
        val snapshot = snapshot(
            plan = plan.filterNot { it.dayOfWeek == today.dayOfWeek } + entry,
            phases = listOf(Phase(1, "Other phase", 4), phase),
        )

        assertFalse(snapshot.loading)
        assertFalse(snapshot.error)
        assertEquals(phase, snapshot.phase)
        assertEquals(entry, snapshot.entry)
        assertEquals(program.weeklyTarget, snapshot.target)
    }

    @Test
    fun seedPlanSelectsTheEntryForEveryLocalDay() {
        val seedFile = listOf(
            File("src/main/assets/seed/program_seed.json"),
            File("app/src/main/assets/seed/program_seed.json"),
        ).first { it.isFile }
        val seed = Json.decodeFromString<ProgramSeed>(seedFile.readText(Charsets.UTF_8))
        val seedPlan = seed.weekTemplate.map {
            WeekPlanEntry(DayOfWeek.valueOf(it.day), it.sessionType, LocalTime.parse(it.reminderTime))
        }
        val seedPhases = seed.phases.map { Phase(it.id, it.name, it.minWeeks, it.description) }
        val state = ProgramState(seedPhases.last().id, today, seed.defaults.weeklyTarget)

        seedPlan.forEach { expected ->
            val date = today.with(expected.dayOfWeek)
            val snapshot = snapshot(today = date, plan = seedPlan.reversed(), program = state, phases = seedPhases)
            assertFalse(snapshot.error)
            assertEquals(expected, snapshot.entry)
            assertEquals(seedPhases.last(), snapshot.phase)
        }
    }

    @Test
    fun dateAndPhaseWeekUseWholeSevenDayPeriodsAndClampFutureStarts() {
        listOf(0L to 1, 6L to 1, 7L to 2, 13L to 2, 14L to 3, -7L to 1).forEach { (days, week) ->
            val snapshot = snapshot(program = program.copy(phaseStartDate = today.minusDays(days)))
            assertEquals(today, snapshot.today)
            assertEquals("Days in phase: $days", week, snapshot.phaseWeek)
        }
    }

    @Test
    fun weeklyMinimalAndDoneCountOncePerDayAndSkippedDoesNotCount() {
        val monday = today.with(DayOfWeek.MONDAY)
        val snapshot = snapshot(logs = listOf(
            log(1, monday, outcome = SessionOutcome.MINIMAL),
            log(2, monday, outcome = SessionOutcome.DONE),
            log(3, monday, outcome = SessionOutcome.SKIPPED),
            log(4, monday.plusDays(1), outcome = SessionOutcome.SKIPPED),
            log(5, today, outcome = SessionOutcome.MINIMAL),
            log(6, today, outcome = SessionOutcome.MINIMAL),
            log(7, monday.minusDays(1)),
            log(8, today.plusDays(1)),
        ))

        assertEquals(2, snapshot.completed)
    }

    @Test
    fun mondayResetsTheWeeklyCount() {
        val monday = today.with(DayOfWeek.MONDAY)
        val logs = listOf(log(1, monday.minusDays(1)), log(2, monday, outcome = SessionOutcome.MINIMAL))

        assertEquals(1, snapshot(today = monday.minusDays(1), logs = logs).completed)
        assertEquals(1, snapshot(today = monday, logs = logs).completed)
    }

    @Test
    fun officeDoneUsesClockZoneAndExcludesOtherActionsAndAdjacentDates() {
        val snapshot = snapshot(breaks = listOf(
            OfficeBreakEvent(Instant.parse("2026-10-06T22:15:00Z"), OfficeBreakAction.DONE),
            OfficeBreakEvent(Instant.parse("2026-10-07T21:59:59Z"), OfficeBreakAction.DONE),
            OfficeBreakEvent(Instant.parse("2026-10-06T21:59:59Z"), OfficeBreakAction.DONE),
            OfficeBreakEvent(Instant.parse("2026-10-07T22:00:00Z"), OfficeBreakAction.DONE),
            OfficeBreakEvent(clock.instant(), OfficeBreakAction.SNOOZED),
            OfficeBreakEvent(clock.instant().plusSeconds(1), OfficeBreakAction.SKIPPED),
        ))

        assertEquals(2, snapshot.breaksDone)
        assertEquals(7, snapshot.breaksTarget)
    }

    @Test
    fun workOffForTodaySuppressesTargetButRetainsDoneBreaks() {
        val snapshot = snapshot(
            settings = settings.copy(workOffToday = today),
            breaks = listOf(OfficeBreakEvent(clock.instant(), OfficeBreakAction.DONE)),
        )

        assertTrue(snapshot.workOff)
        assertEquals(0, snapshot.breaksTarget)
        assertEquals(1, snapshot.breaksDone)
    }

    @Test
    fun workOffForAnotherDateDoesNotSuppressTodaysTarget() {
        listOf(today.minusDays(1), today.plusDays(1)).forEach { offDate ->
            val snapshot = snapshot(settings = settings.copy(workOffToday = offDate))
            assertFalse(snapshot.workOff)
            assertEquals(7, snapshot.breaksTarget)
        }
    }

    @Test
    fun customWorkdaysAndIntervalControlTarget() {
        assertEquals(0, snapshot(settings = settings.copy(workDays = setOf(DayOfWeek.SUNDAY))).breaksTarget)
        assertEquals(3, snapshot(settings = settings.copy(
            workStart = LocalTime.of(10, 0), workEnd = LocalTime.of(14, 59), breakIntervalMin = 90,
        )).breaksTarget)
    }

    @Test
    fun pendingChecksIncludeOnlyStrengthAndPilatesDoneOrMinimal() {
        var nextId = 0L
        val logs = SessionType.entries.flatMap { type ->
            SessionOutcome.entries.map { outcome -> log(++nextId, today.minusDays(1), type, outcome) }
        }

        assertEquals(6, snapshot(logs = logs).pendingChecks)
    }

    @Test
    fun completedChecksAreExcludedRegardlessOfTheirPainOrBaselineAnswer() {
        val logs = listOf(log(1, today.minusDays(1)), log(2, today.minusDays(1)), log(3, today.minusDays(1)))
        val checks = listOf(
            PainCheck(1, clock.instant(), 0, true),
            PainCheck(2, clock.instant(), 8, false),
        )

        assertEquals(1, snapshot(logs = logs, checks = checks).pendingChecks)
    }

    @Test
    fun pendingCheckStartsExactlyAtConfiguredLocalCheckTime() {
        val logs = listOf(log(1, today.minusDays(1)))

        assertEquals(0, snapshot(logs = logs, clock = Clock.offset(clock, java.time.Duration.ofSeconds(-1))).pendingChecks)
        assertEquals(1, snapshot(logs = logs).pendingChecks)
    }

    @Test
    fun pendingCheckExpiresExactlyFortyEightHoursAfterDueTime() {
        val logs = listOf(log(1, today.minusDays(3)))

        assertEquals(1, snapshot(logs = logs, clock = Clock.offset(clock, java.time.Duration.ofSeconds(-1))).pendingChecks)
        assertEquals(0, snapshot(logs = logs).pendingChecks)
        assertEquals(0, snapshot(logs = listOf(log(2, today.minusDays(4)))).pendingChecks)
    }

    @Test
    fun missingProgramPhaseTodayEntryOrSettingsIsAnErrorWithoutInventedData() {
        val missingProgram = snapshot(program = null)
        val missingPhase = snapshot(phases = listOf(Phase(1, "Other phase", 4)))
        val missingEntry = snapshot(plan = plan.filterNot { it.dayOfWeek == today.dayOfWeek })
        val missingSettings = snapshot(settings = null, logs = listOf(log(1, today.minusDays(1))))

        listOf(missingProgram, missingPhase, missingEntry, missingSettings).forEach {
            assertTrue(it.error)
            assertFalse(it.loading)
        }
        assertNull(missingProgram.phase)
        assertEquals(0, missingProgram.target)
        assertNull(missingPhase.phase)
        assertNull(missingEntry.entry)
        assertEquals(0, missingSettings.breaksTarget)
        assertEquals(0, missingSettings.pendingChecks)
    }

    @Test
    fun futureLogsAreAbsentFromHistoryWeeklyCountAndPendingChecks() {
        val snapshot = snapshot(logs = listOf(
            log(9, today.plusDays(1)), log(1, today.minusDays(1)),
            log(2, today), log(10, today.plusWeeks(1)), log(3, today),
        ))

        assertEquals(listOf(3L, 2L, 1L), snapshot.history.map { it.id })
        assertEquals(2, snapshot.completed)
        assertEquals(1, snapshot.pendingChecks)
    }

    private fun log(
        id: Long, date: LocalDate, type: SessionType = SessionType.STRENGTH_A,
        outcome: SessionOutcome = SessionOutcome.DONE,
    ) = SessionLog(id = id, date = date, sessionType = type, phaseId = phase.id, outcome = outcome)

    private fun snapshot(
        today: LocalDate = this.today, plan: List<WeekPlanEntry> = this.plan,
        program: ProgramState? = this.program, phases: List<Phase> = listOf(phase),
        logs: List<SessionLog> = emptyList(), checks: List<PainCheck> = emptyList(),
        breaks: List<OfficeBreakEvent> = emptyList(), settings: Settings? = this.settings, clock: Clock = this.clock,
    ) = homeSnapshot(today, plan, program, phases, logs, checks, breaks, settings, clock)
}