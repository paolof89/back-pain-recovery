package it.finardi.schiena.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.finardi.schiena.notif.DebugStore
import it.finardi.schiena.notif.NotificationFactory
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DebugUiState(
    val loading: Boolean = true,
    val alarms: List<Pair<String, String>> = emptyList(),
    val events: List<String> = emptyList(),
    val lastCrash: String? = null,
    val error: Boolean = false,
    val sent: Boolean = false,
)

@HiltViewModel
class DebugViewModel @Inject constructor(private val store: DebugStore, private val notifications: NotificationFactory) : ViewModel() {
    private val state = MutableStateFlow(DebugUiState())
    val uiState = state.asStateFlow()
    private var refreshJob: Job? = null

    init { refresh() }

    fun refresh(test: Boolean = false) {
        if (refreshJob?.isActive == true) return
        state.value = state.value.copy(loading = true, error = false, sent = false)
        refreshJob = viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    if (test) notifications.sendTest()
                    store.snapshot()
                }
                state.value = DebugUiState(loading = false, alarms = snapshot.alarms.map { it.kind to it.trigger },
                    events = snapshot.events.takeLast(50), lastCrash = snapshot.lastCrash, sent = test)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, error = true) }
        }
    }
}