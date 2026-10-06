package it.finardi.schiena.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.finardi.schiena.data.Phase
import it.finardi.schiena.data.ProgramRepository
import it.finardi.schiena.data.ProgramState
import it.finardi.schiena.data.WeekPlanEntry
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklyPlanUiState(
    val isLoading: Boolean = true,
    val plan: List<WeekPlanEntry> = emptyList(),
    val currentPhase: Phase? = null,
    val hasLoadError: Boolean = false,
    val isRestoring: Boolean = false,
    val hasRestoreError: Boolean = false,
)

internal fun planSnapshot(
    plan: List<WeekPlanEntry>,
    state: ProgramState?,
    phases: List<Phase>,
): WeeklyPlanUiState {
    val phase = phases.firstOrNull { it.id == state?.currentPhaseId }
    val complete = plan.size == 7 && plan.map { it.dayOfWeek }.distinct().size == 7 && phase != null
    return WeeklyPlanUiState(
        isLoading = false,
        plan = plan.sortedBy { it.dayOfWeek.value },
        currentPhase = phase,
        hasLoadError = !complete,
    )
}

@HiltViewModel
class WeeklyPlanViewModel @Inject constructor(
    private val repository: ProgramRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(WeeklyPlanUiState())
    val uiState: StateFlow<WeeklyPlanUiState> = mutableUiState.asStateFlow()
    private var observation: Job? = null

    init {
        retry()
    }

    fun retry() {
        if (mutableUiState.value.isRestoring) return
        observation?.cancel()
        mutableUiState.update { it.copy(isLoading = true, hasLoadError = false) }
        observation = viewModelScope.launch {
            try {
                repository.initialize()
                combine(
                    repository.observePlan(),
                    repository.observeState(),
                    repository.observePhases(),
                    ::planSnapshot,
                ).collect { snapshot ->
                    mutableUiState.update {
                        snapshot.copy(
                            isRestoring = it.isRestoring,
                            hasRestoreError = it.hasRestoreError,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(isLoading = false, hasLoadError = true) }
            }
        }
    }

    fun restoreDefaults() {
        if (mutableUiState.value.isRestoring || mutableUiState.value.isLoading) return
        mutableUiState.update { it.copy(isRestoring = true, hasRestoreError = false) }
        viewModelScope.launch {
            var restored = false
            try {
                repository.restoreDefaults()
                restored = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.update { it.copy(hasRestoreError = true) }
            } finally {
                mutableUiState.update { it.copy(isRestoring = false) }
            }
            if (restored) retry()
        }
    }
}