package it.finardi.schiena.data

import android.app.Application
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ProgramResetTest : ProgramDataFixture() {
    private data class HistorySnapshot(
        val sessions: List<SessionLog>,
        val checks: List<PainCheck>,
        val breaks: List<OfficeBreakEvent>,
        val transitions: List<PhaseTransition>,
        val state: ProgramState?,
        val settings: Settings?,
    )

    private suspend fun snapshot(): HistorySnapshot {
        val history = database.historyDao()
        return HistorySnapshot(history.getSessions(), history.getPainChecks(), history.getOfficeBreaks(),
            history.getTransitions(), database.programDao().getState(), settingsRepository.settings.first())
    }

    private suspend fun populateHistory() {
        val history = database.historyDao()
        SessionStatus.values().forEachIndexed { index, status ->
            val sessionId = history.insertSession(SessionLog(
                date = LocalDate.of(2026, 10, 1).plusDays(index.toLong()),
                sessionType = SessionType.values()[index],
                phaseId = index % 3 + 1,
                outcome = SessionOutcome.values()[index % 3],
                painDuring = if (index % 3 == 2) null else index,
                radiating = status == SessionStatus.RED,
                durationMin = 20 + index,
                note = "History $index",
                status = status,
            ))
            history.upsertPainCheck(PainCheck(sessionId,
                Instant.parse("2026-10-06T09:00:00.123456789Z").plusSeconds(index.toLong()), index, index % 2 == 0))
        }
        OfficeBreakAction.values().forEachIndexed { index, action ->
            history.upsertOfficeBreak(OfficeBreakEvent(Instant.parse("2026-10-06T10:00:00Z").plusSeconds(index.toLong()), action))
        }
        history.insertTransition(PhaseTransition(date = LocalDate.of(2026, 9, 1), fromPhase = 1, toPhase = 2, reason = "Confirmed"))
        history.insertTransition(PhaseTransition(date = LocalDate.of(2026, 10, 1), fromPhase = 2, toPhase = 3, reason = "Confirmed again"))
        database.programDao().upsertState(ProgramState(3, LocalDate.of(2026, 10, 1), 6, LocalDate.of(2026, 10, 5)))
        settingsRepository.update { it.copy(checkTime = LocalTime.of(8, 30), breakIntervalMin = 60, notificationsEnabled = true) }
        settingsRepository.setWorkOffToday(true)
    }

    @Test
    fun resetRestoresAllProgramContentAndPlanButPreservesEveryHistoryTableStateAndSettings() = runTest {
        repository.initialize()
        populateHistory()
        val dao = database.programDao()
        val before = snapshot()
        dao.upsertExercises(dao.getExercises().map { it.copy(name = "Custom", cues = listOf("Custom cue")) })
        dao.upsertPhases(dao.getPhases().map { it.copy(name = "Custom phase", minWeeks = 9, description = "Custom description") })
        dao.upsertPhaseExercises(dao.getPhaseExercises(1, SessionType.STRENGTH_A).map { it.copy(sets = 99, enabled = false) })
        dao.upsertMinimalSession(dao.getMinimalSession().map { it.copy(sets = 99, enabled = false) })
        dao.upsertReturnToSport(listOf(ReturnToSport(3, "{}")))
        dao.upsertOfficeRoutine(listOf(OfficeBreakRoutine(0, "Custom routine"), OfficeBreakRoutine(99, "Extra routine")))
        dao.upsertPlan(repository.observePlan().first().map { it.copy(sessionType = SessionType.FREE, reminderTime = LocalTime.NOON) })
        repository.restoreDefaults()
        repository.restoreDefaults()
        assertEquals(before, snapshot())
        seed.exercises.forEach { expected ->
            assertEquals(Exercise(expected.id, expected.name, expected.goal, expected.cues, expected.kind, expected.perSide),
                dao.getExercises().single { it.id == expected.id })
        }
        assertEquals(seed.phases.map { Phase(it.id, it.name, it.minWeeks, it.description) }, dao.getPhases())
        val expectedExercises = seed.phases.first().sessions.getValue(SessionType.STRENGTH_A).mapIndexed { order, expected ->
            PhaseExercise(1, SessionType.STRENGTH_A, order, expected.exerciseId, expected.sets, expected.reps,
                expected.holdSec, expected.distanceM, expected.restSec, expected.enabled)
        }
        assertEquals(expectedExercises, dao.getPhaseExercises(1, SessionType.STRENGTH_A))
        assertEquals(seed.minimalSession.map { it.sets }, dao.getMinimalSession().map { it.sets })
        assertTrue(dao.getMinimalSession().all { it.enabled })
        assertEquals(seed.phases.last().returnToSport.toString(), dao.getReturnToSport().single().contentJson)
        assertEquals(seed.defaults.officeBreakRoutine, dao.getOfficeRoutine().map { it.instruction })
        assertEquals(seed.weekTemplate.map { WeekPlanEntry(DayOfWeek.valueOf(it.day), it.sessionType, LocalTime.parse(it.reminderTime)) },
            repository.observePlan().first())
        repository.initialize()
        assertEquals(before, snapshot())
    }

    @Test
    fun failedResetRollsBackDeletesAndUpdatesAndPreservesHistory() = runTest {
        repository.initialize()
        populateHistory()
        val dao = database.programDao()
        val custom = dao.getExercises().first().copy(name = "Must survive failed reset")
        dao.upsertExercises(listOf(custom))
        repository.setPlanEntry(WeekPlanEntry(DayOfWeek.MONDAY, SessionType.FREE, LocalTime.NOON))
        val before = snapshot()
        val exercises = dao.getExercises()
        val plan = repository.observePlan().first()
        val prescriptions = dao.getPhaseExercises(1, SessionType.STRENGTH_A)
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_prescription BEFORE INSERT ON PhaseExercise
            BEGIN SELECT RAISE(ABORT, 'forced reset failure'); END
        """.trimIndent())
        assertTrue(runCatching { repository.restoreDefaults() }.isFailure)
        assertEquals(before, snapshot())
        assertEquals(exercises, dao.getExercises())
        assertEquals(plan, repository.observePlan().first())
        assertEquals(prescriptions, dao.getPhaseExercises(1, SessionType.STRENGTH_A))
        assertEquals("1", dao.getMetadata(SeedImporter.IMPORT_MARKER))
    }

    @Test
    fun resetRetainsReferencedNonSeedPhasesAndRemovesUnreferencedProgramExtras() = runTest {
        repository.initialize()
        val dao = database.programDao()
        val history = database.historyDao()
        dao.upsertPhases(listOf(Phase(10, "Historical phase", 4), Phase(11, "Unused phase", 4)))
        dao.upsertExercises(listOf(dao.getExercises().first().copy(id = "extra")))
        history.insertSession(SessionLog(date = LocalDate.now(clock), sessionType = SessionType.PILATES,
            phaseId = 10, outcome = SessionOutcome.DONE, painDuring = 2))
        history.insertTransition(PhaseTransition(date = LocalDate.now(clock), fromPhase = 3, toPhase = 10, reason = "Custom"))
        dao.upsertState(ProgramState(10, LocalDate.now(clock), 5))
        val before = snapshot()
        repository.restoreDefaults()
        assertEquals(before, snapshot())
        assertTrue(dao.getPhases().any { it.id == 10 })
        assertTrue(dao.getPhases().none { it.id == 11 })
        assertTrue(dao.getExercises().none { it.id == "extra" })
    }

    @Test
    fun resetDoesNotCreateStateOrSettings() = runTest {
        repository.restoreDefaults()
        assertNull(database.programDao().getState())
        assertNull(settingsRepository.settings.first())
        assertEquals(7, repository.observePlan().first().size)
        repository.setPlanEntry(WeekPlanEntry(DayOfWeek.MONDAY, SessionType.FREE, LocalTime.NOON))
        repository.initialize()
        assertEquals(ProgramState(1, LocalDate.now(clock), seed.defaults.weeklyTarget), database.programDao().getState())
        assertEquals(Settings.fromSeed(seed.defaults), settingsRepository.settings.first())
        assertEquals(SessionType.FREE, repository.observePlan().first().first().sessionType)
    }

    @Test
    fun realHistoryDaoEnforcesForeignKeysAndPainCheckUpsertPreservesSession() = runTest {
        repository.initialize()
        val history = database.historyDao()
        val missing = SessionLog(date = LocalDate.now(clock), sessionType = SessionType.PILATES,
            phaseId = 999, outcome = SessionOutcome.DONE, painDuring = 2)
        assertTrue(runCatching { history.insertSession(missing) }.isFailure)
        val id = history.insertSession(missing.copy(phaseId = 1))
        val check = PainCheck(id, Instant.parse("2026-10-06T09:00:00Z"), 3, true)
        history.upsertPainCheck(check)
        history.upsertPainCheck(check.copy(painNow = 4, backToBaseline = false))
        history.upsertSession(missing.copy(id = id, phaseId = 1, status = SessionStatus.GREEN))
        assertEquals(listOf(check.copy(painNow = 4, backToBaseline = false)), history.getPainChecks())
        assertEquals(1, history.getSessions().size)
        assertEquals(SessionStatus.GREEN, history.getSessions().single().status)
        assertTrue(runCatching { history.upsertPainCheck(check.copy(sessionLogId = 999)) }.isFailure)
    }
}