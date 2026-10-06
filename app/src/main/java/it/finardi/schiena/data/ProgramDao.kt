package it.finardi.schiena.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import it.finardi.schiena.domain.SessionType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Upsert suspend fun upsertExercises(values: List<Exercise>)
    @Upsert suspend fun upsertPhases(values: List<Phase>)
    @Upsert suspend fun upsertPhaseExercises(values: List<PhaseExercise>)
    @Upsert suspend fun upsertPlan(values: List<WeekPlanEntry>)
    @Upsert suspend fun upsertPlanEntry(value: WeekPlanEntry)
    @Upsert suspend fun upsertState(value: ProgramState)
    @Upsert suspend fun upsertMinimalSession(values: List<MinimalSessionExercise>)
    @Upsert suspend fun upsertReturnToSport(values: List<ReturnToSport>)
    @Upsert suspend fun upsertOfficeRoutine(values: List<OfficeBreakRoutine>)
    @Upsert suspend fun upsertMetadata(value: ProgramMetadata)

    @Query("SELECT * FROM WeekPlanEntry ORDER BY dayOfWeek")
    fun observePlan(): Flow<List<WeekPlanEntry>>

    @Query("SELECT * FROM ProgramState WHERE id = 1")
    fun observeState(): Flow<ProgramState?>

    @Query("SELECT * FROM Phase ORDER BY id")
    fun observePhases(): Flow<List<Phase>>

    @Query("SELECT * FROM ProgramState WHERE id = 1")
    suspend fun getState(): ProgramState?

    @Query("SELECT * FROM Exercise ORDER BY id")
    suspend fun getExercises(): List<Exercise>

    @Query("SELECT * FROM Phase ORDER BY id")
    suspend fun getPhases(): List<Phase>

    @Query("SELECT * FROM PhaseExercise WHERE phaseId = :phaseId AND sessionType = :sessionType ORDER BY `order`")
    suspend fun getPhaseExercises(phaseId: Int, sessionType: SessionType): List<PhaseExercise>

    @Query("SELECT * FROM MinimalSessionExercise ORDER BY `order`")
    suspend fun getMinimalSession(): List<MinimalSessionExercise>

    @Query("SELECT * FROM ReturnToSport ORDER BY phaseId")
    suspend fun getReturnToSport(): List<ReturnToSport>

    @Query("SELECT * FROM OfficeBreakRoutine ORDER BY `order`")
    suspend fun getOfficeRoutine(): List<OfficeBreakRoutine>

    @Query("SELECT value FROM ProgramMetadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): String?

    @Query("DELETE FROM PhaseExercise") suspend fun clearPhaseExercises()
    @Query("DELETE FROM MinimalSessionExercise") suspend fun clearMinimalSession()
    @Query("DELETE FROM ReturnToSport") suspend fun clearReturnToSport()
    @Query("DELETE FROM OfficeBreakRoutine") suspend fun clearOfficeRoutine()
    @Query("DELETE FROM WeekPlanEntry") suspend fun clearPlan()

    @Query("DELETE FROM Exercise WHERE id NOT IN (:seedIds)")
    suspend fun removeNonSeedExercises(seedIds: List<String>)

    @Query("""
        DELETE FROM Phase WHERE id NOT IN (:seedIds)
        AND id NOT IN (SELECT phaseId FROM SessionLog)
        AND id NOT IN (SELECT currentPhaseId FROM ProgramState)
        AND id NOT IN (SELECT fromPhase FROM PhaseTransition)
        AND id NOT IN (SELECT toPhase FROM PhaseTransition)
    """)
    suspend fun removeUnreferencedNonSeedPhases(seedIds: List<Int>)
}