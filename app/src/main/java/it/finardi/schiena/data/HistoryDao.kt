package it.finardi.schiena.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import it.finardi.schiena.domain.SessionType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert suspend fun insertSession(value: SessionLog): Long
    @Upsert suspend fun upsertSession(value: SessionLog)
    @Upsert suspend fun upsertPainCheck(value: PainCheck)
    @Upsert suspend fun upsertOfficeBreak(value: OfficeBreakEvent)
    @Insert suspend fun insertTransition(value: PhaseTransition): Long

    @Query("SELECT * FROM SessionLog WHERE date BETWEEN :start AND :end ORDER BY date, id")
    fun observeSessions(start: LocalDate, end: LocalDate): Flow<List<SessionLog>>

    @Query("SELECT * FROM SessionLog ORDER BY date, id")
    suspend fun getSessions(): List<SessionLog>

    @Query("SELECT * FROM SessionLog WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionLog?

    @Query("SELECT * FROM SessionLog WHERE date = :date AND sessionType = :type ORDER BY id DESC LIMIT 1")
    suspend fun getSession(date: LocalDate, type: SessionType): SessionLog?

    @Query("DELETE FROM PainCheck WHERE sessionLogId = :sessionId")
    suspend fun deletePainCheck(sessionId: Long)

    @Query("SELECT * FROM PainCheck ORDER BY sessionLogId")
    fun observePainChecks(): Flow<List<PainCheck>>

    @Query("SELECT * FROM OfficeBreakEvent ORDER BY timestamp")
    fun observeOfficeBreaks(): Flow<List<OfficeBreakEvent>>

    @Query("SELECT * FROM PainCheck ORDER BY sessionLogId")
    suspend fun getPainChecks(): List<PainCheck>

    @Query("SELECT * FROM OfficeBreakEvent ORDER BY timestamp")
    suspend fun getOfficeBreaks(): List<OfficeBreakEvent>

    @Query("SELECT * FROM PhaseTransition ORDER BY date, id")
    suspend fun getTransitions(): List<PhaseTransition>
}