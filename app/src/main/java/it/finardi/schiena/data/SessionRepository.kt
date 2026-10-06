package it.finardi.schiena.data

import androidx.room.withTransaction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.domain.TrafficLightEngine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class ExercisePrescription(
    val exercise: Exercise,
    val sets: Int,
    val reps: Int?,
    val holdSec: Int?,
    val distanceM: Int?,
    val restSec: Int,
)

@Singleton
class SessionRepository @Inject constructor(private val database: AppDatabase) {
    fun observeSessions(start: LocalDate, end: LocalDate) = database.historyDao().observeSessions(start, end)
    fun observePainChecks() = database.historyDao().observePainChecks()
    fun observeOfficeBreaks() = database.historyDao().observeOfficeBreaks()

    suspend fun prescriptions(phaseId: Int, type: SessionType, minimal: Boolean): List<ExercisePrescription> =
        database.withTransaction {
            val exercises = database.programDao().getExercises().associateBy { it.id }
            if (minimal) {
                database.programDao().getMinimalSession().filter { it.enabled }.map {
                    ExercisePrescription(checkNotNull(exercises[it.exerciseId]), it.sets, it.reps, it.holdSec, it.distanceM, it.restSec)
                }
            } else {
                database.programDao().getPhaseExercises(phaseId, type).filter { it.enabled }.map {
                    ExercisePrescription(checkNotNull(exercises[it.exerciseId]), it.sets, it.reps, it.holdSec, it.distanceM, it.restSec)
                }
            }
        }

    suspend fun save(
        date: LocalDate,
        type: SessionType,
        phaseId: Int,
        outcome: SessionOutcome,
        painDuring: Int?,
        radiating: Boolean,
        durationMin: Int?,
        note: String?,
    ): Long {
        require(durationMin == null || durationMin in 1..1440)
        val skipped = outcome == SessionOutcome.SKIPPED
        val status = TrafficLightEngine.provisional(outcome, painDuring, radiating)
        return database.withTransaction {
            val previous = database.historyDao().getSession(date, type)
            val log = SessionLog(
                id = previous?.id ?: 0, date = date, sessionType = type, phaseId = phaseId,
                outcome = outcome, painDuring = if (skipped) null else painDuring,
                radiating = !skipped && radiating, durationMin = if (skipped) null else durationMin,
                note = note?.trim()?.takeIf { it.isNotEmpty() }, status = status,
            )
            if (previous == null) database.historyDao().insertSession(log)
            else {
                database.historyDao().deletePainCheck(previous.id)
                database.historyDao().upsertSession(log)
                previous.id
            }
        }
    }
}