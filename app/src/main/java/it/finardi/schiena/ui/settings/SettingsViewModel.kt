package it.finardi.schiena.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.finardi.schiena.data.ProgramRepository
import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.SettingsRepository
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.notif.AlarmScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanDraft(val day: DayOfWeek, val type: SessionType, val time: String)

data class SettingsDraft(
    val plan: List<PlanDraft>,
    val checkTime: String,
    val workDays: Set<DayOfWeek>,
    val workStart: String,
    val workEnd: String,
    val interval: Int,
    val notifications: Boolean,
    val accepted: Boolean,
) {
    fun validatedPlan(): List<WeekPlanEntry> {
        require(plan.map { it.day }.toSet() == DayOfWeek.entries.toSet() && plan.size == 7)
        return plan.map { WeekPlanEntry(it.day, it.type, parseTime(it.time)) }
    }

    fun applyTo(current: Settings, onboarding: Boolean): Settings = current.copy(
        checkTime = parseTime(checkTime), workDays = workDays,
        workStart = parseTime(workStart), workEnd = parseTime(workEnd),
        breakIntervalMin = interval, notificationsEnabled = notifications,
        disclaimerAccepted = if (onboarding) accepted else current.disclaimerAccepted,
        onboardingComplete = current.onboardingComplete || onboarding,
    ).validated()

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("HH:mm")

        fun from(settings: Settings, plan: List<WeekPlanEntry>) = SettingsDraft(
            plan.sortedBy { it.dayOfWeek.value }.map { PlanDraft(it.dayOfWeek, it.sessionType, it.reminderTime.format(formatter)) },
            settings.checkTime.format(formatter), settings.workDays,
            settings.workStart.format(formatter), settings.workEnd.format(formatter),
            settings.breakIntervalMin, settings.notificationsEnabled, settings.disclaimerAccepted,
        )

        fun parseTime(value: String): LocalTime {
            require(value.matches(Regex("[0-9]{2}:[0-9]{2}")))
            return requireNotNull(runCatching { LocalTime.parse(value) }.getOrNull())
        }
    }
}

data class SettingsUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val persisted: Settings? = null,
    val draft: SettingsDraft? = null,
    val saving: Boolean = false,
    val invalid: Boolean = false,
    val saveError: Boolean = false,
    val saved: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val exactAllowed: Boolean = false,
    val schedulingError: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val program: ProgramRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {
    private val state = MutableStateFlow(SettingsUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null

    init { retry(); refreshPermissions() }

    fun retry() {
        observation?.cancel()
        state.update { it.copy(loading = true, loadError = false) }
        observation = viewModelScope.launch {
            try {
                program.initialize()
                combine(settings.settings, program.observePlan()) { preferences, plan -> preferences to plan }
                    .collect { (preferences, plan) ->
                        if (preferences != null && plan.size == 7) {
                            state.update { it.copy(loading = false, loadError = false, persisted = preferences,
                                draft = it.draft ?: SettingsDraft.from(preferences, plan)) }
                        } else {
                            state.update { it.copy(loading = false, loadError = true) }
                        }
                    }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(loading = false, loadError = true) } }
        }
    }

    fun refreshPermissions() {
        val before = state.value
        val notifications = scheduler.notificationsAllowed()
        val exact = scheduler.canScheduleExactAlarms()
        state.update { it.copy(notificationsAllowed = notifications, exactAllowed = exact) }
        if (before.persisted != null && (before.schedulingError || notifications != before.notificationsAllowed || exact != before.exactAllowed)) {
            viewModelScope.launch {
                try {
                    scheduler.reschedule()
                    state.update { it.copy(schedulingError = false) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { state.update { it.copy(schedulingError = true) } }
            }
        }
    }

    fun edit(transform: (SettingsDraft) -> SettingsDraft) {
        if (!state.value.saving) state.update { it.copy(draft = it.draft?.let(transform), invalid = false, saved = false) }
    }

    fun save(onboarding: Boolean) {
        val snapshot = state.value
        if (snapshot.saving || snapshot.loading) return
        val draft = snapshot.draft ?: return
        val persisted = snapshot.persisted ?: return
        val plan: List<WeekPlanEntry>
        try {
            plan = draft.validatedPlan()
            draft.applyTo(persisted, onboarding)
        } catch (_: IllegalArgumentException) {
            state.update { it.copy(invalid = true, saved = false) }
            return
        }
        state.update { it.copy(saving = true, invalid = false, saveError = false, saved = false) }
        viewModelScope.launch {
            try {
                program.setPlan(plan)
                settings.update { current -> draft.applyTo(current, onboarding) }
                scheduler.reschedule()
                state.update { it.copy(saving = false, saved = true, schedulingError = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(saving = false, saveError = true) } }
        }
    }
}