package it.finardi.schiena.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Exercise::class, Phase::class, PhaseExercise::class, WeekPlanEntry::class,
        SessionLog::class, PainCheck::class, OfficeBreakEvent::class, ProgramState::class,
        PhaseTransition::class, MinimalSessionExercise::class, ReturnToSport::class,
        OfficeBreakRoutine::class, ProgramMetadata::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    abstract fun historyDao(): HistoryDao
}