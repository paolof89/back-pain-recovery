package it.finardi.schiena.notif

import it.finardi.schiena.data.AppDatabase
import it.finardi.schiena.data.ProgramRepository
import it.finardi.schiena.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class NotificationCoordinator @Inject constructor(
    private val program: ProgramRepository,
    private val settings: SettingsRepository,
    private val database: AppDatabase,
    private val scheduler: AlarmScheduler,
    private val debug: DebugStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                try {
                    program.initialize()
                    combine(
                        settings.settings,
                        program.observePlan(),
                        database.invalidationTracker.createFlow("SessionLog", "PainCheck", "OfficeBreakEvent", "ProgramState"),
                    ) { _, _, _ -> Unit }.collect { scheduler.reschedule() }
                    return@launch
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    runCatching { debug.event("coordinator retry: ${error.javaClass.simpleName}: ${error.message}") }
                    delay(1_000)
                }
            }
        }
    }
}