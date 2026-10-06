package it.finardi.schiena.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.finardi.schiena.data.*
import it.finardi.schiena.domain.*
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SessionContext(val date: LocalDate, val type: SessionType, val phaseId: Int, val minimal: Boolean)

data class HomeUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val today: LocalDate? = null,
    val entry: WeekPlanEntry? = null,
    val phase: Phase? = null,
    val phaseWeek: Int = 1,
    val target: Int = 0,
    val completed: Int = 0,
    val breaksDone: Int = 0,
    val breaksTarget: Int = 0,
    val workOff: Boolean = false,
    val pendingChecks: Int = 0,
    val history: List<SessionLog> = emptyList(),
    val actionError: Boolean = false,
)

internal fun homeSnapshot(
    today: LocalDate, plan: List<WeekPlanEntry>, program: ProgramState?, phases: List<Phase>,
    logs: List<SessionLog>, checks: List<PainCheck>, breaks: List<OfficeBreakEvent>,
    settings: Settings?, clock: Clock,
): HomeUiState {
    val phase = phases.find { it.id == program?.currentPhaseId }
    val entry = plan.find { it.dayOfWeek == today.dayOfWeek }
    val workOff = settings?.isWorkOffToday(today) == true
    val working = settings != null && today.dayOfWeek in settings.workDays && !workOff
    val checked = checks.map { it.sessionLogId }.toSet()
    val pending = logs.count {
        it.outcome != SessionOutcome.SKIPPED &&
            it.sessionType in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B, SessionType.PILATES) &&
            it.id !in checked && settings != null &&
            clock.instant() >= it.date.plusDays(1).atTime(settings.checkTime).atZone(clock.zone).toInstant() &&
            clock.instant() < it.date.plusDays(3).atTime(settings.checkTime).atZone(clock.zone).toInstant()
    }
    return HomeUiState(
        loading = false, error = phase == null || entry == null || program == null || settings == null,
        today = today, entry = entry, phase = phase,
        phaseWeek = program?.let { (ChronoUnit.DAYS.between(it.phaseStartDate, today).coerceAtLeast(0) / 7 + 1).toInt() } ?: 1,
        target = program?.weeklyTarget ?: 0,
        completed = weeklyCompleted(logs.map { ProgressEntry(it.date, it.outcome) }, today),
        breaksDone = breaks.count { it.action == OfficeBreakAction.DONE && it.timestamp.atZone(clock.zone).toLocalDate() == today },
        breaksTarget = if (working) (Duration.between(settings!!.workStart, settings.workEnd).toMinutes() / settings.breakIntervalMin).toInt() else 0,
        workOff = workOff, pendingChecks = pending,
        history = logs.filter { it.date <= today }.sortedWith(compareByDescending<SessionLog> { it.date }.thenByDescending { it.id }),
    )
}

data class SessionUiState(
    val context: SessionContext? = null,
    val exercises: List<ExercisePrescription> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveError: Boolean = false,
    val previous: SessionLog? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val program: ProgramRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val home = MutableStateFlow(HomeUiState())
    val uiState = home.asStateFlow()
    private val session = MutableStateFlow(SessionUiState())
    val sessionState = session.asStateFlow()
    private var observation: Job? = null
    private var preparation: Job? = null
    private val dates = MutableStateFlow(LocalDate.now(clock))
    private val ticks = MutableStateFlow(clock.instant())

    init {
        retry()
        viewModelScope.launch {
            while (true) { delay(30_000); refreshDate() }
        }
        val date = savedState.get<String>("sessionDate")
        if (date != null) prepare(SessionContext(LocalDate.parse(date),
            SessionType.valueOf(checkNotNull(savedState["sessionType"])),
            checkNotNull(savedState["sessionPhase"]), savedState["sessionMinimal"] ?: false), savedState["requiresPlayer"] ?: false)
    }

    fun refreshDate() {
        dates.value = LocalDate.now(clock)
        ticks.value = clock.instant()
    }

    fun retry() {
        observation?.cancel()
        home.update { it.copy(loading = true, error = false) }
        observation = viewModelScope.launch {
            try {
                program.initialize()
                val content = combine(program.observePlan(), program.observeState(), program.observePhases()) { plan, state, phases -> Triple(plan, state, phases) }
                dates.flatMapLatest { today ->
                    val history = combine(sessions.observeSessions(today.minusDays(14), today), sessions.observePainChecks(), sessions.observeOfficeBreaks()) { logs, checks, breaks -> Triple(logs, checks, breaks) }
                    val preferences = combine(settings.settings, ticks) { value, _ -> value }
                    combine(content, history, preferences) { data, events, value ->
                        homeSnapshot(today, data.first, data.second, data.third, events.first, events.second, events.third, value, clock)
                    }
                }.collect { snapshot -> home.value = snapshot }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { home.update { it.copy(loading = false, error = true) } }
        }
    }

    fun begin(minimal: Boolean, requiresPlayer: Boolean = true) {
        val snapshot = home.value
        if (snapshot.loading || snapshot.error || session.value.saving) return
        prepare(SessionContext(checkNotNull(snapshot.today), checkNotNull(snapshot.entry).sessionType, checkNotNull(snapshot.phase).id, minimal), requiresPlayer)
    }

    private fun prepare(context: SessionContext, requiresPlayer: Boolean) {
        preparation?.cancel()
        savedState["sessionDate"] = context.date.toString()
        savedState["sessionType"] = context.type.name
        savedState["sessionPhase"] = context.phaseId
        savedState["sessionMinimal"] = context.minimal
        savedState["requiresPlayer"] = requiresPlayer
        session.value = SessionUiState(context = context, loading = true)
        preparation = viewModelScope.launch {
            try {
                program.initialize()
                val exercises = if (requiresPlayer) sessions.prescriptions(context.phaseId, context.type, context.minimal) else emptyList()
                val previous = sessions.observeSessions(context.date, context.date).first().lastOrNull { it.sessionType == context.type }
                val needsPlayer = requiresPlayer && (context.minimal || context.type in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B))
                session.value = SessionUiState(context = context, exercises = exercises, previous = previous, error = needsPlayer && exercises.isEmpty())
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { session.update { it.copy(loading = false, error = true) } }
        }
    }

    fun retrySession() { session.value.context?.let { prepare(it, savedState["requiresPlayer"] ?: false) } }

    fun beginMinimalFromSession() {
        if (!session.value.saving) session.value.context?.let { prepare(it.copy(minimal = true), true) }
    }

    fun save(outcome: SessionOutcome, pain: Int?, radiating: Boolean, duration: Int?, note: String) {
        val snapshot = session.value
        val context = snapshot.context ?: return
        if (snapshot.saving || snapshot.saved || snapshot.loading) return
        session.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                sessions.save(context.date, context.type, context.phaseId, outcome, pain, radiating, duration, note)
                session.update { it.copy(saving = false, saved = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { session.update { it.copy(saving = false, saveError = true) } }
        }
    }

    fun setWorkOff(value: Boolean) {
        viewModelScope.launch {
            try {
                settings.setWorkOffToday(value)
                home.update { it.copy(actionError = false) }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { home.update { it.copy(actionError = true) } }
        }
    }

    fun acknowledgeSave() {
        listOf("sessionDate", "sessionType", "sessionPhase", "sessionMinimal", "requiresPlayer").forEach {
            savedState.remove<Any>(it)
        }
        session.value = SessionUiState()
    }
}