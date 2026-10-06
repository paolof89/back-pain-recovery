package it.finardi.schiena.ui.paincheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.finardi.schiena.data.PainCheckRepository
import it.finardi.schiena.data.SessionLog
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PainCheckUiState(
    val loading: Boolean = true,
    val log: SessionLog? = null,
    val saving: Boolean = false,
    val stale: Boolean = false,
    val error: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class PainCheckViewModel @Inject constructor(private val repository: PainCheckRepository) : ViewModel() {
    private val state = MutableStateFlow(PainCheckUiState())
    val uiState = state.asStateFlow()
    private var request: Job? = null
    private var sessionId: Long? = null

    fun load(id: Long) {
        if (state.value.saving) return
        sessionId = id
        request?.cancel()
        state.value = PainCheckUiState()
        request = viewModelScope.launch {
            try {
                repository.expire()
                val log = repository.pending().firstOrNull { it.id == id }
                state.value = PainCheckUiState(loading = false, log = log, stale = log == null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = PainCheckUiState(loading = false, error = true) }
        }
    }

    fun retry() { sessionId?.let(::load) }

    fun save(pain: Int, baseline: Boolean) {
        val snapshot = state.value
        val log = snapshot.log ?: return
        if (snapshot.loading || snapshot.saving || snapshot.saved || snapshot.stale || pain !in 0..10) return
        state.update { it.copy(saving = true, error = false) }
        request = viewModelScope.launch {
            try {
                val completed = repository.complete(log.id, pain, baseline)
                state.update { it.copy(saving = false, saved = completed, stale = !completed) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(saving = false, error = true) } }
        }
    }
}