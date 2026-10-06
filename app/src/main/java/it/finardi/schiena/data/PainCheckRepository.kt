package it.finardi.schiena.data

import androidx.room.withTransaction
import it.finardi.schiena.domain.PainCheckWindow
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.TrafficLightEngine
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PainCheckRepository @Inject constructor(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun pending(): List<SessionLog> {
        val settings = settingsRepository.settings.first() ?: return emptyList()
        return database.withTransaction {
            val checked = database.historyDao().getPainChecks().map { it.sessionLogId }.toSet()
            database.historyDao().getSessions().filter {
                PainCheckWindow.required(it.sessionType, it.outcome) && it.id !in checked &&
                    PainCheckWindow.pending(it.date, settings.checkTime, clock.zone, clock.instant())
            }
        }
    }

    suspend fun complete(sessionId: Long, painNow: Int, backToBaseline: Boolean): Boolean {
        require(painNow in 0..10)
        val settings = settingsRepository.settings.first() ?: return false
        return database.withTransaction {
            val dao = database.historyDao()
            val log = dao.getSessionById(sessionId) ?: return@withTransaction false
            if (!PainCheckWindow.required(log.sessionType, log.outcome) ||
                !PainCheckWindow.pending(log.date, settings.checkTime, clock.zone, clock.instant()) ||
                dao.getPainChecks().any { it.sessionLogId == sessionId }) return@withTransaction false
            val provisional = TrafficLightEngine.provisional(log.outcome, log.painDuring, log.radiating)
            dao.upsertPainCheck(PainCheck(sessionId, clock.instant(), painNow, backToBaseline))
            dao.upsertSession(log.copy(status = TrafficLightEngine.verified(provisional, backToBaseline, expired = false)))
            true
        }
    }

    suspend fun expire() {
        val settings = settingsRepository.settings.first() ?: return
        database.withTransaction {
            val dao = database.historyDao()
            val checked = dao.getPainChecks().map { it.sessionLogId }.toSet()
            dao.getSessions().filter {
                PainCheckWindow.required(it.sessionType, it.outcome) && it.id !in checked &&
                    clock.instant() >= PainCheckWindow.expires(it.date, settings.checkTime, clock.zone) &&
                    it.status == SessionStatus.PENDING
            }.forEach { dao.upsertSession(it.copy(status = SessionStatus.UNVERIFIED)) }
        }
    }
}