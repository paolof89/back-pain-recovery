package it.finardi.schiena.data

import android.app.Application
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SessionRepositoryTest : ProgramDataFixture() {
    private val date = LocalDate.of(2026, 10, 6)
    private val sessions get() = SessionRepository(database)

    @Test
    fun seedPrescriptionsPreserveOrderAndEveryParameterIncludingInheritance() = runBlocking {
        repository.initialize()
        val exercises = database.programDao().getExercises().associateBy { it.id }
        for (phase in seed.phases) {
            val source = phase.inheritSessionsFromPhase?.let { parentId ->
                seed.phases.single { it.id == parentId }
            } ?: phase
            for (type in listOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)) {
                val expected = source.sessions.getValue(type).filter { it.enabled }.map {
                    ExercisePrescription(exercises.getValue(it.exerciseId), it.sets, it.reps,
                        it.holdSec, it.distanceM, it.restSec)
                }
                assertEquals("phase=${phase.id} type=$type", expected,
                    sessions.prescriptions(phase.id, type, minimal = false))
            }
        }
        assertTrue(sessions.prescriptions(1, SessionType.AEROBIC, minimal = false).isEmpty())
    }

    @Test
    fun phasePrescriptionsAreOrderedAndDisabledRowsAreExcluded() = runBlocking {
        repository.initialize()
        val dao = database.programDao()
        val rows = dao.getPhaseExercises(1, SessionType.STRENGTH_A)
        dao.upsertPhaseExercises(rows.reversed().mapIndexed { index, row ->
            row.copy(enabled = row.order != 1, sets = index + 1, restSec = 17 + index)
        })
        val exercises = dao.getExercises().associateBy { it.id }
        val expected = rows.filter { it.order != 1 }.map { row ->
            val reverseIndex = rows.lastIndex - row.order
            ExercisePrescription(exercises.getValue(row.exerciseId), reverseIndex + 1,
                row.reps, row.holdSec, row.distanceM, 17 + reverseIndex)
        }
        assertEquals(expected, sessions.prescriptions(1, SessionType.STRENGTH_A, minimal = false))
    }

    @Test
    fun minimalUsesSeedRecipeRatherThanPhaseOrSessionType() = runBlocking {
        repository.initialize()
        val exercises = database.programDao().getExercises().associateBy { it.id }
        val expected = seed.minimalSession.filter { it.enabled }.map {
            ExercisePrescription(exercises.getValue(it.exerciseId), it.sets, it.reps,
                it.holdSec, it.distanceM, it.restSec)
        }
        for (phase in seed.phases) {
            for (type in SessionType.values()) {
                assertEquals(expected, sessions.prescriptions(phase.id, type, minimal = true))
            }
        }
    }

    @Test
    fun editedMinimalRecipeIsAuthoritativeOrderedAndExcludesDisabledRows() = runBlocking {
        repository.initialize()
        val dao = database.programDao()
        dao.upsertPhaseExercises(dao.getPhaseExercises(1, SessionType.STRENGTH_A).map {
            it.copy(sets = 9, restSec = 99, enabled = false)
        })
        val rows = dao.getMinimalSession().map {
            it.copy(sets = 4, reps = 7, holdSec = 13, distanceM = 21,
                restSec = 11, enabled = it.order != 1)
        }
        dao.upsertMinimalSession(rows.reversed())
        val exercises = dao.getExercises().associateBy { it.id }
        val expected = rows.filter { it.enabled }.map {
            ExercisePrescription(exercises.getValue(it.exerciseId), it.sets, it.reps,
                it.holdSec, it.distanceM, it.restSec)
        }
        assertEquals(expected, sessions.prescriptions(1, SessionType.STRENGTH_A, minimal = true))
        assertEquals(expected, sessions.prescriptions(3, SessionType.FREE, minimal = true))
        assertTrue(sessions.prescriptions(1, SessionType.STRENGTH_A, minimal = false).isEmpty())
    }

    @Test
    fun saveAndUpdateSameDayAndTypeKeepOneIdAndReplaceAllFields() = runBlocking {
        repository.initialize()
        val firstId = save(pain = 6, radiating = true, duration = 30, note = " first ")
        assertTrue(firstId > 0)
        assertEquals(SessionLog(firstId, date, SessionType.STRENGTH_A, 1,
            SessionOutcome.DONE, 6, true, 30, "first", SessionStatus.RED),
            database.historyDao().getSession(date, SessionType.STRENGTH_A))
        val secondId = save(phaseId = 2, outcome = SessionOutcome.MINIMAL, pain = 4,
            duration = 5, note = " changed ")
        assertEquals(firstId, secondId)
        assertEquals(listOf(SessionLog(firstId, date, SessionType.STRENGTH_A, 2,
            SessionOutcome.MINIMAL, 4, false, 5, "changed", SessionStatus.YELLOW)),
            database.historyDao().getSessions())
    }

    @Test
    fun differentTypesAndDatesCreateDistinctLogsAndLookupIsExact() = runBlocking {
        repository.initialize()
        val strengthId = save()
        val aerobicId = save(type = SessionType.AEROBIC)
        val nextDayId = save(day = date.plusDays(1))
        assertNotEquals(strengthId, aerobicId)
        assertNotEquals(strengthId, nextDayId)
        assertNotEquals(aerobicId, nextDayId)
        val dao = database.historyDao()
        assertEquals(3, dao.getSessions().size)
        assertEquals(strengthId, dao.getSession(date, SessionType.STRENGTH_A)?.id)
        assertEquals(aerobicId, dao.getSession(date, SessionType.AEROBIC)?.id)
        assertEquals(nextDayId, dao.getSession(date.plusDays(1), SessionType.STRENGTH_A)?.id)
        assertNull(dao.getSession(date, SessionType.FREE))
        assertNull(dao.getSession(date.minusDays(1), SessionType.STRENGTH_A))
    }

    @Test
    fun relogDeletesOnlyItsObsoletePainCheckAndResetsProvisionalStatus() = runBlocking {
        repository.initialize()
        val firstId = save(pain = 6)
        val otherId = save(type = SessionType.PILATES)
        val dao = database.historyDao()
        dao.upsertPainCheck(PainCheck(firstId, clock.instant(), 2, true))
        val otherCheck = PainCheck(otherId, clock.instant(), 3, false)
        dao.upsertPainCheck(otherCheck)
        assertEquals(2, dao.getPainChecks().size)
        assertEquals(firstId, save(pain = 1))
        assertEquals(listOf(otherCheck), dao.getPainChecks())
        assertEquals(listOf(otherCheck), sessions.observePainChecks().first())
        assertEquals(SessionStatus.PENDING, dao.getSession(date, SessionType.STRENGTH_A)?.status)
    }

    @Test
    fun skipClearsPainRadiatingDurationAndObsoleteCheck() = runBlocking {
        repository.initialize()
        val sessionId = save(pain = 8, radiating = true, duration = 40, note = "old")
        database.historyDao().upsertPainCheck(PainCheck(sessionId, clock.instant(), 7, false))
        assertEquals(sessionId, save(outcome = SessionOutcome.SKIPPED, pain = null,
            radiating = true, duration = 15, note = "  "))
        assertEquals(listOf(SessionLog(sessionId, date, SessionType.STRENGTH_A, 1,
            SessionOutcome.SKIPPED, status = SessionStatus.UNVERIFIED)),
            database.historyDao().getSessions())
        assertTrue(database.historyDao().getPainChecks().isEmpty())
        val newSkip = save(type = SessionType.FREE, outcome = SessionOutcome.SKIPPED,
            pain = 10, radiating = true, duration = 10, note = " reason ")
        assertEquals(SessionLog(newSkip, date, SessionType.FREE, 1,
            SessionOutcome.SKIPPED, note = "reason", status = SessionStatus.UNVERIFIED),
            database.historyDao().getSession(date, SessionType.FREE))
    }

    @Test
    fun durationBoundariesAndNullAreAcceptedAndBlankNoteBecomesNull() = runBlocking {
        repository.initialize()
        for (duration in listOf(null, 1, 1440)) {
            save(duration = duration, note = " \t\n ")
            val log = database.historyDao().getSessions().single()
            assertEquals(duration, log.durationMin)
            assertNull(log.note)
        }
    }

    @Test
    fun invalidDurationDoesNotInsertUpdateOrDeleteChecksForAnyOutcome() = runBlocking {
        repository.initialize()
        val sessionId = save()
        val dao = database.historyDao()
        dao.upsertPainCheck(PainCheck(sessionId, clock.instant(), 1, true))
        val beforeLogs = dao.getSessions()
        val beforeChecks = dao.getPainChecks()
        for (outcome in SessionOutcome.values()) {
            for (duration in listOf(Int.MIN_VALUE, -1, 0, 1441, Int.MAX_VALUE)) {
                for (type in listOf(SessionType.STRENGTH_A, SessionType.FREE)) {
                    val failure = runCatching { save(type = type, outcome = outcome,
                        duration = duration) }.exceptionOrNull()
                    assertTrue("outcome=$outcome duration=$duration type=$type",
                        failure is IllegalArgumentException)
                    assertEquals(beforeLogs, dao.getSessions())
                    assertEquals(beforeChecks, dao.getPainChecks())
                }
            }
        }
    }

    @Test
    fun missingOrInvalidRequiredPainDoesNotInsertUpdateOrDeleteChecks() = runBlocking {
        repository.initialize()
        val sessionId = save()
        val dao = database.historyDao()
        dao.upsertPainCheck(PainCheck(sessionId, clock.instant(), 1, true))
        val beforeLogs = dao.getSessions()
        val beforeChecks = dao.getPainChecks()
        for (outcome in listOf(SessionOutcome.DONE, SessionOutcome.MINIMAL)) {
            for (pain in listOf(null, -1, 11, Int.MIN_VALUE, Int.MAX_VALUE)) {
                for (type in listOf(SessionType.STRENGTH_A, SessionType.FREE)) {
                    val failure = runCatching { save(type = type, outcome = outcome,
                        pain = pain, radiating = true) }.exceptionOrNull()
                    assertTrue("outcome=$outcome pain=$pain type=$type",
                        failure is IllegalArgumentException)
                    assertEquals(beforeLogs, dao.getSessions())
                    assertEquals(beforeChecks, dao.getPainChecks())
                }
            }
        }
    }

    @Test
    fun sessionObservationUsesInclusiveRangeAndDateThenIdOrder() = runBlocking {
        repository.initialize()
        val lastId = save(day = date.plusDays(1))
        val firstId = save()
        val secondId = save(type = SessionType.PILATES)
        save(day = date.minusDays(1))
        save(day = date.plusDays(2))
        assertEquals(listOf(firstId, secondId, lastId),
            sessions.observeSessions(date, date.plusDays(1)).first().map { it.id })
        assertEquals(listOf(firstId, secondId),
            sessions.observeSessions(date, date).first().map { it.id })
        assertTrue(sessions.observeSessions(date.plusDays(3), date.plusDays(4)).first().isEmpty())
    }

    @Test
    fun historyFlowsAreOrderedAndDeletingMissingCheckIsHarmless() = runBlocking {
        repository.initialize()
        val firstId = save()
        val secondId = save(type = SessionType.PILATES)
        val dao = database.historyDao()
        assertTrue(sessions.observePainChecks().first().isEmpty())
        assertTrue(sessions.observeOfficeBreaks().first().isEmpty())
        val firstCheck = PainCheck(firstId, clock.instant(), 1, true)
        val secondCheck = PainCheck(secondId, clock.instant(), 4, false)
        dao.upsertPainCheck(secondCheck)
        dao.upsertPainCheck(firstCheck)
        assertEquals(listOf(firstCheck, secondCheck), sessions.observePainChecks().first())
        val earlyBreak = OfficeBreakEvent(clock.instant(), OfficeBreakAction.DONE)
        val lateBreak = OfficeBreakEvent(clock.instant().plusSeconds(60), OfficeBreakAction.SKIPPED)
        dao.upsertOfficeBreak(lateBreak)
        dao.upsertOfficeBreak(earlyBreak)
        assertEquals(listOf(earlyBreak, lateBreak), sessions.observeOfficeBreaks().first())
        dao.deletePainCheck(secondId)
        dao.deletePainCheck(secondId)
        assertEquals(listOf(firstCheck), sessions.observePainChecks().first())
    }

    @Test
    fun readsSavesUpdatesSkipsAndRejectionsLeaveMetadataAndProgramStateUnchanged() = runBlocking {
        repository.initialize()
        val dao = database.programDao()
        val state = ProgramState(2, date.minusWeeks(5), 6, date.minusDays(2))
        dao.upsertState(state)
        dao.upsertMetadata(ProgramMetadata("test-marker", "preserve-me"))
        val importMarker = dao.getMetadata(SeedImporter.IMPORT_MARKER)
        assertEquals("1", importMarker)
        sessions.prescriptions(1, SessionType.STRENGTH_A, minimal = false)
        sessions.prescriptions(3, SessionType.FREE, minimal = true)
        save()
        save(outcome = SessionOutcome.MINIMAL, pain = 5)
        save(outcome = SessionOutcome.SKIPPED, pain = null)
        save(type = SessionType.AEROBIC)
        assertTrue(runCatching { save(duration = 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { save(pain = null) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(state, dao.getState())
        assertEquals(importMarker, dao.getMetadata(SeedImporter.IMPORT_MARKER))
        assertEquals("preserve-me", dao.getMetadata("test-marker"))
        assertTrue(database.historyDao().getTransitions().isEmpty())
    }

    private suspend fun save(
        day: LocalDate = date,
        type: SessionType = SessionType.STRENGTH_A,
        phaseId: Int = 1,
        outcome: SessionOutcome = SessionOutcome.DONE,
        pain: Int? = 2,
        radiating: Boolean = false,
        duration: Int? = null,
        note: String? = null,
    ): Long = sessions.save(day, type, phaseId, outcome, pain, radiating, duration, note)
}