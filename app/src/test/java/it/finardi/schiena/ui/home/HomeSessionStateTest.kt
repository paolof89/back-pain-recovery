package it.finardi.schiena.ui.home

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import it.finardi.schiena.data.ProgramDataFixture
import it.finardi.schiena.data.SessionRepository
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HomeSessionStateTest : ProgramDataFixture() {
    private val today = LocalDate.of(2026, 10, 6)
    private val legacyDate = LocalDate.of(2026, 9, 28)
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: HomeViewModel
    private lateinit var sessions: SessionRepository

    @Before
    fun setUpSession() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository.initialize()
        sessions = SessionRepository(database)
        assertEquals(today, LocalDate.now(clock))
    }

    @After
    fun tearDownSession() {
        try {
            viewModelStore.clear()
            if (::viewModel.isInitialized) {
                runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aerobicBeginsLogWithoutPrescriptionsWhenPlayerIsNotRequired() = runBlocking {
        val savedState = SavedStateHandle()
        createViewModel(savedState)
        val home = awaitHome()
        assertEquals(SessionType.AEROBIC, home.entry?.sessionType)
        assertTrue(sessions.prescriptions(home.phase!!.id, SessionType.AEROBIC, false).isEmpty())

        viewModel.begin(minimal = false, requiresPlayer = false)
        val state = awaitSession()

        val expected = SessionContext(today, SessionType.AEROBIC, home.phase.id, false)
        assertEquals(expected, state.context)
        assertTrue(state.exercises.isEmpty())
        assertFalse(state.error)
        assertFalse(state.saved)
        assertSavedContext(savedState, expected, requiresPlayer = false)
    }

    @Test
    fun skippedStrengthQuickLogIsAllowedWithAllStrengthPrescriptionsDisabled() = runBlocking {
        disableStrengthPrescriptions()
        val savedState = SavedStateHandle()
        createViewModel(savedState)
        val home = awaitHome()
        repository.setPlanEntry(home.entry!!.copy(sessionType = SessionType.STRENGTH_A))
        awaitHome { it.entry?.sessionType == SessionType.STRENGTH_A }
        assertTrue(sessions.prescriptions(home.phase!!.id, SessionType.STRENGTH_A, false).isEmpty())

        viewModel.begin(minimal = false, requiresPlayer = false)
        val state = awaitSession()
        val expected = SessionContext(today, SessionType.STRENGTH_A, home.phase.id, false)
        assertEquals(expected, state.context)
        assertTrue(state.exercises.isEmpty())
        assertFalse(state.error)
        assertSavedContext(savedState, expected, requiresPlayer = false)

        viewModel.save(SessionOutcome.SKIPPED, null, false, null, "Skipped strength")
        val saved = awaitSession { it.saved }
        assertFalse(saved.saveError)
        val log = sessions.observeSessions(today, today).first { it.isNotEmpty() }.single()
        assertEquals(expected.date, log.date)
        assertEquals(expected.type, log.sessionType)
        assertEquals(expected.phaseId, log.phaseId)
        assertEquals(SessionOutcome.SKIPPED, log.outcome)
        assertEquals("Skipped strength", log.note)
    }

    @Test
    fun requiredStrengthPlayerRejectsEmptyEnabledPrescriptions() = runBlocking {
        disableStrengthPrescriptions()
        createViewModel()
        val home = awaitHome()
        repository.setPlanEntry(home.entry!!.copy(sessionType = SessionType.STRENGTH_B))
        awaitHome { it.entry?.sessionType == SessionType.STRENGTH_B }

        viewModel.begin(minimal = false, requiresPlayer = true)
        val state = awaitSession()

        assertEquals(SessionContext(today, SessionType.STRENGTH_B, home.phase!!.id, false), state.context)
        assertTrue(state.exercises.isEmpty())
        assertTrue(state.error)
        assertFalse(state.saving)
        assertFalse(state.saved)
    }

    @Test
    fun requiredMinimalPlayerRejectsEmptyEnabledMinimalPrescriptions() = runBlocking {
        disableMinimalPrescriptions()
        createViewModel()
        val home = awaitHome()
        assertEquals(SessionType.AEROBIC, home.entry?.sessionType)

        viewModel.begin(minimal = true, requiresPlayer = true)
        val state = awaitSession()

        assertEquals(SessionContext(today, SessionType.AEROBIC, home.phase!!.id, true), state.context)
        assertTrue(state.exercises.isEmpty())
        assertTrue(state.error)
        assertFalse(state.saving)
        assertFalse(state.saved)
    }

    @Test
    fun restoredContextIsExactAndRetryRetainsRequiresPlayer() = runBlocking {
        val expected = SessionContext(legacyDate, SessionType.STRENGTH_B, 2, true)
        val previousId = sessions.save(legacyDate, expected.type, expected.phaseId,
            SessionOutcome.MINIMAL, 2, false, 5, "Previous minimal")
        val savedState = savedContext(expected, requiresPlayer = true)
        createViewModel(savedState)
        val home = awaitHome()
        val restored = awaitSession()

        assertEquals(today, home.today)
        assertEquals(SessionType.AEROBIC, home.entry?.sessionType)
        assertEquals(1, home.phase?.id)
        assertEquals(expected, restored.context)
        assertEquals(sessions.prescriptions(2, SessionType.STRENGTH_B, true), restored.exercises)
        assertTrue(restored.exercises.isNotEmpty())
        assertFalse(restored.error)
        assertEquals(previousId, restored.previous?.id)
        assertSavedContext(savedState, expected, requiresPlayer = true)

        disableMinimalPrescriptions()
        viewModel.retrySession()
        val retried = awaitSession { it.error }
        assertEquals(expected, retried.context)
        assertTrue(retried.exercises.isEmpty())
        assertSavedContext(savedState, expected, requiresPlayer = true)
    }

    @Test
    fun saveUsesFrozenContextGuardsSecondSaveAndAcknowledgementClearsAllSessionKeys() = runBlocking {
        disableStrengthPrescriptions()
        val expected = SessionContext(legacyDate, SessionType.STRENGTH_A, 2, false)
        val savedState = savedContext(expected, requiresPlayer = false)
        savedState["unrelated"] = "retained"
        createViewModel(savedState)
        val home = awaitHome()
        val restored = awaitSession()
        assertEquals(expected, restored.context)
        assertTrue(restored.exercises.isEmpty())
        assertFalse(restored.error)
        assertSavedContext(savedState, expected, requiresPlayer = false)

        val programState = checkNotNull(database.programDao().getState())
        database.programDao().upsertState(programState.copy(currentPhaseId = 3))
        repository.setPlanEntry(home.entry!!.copy(sessionType = SessionType.FREE))
        awaitHome { it.phase?.id == 3 && it.entry?.sessionType == SessionType.FREE }
        assertEquals(expected, viewModel.sessionState.value.context)

        viewModel.save(SessionOutcome.DONE, 3, false, 23, "  Frozen log  ")
        val saved = awaitSession { it.saved }
        assertFalse(saved.saving)
        assertFalse(saved.saveError)
        val log = sessions.observeSessions(legacyDate, today).first { it.isNotEmpty() }.single()
        assertEquals(expected.date, log.date)
        assertEquals(expected.type, log.sessionType)
        assertEquals(expected.phaseId, log.phaseId)
        assertEquals(SessionOutcome.DONE, log.outcome)
        assertEquals(3, log.painDuring)
        assertFalse(log.radiating)
        assertEquals(23, log.durationMin)
        assertEquals("Frozen log", log.note)
        assertSavedContext(savedState, expected, requiresPlayer = false)

        viewModel.save(SessionOutcome.SKIPPED, null, false, null, "Must not replace")
        assertEquals(saved, viewModel.sessionState.value)
        assertEquals(listOf(log), sessions.observeSessions(legacyDate, today).first())

        viewModel.acknowledgeSave()
        assertEquals(SessionUiState(), viewModel.sessionState.value)
        listOf("sessionDate", "sessionType", "sessionPhase", "sessionMinimal", "requiresPlayer").forEach {
            assertFalse("Session key remains: $it", savedState.contains(it))
        }
        assertEquals("retained", savedState.get<String>("unrelated"))
        assertEquals(listOf(log), sessions.observeSessions(legacyDate, today).first())
    }

    @Test
    fun minimalFromSessionPreservesFrozenDateTypeAndPhaseDespiteDifferentHome() = runBlocking {
        val original = SessionContext(legacyDate, SessionType.STRENGTH_B, 2, false)
        val savedState = savedContext(original, requiresPlayer = false)
        createViewModel(savedState)
        val home = awaitHome()
        assertEquals(original, awaitSession().context)
        val programState = checkNotNull(database.programDao().getState())
        database.programDao().upsertState(programState.copy(currentPhaseId = 3))
        repository.setPlanEntry(home.entry!!.copy(sessionType = SessionType.FREE))
        val changedHome = awaitHome { it.phase?.id == 3 && it.entry?.sessionType == SessionType.FREE }
        assertEquals(today, changedHome.today)

        viewModel.beginMinimalFromSession()
        val minimal = awaitSession { it.context?.minimal == true }
        val expected = original.copy(minimal = true)

        assertEquals(expected, minimal.context)
        assertEquals(sessions.prescriptions(original.phaseId, original.type, true), minimal.exercises)
        assertTrue(minimal.exercises.isNotEmpty())
        assertFalse(minimal.error)
        assertSavedContext(savedState, expected, requiresPlayer = true)
    }

    private fun createViewModel(savedState: SavedStateHandle = SavedStateHandle()) {
        viewModel = HomeViewModel(repository, sessions, settingsRepository, clock, savedState)
        viewModelStore.put("home", viewModel)
    }

    private suspend fun awaitHome(predicate: (HomeUiState) -> Boolean = { true }): HomeUiState =
        withTimeout(10_000) {
            viewModel.uiState.first { !it.loading && predicate(it) }.also { assertFalse(it.error) }
        }

    private suspend fun awaitSession(predicate: (SessionUiState) -> Boolean = { true }): SessionUiState =
        withTimeout(10_000) {
            viewModel.sessionState.first { it.context != null && !it.loading && predicate(it) }
        }

    private suspend fun disableStrengthPrescriptions() {
        val dao = database.programDao()
        val prescriptions = dao.getPhases().flatMap { phase ->
            listOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B).flatMap { type ->
                dao.getPhaseExercises(phase.id, type)
            }
        }
        assertTrue(prescriptions.isNotEmpty())
        dao.upsertPhaseExercises(prescriptions.map { it.copy(enabled = false) })
    }

    private suspend fun disableMinimalPrescriptions() {
        val dao = database.programDao()
        val prescriptions = dao.getMinimalSession()
        assertTrue(prescriptions.isNotEmpty())
        dao.upsertMinimalSession(prescriptions.map { it.copy(enabled = false) })
    }

    private fun savedContext(context: SessionContext, requiresPlayer: Boolean) = SavedStateHandle(mapOf(
        "sessionDate" to context.date.toString(),
        "sessionType" to context.type.name,
        "sessionPhase" to context.phaseId,
        "sessionMinimal" to context.minimal,
        "requiresPlayer" to requiresPlayer,
    ))

    private fun assertSavedContext(savedState: SavedStateHandle, context: SessionContext, requiresPlayer: Boolean) {
        assertEquals(context.date.toString(), savedState.get<String>("sessionDate"))
        assertEquals(context.type.name, savedState.get<String>("sessionType"))
        assertEquals(context.phaseId, savedState.get<Int>("sessionPhase"))
        assertEquals(context.minimal, savedState.get<Boolean>("sessionMinimal"))
        assertEquals(requiresPlayer, savedState.get<Boolean>("requiresPlayer"))
    }
}