package it.finardi.schiena.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Entity
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val goal: String,
    val cues: List<String>,
    val kind: ExerciseKind,
    val perSide: Boolean,
)

@Entity
data class Phase(
    @PrimaryKey val id: Int,
    val name: String,
    val minWeeks: Int,
    val description: String = "",
)

@Entity(
    primaryKeys = ["phaseId", "sessionType", "order"],
    foreignKeys = [
        ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["phaseId"]),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
    ],
    indices = [Index("exerciseId")],
)
data class PhaseExercise(
    val phaseId: Int,
    val sessionType: SessionType,
    val order: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val holdSec: Int? = null,
    val distanceM: Int? = null,
    val restSec: Int,
    val enabled: Boolean = true,
)

@Entity
data class WeekPlanEntry(
    @PrimaryKey val dayOfWeek: DayOfWeek,
    val sessionType: SessionType,
    val reminderTime: LocalTime,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["phaseId"])],
    indices = [Index("phaseId"), Index("date")],
)
data class SessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val sessionType: SessionType,
    val phaseId: Int,
    val outcome: SessionOutcome,
    val painDuring: Int? = null,
    val radiating: Boolean = false,
    val durationMin: Int? = null,
    val note: String? = null,
    val status: SessionStatus = SessionStatus.PENDING,
)

@Entity(foreignKeys = [
    ForeignKey(entity = SessionLog::class, parentColumns = ["id"], childColumns = ["sessionLogId"]),
])
data class PainCheck(
    @PrimaryKey val sessionLogId: Long,
    val timestamp: Instant,
    val painNow: Int,
    val backToBaseline: Boolean,
)

@Entity
data class OfficeBreakEvent(
    @PrimaryKey val timestamp: Instant,
    val action: OfficeBreakAction,
)

@Entity(
    foreignKeys = [ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["currentPhaseId"])],
    indices = [Index("currentPhaseId")],
)
data class ProgramState(
    val currentPhaseId: Int,
    val phaseStartDate: LocalDate,
    val weeklyTarget: Int,
    val lastGateProposalDate: LocalDate? = null,
    @PrimaryKey val id: Int = 1,
) {
    init {
        require(id == 1)
        require(weeklyTarget in 1..7)
    }
}

@Entity(
    foreignKeys = [
        ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["fromPhase"]),
        ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["toPhase"]),
    ],
    indices = [Index("fromPhase"), Index("toPhase"), Index("date")],
)
data class PhaseTransition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val fromPhase: Int,
    val toPhase: Int,
    val reason: String,
)

@Entity(foreignKeys = [
    ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"]),
], indices = [Index("exerciseId")])
data class MinimalSessionExercise(
    @PrimaryKey val order: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val holdSec: Int? = null,
    val distanceM: Int? = null,
    val restSec: Int,
    val enabled: Boolean = true,
)

@Entity(foreignKeys = [
    ForeignKey(entity = Phase::class, parentColumns = ["id"], childColumns = ["phaseId"]),
])
data class ReturnToSport(
    @PrimaryKey val phaseId: Int,
    val contentJson: String,
)

@Entity
data class OfficeBreakRoutine(@PrimaryKey val order: Int, val instruction: String)

@Entity
data class ProgramMetadata(@PrimaryKey val key: String, val value: String)