package it.finardi.schiena.data

import android.app.Application
import app.cash.turbine.test
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class ProgramRepositoryTest : ProgramDataFixture() {
    @Test
    fun initializeImportsCompleteSeedAndStateUsingClockZone() = runTest {
        assertNull(repository.observeState().first())
        repository.initialize()
        val dao = database.programDao()
        assertEquals(seed.exercises.size, dao.getExercises().size)
        assertEquals(seed.phases.size, dao.getPhases().size)
        assertEquals(DayOfWeek.values().toList(), repository.observePlan().first().map { it.dayOfWeek })
        assertEquals(ProgramState(1, LocalDate.of(2026, 10, 6), seed.defaults.weeklyTarget), dao.getState())
        assertEquals("1", dao.getMetadata(SeedImporter.IMPORT_MARKER))
        assertEquals(Settings.fromSeed(seed.defaults), settingsRepository.settings.first())
        seed.exercises.forEach { expected ->
            val actual = dao.getExercises().single { it.id == expected.id }
            assertEquals(expected.cues, actual.cues)
            assertEquals(expected.kind, actual.kind)
            assertEquals(expected.perSide, actual.perSide)
        }
    }

    @Test
    fun inheritanceIsMaterializedAndAllAdditionalContentIsPreserved() = runTest {
        repository.initialize()
        val dao = database.programDao()
        for (type in listOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)) {
            assertEquals(
                dao.getPhaseExercises(2, type).map { it.copy(phaseId = 3) },
                dao.getPhaseExercises(3, type),
            )
        }
        val minimal = dao.getMinimalSession()
        assertEquals(seed.minimalSession.size, minimal.size)
        seed.minimalSession.forEachIndexed { order, expected ->
            assertEquals(
                MinimalSessionExercise(order, expected.exerciseId, expected.sets, expected.reps,
                    expected.holdSec, expected.distanceM, expected.restSec, expected.enabled),
                minimal[order],
            )
        }
        val sport = dao.getReturnToSport().single()
        assertEquals(3, sport.phaseId)
        assertEquals(seed.phases.single { it.id == 3 }.returnToSport, Json.parseToJsonElement(sport.contentJson))
        assertEquals(seed.defaults.officeBreakRoutine, dao.getOfficeRoutine().map { it.instruction })
    }

    @Test
    fun initializationMarkerSurvivesNewImporterAndPreservesEdits() = runTest {
        repository.initialize()
        val dao = database.programDao()
        val exercise = dao.getExercises().first().copy(name = "Edited name")
        dao.upsertExercises(listOf(exercise))
        val plan = WeekPlanEntry(DayOfWeek.MONDAY, SessionType.FREE, LocalTime.of(20, 15))
        repository.setPlanEntry(plan)
        val state = ProgramState(2, LocalDate.of(2026, 9, 1), 5, LocalDate.of(2026, 10, 1))
        dao.upsertState(state)
        settingsRepository.update { it.copy(checkTime = LocalTime.of(8, 5), notificationsEnabled = true) }
        val settings = settingsRepository.settings.first()
        newRepository().initialize()
        newRepository().initialize()
        assertEquals(exercise, dao.getExercises().first())
        assertEquals(plan, repository.observePlan().first().first())
        assertEquals(state, dao.getState())
        assertEquals(settings, settingsRepository.settings.first())
    }

    @Test
    fun existingParentWithoutMetadataDoesNotSkipImport() = runTest {
        val first = seed.exercises.first()
        database.programDao().upsertExercises(listOf(
            Exercise(first.id, "Partial import", first.goal, emptyList(), first.kind, first.perSide),
        ))
        repository.initialize()
        assertEquals(seed.exercises.size, database.programDao().getExercises().size)
        assertEquals(first.name, database.programDao().getExercises().single { it.id == first.id }.name)
        assertNotNull(database.programDao().getMetadata(SeedImporter.IMPORT_MARKER))
    }

    @Test
    fun concurrentInitializationIsIdempotent() = runTest {
        List(4) { async { newRepository().initialize() } }.awaitAll()
        assertEquals(7, repository.observePlan().first().size)
        assertEquals(seed.exercises.size, database.programDao().getExercises().size)
        assertEquals(Settings.fromSeed(seed.defaults), settingsRepository.settings.first())
    }

    @Test
    fun planAndStateAndPhaseFlowsExposeExactRepositoryContract() = runTest {
        repository.initialize()
        repository.observePlan().test {
            val initial = awaitItem()
            assertEquals(7, initial.size)
            val entry = WeekPlanEntry(DayOfWeek.WEDNESDAY, SessionType.FREE, LocalTime.of(21, 0))
            repository.setPlanEntry(entry.dayOfWeek, entry.sessionType, entry.reminderTime)
            assertEquals(entry, awaitItem().single { it.dayOfWeek == DayOfWeek.WEDNESDAY })
            cancelAndIgnoreRemainingEvents()
        }
        repository.observeState().test {
            assertEquals(1, awaitItem()?.currentPhaseId)
            val updated = ProgramState(3, LocalDate.of(2026, 10, 6), 6)
            database.programDao().upsertState(updated)
            assertEquals(updated, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.observePhases().test {
            assertEquals(seed.phases.map { it.name }, awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun explicitSessionsOverrideInheritedSessionsWithoutLosingOtherTypes() = runTest {
        val parent = seed.phases.single { it.id == 2 }
        val override = parent.sessions.getValue(SessionType.STRENGTH_A).take(1)
        seed = seed.copy(phases = seed.phases.map {
            if (it.id == 3) it.copy(sessions = mapOf(SessionType.STRENGTH_A to override)) else it
        })
        repository.initialize()
        assertEquals(1, database.programDao().getPhaseExercises(3, SessionType.STRENGTH_A).size)
        assertEquals(parent.sessions.getValue(SessionType.STRENGTH_B).size,
            database.programDao().getPhaseExercises(3, SessionType.STRENGTH_B).size)
    }

    @Test
    fun invalidAndCyclicSeedsLeaveDatabaseAndSettingsUninitialized() = runTest {
        val valid = seed
        val invalidSeeds = listOf(
            valid.copy(version = 2),
            valid.copy(exercises = valid.exercises + valid.exercises.first()),
            valid.copy(weekTemplate = valid.weekTemplate.dropLast(1)),
            valid.copy(minimalSession = listOf(valid.minimalSession.first().copy(exerciseId = "missing"))),
            valid.copy(phases = valid.phases.map { if (it.id == 3) it.copy(inheritSessionsFromPhase = 99) else it }),
            valid.copy(phases = valid.phases.map { if (it.id == 2) it.copy(inheritSessionsFromPhase = 3) else it }),
            valid.copy(defaults = valid.defaults.copy(officeBreak = valid.defaults.officeBreak.copy(intervalMin = 59))),
        )
        for (invalid in invalidSeeds) {
            seed = invalid
            val failure = runCatching { repository.initialize() }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(database.programDao().getExercises().isEmpty())
            assertNull(database.programDao().getState())
            assertNull(database.programDao().getMetadata(SeedImporter.IMPORT_MARKER))
            assertNull(settingsRepository.settings.first())
        }
        seed = valid
        repository.initialize()
        assertEquals(7, repository.observePlan().first().size)
    }

    @Test
    fun sqliteFailureAfterWritesRollsBackContentStateAndMarker() = runTest {
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_marker BEFORE INSERT ON ProgramMetadata
            BEGIN SELECT RAISE(ABORT, 'forced test failure'); END
        """.trimIndent())
        assertTrue(runCatching { repository.initialize() }.isFailure)
        assertTrue(database.programDao().getExercises().isEmpty())
        assertTrue(database.programDao().getPhases().isEmpty())
        assertTrue(repository.observePlan().first().isEmpty())
        assertNull(database.programDao().getState())
        assertNull(database.programDao().getMetadata(SeedImporter.IMPORT_MARKER))
        assertNull(settingsRepository.settings.first())
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_marker")
        repository.initialize()
        assertEquals(7, repository.observePlan().first().size)
    }
}