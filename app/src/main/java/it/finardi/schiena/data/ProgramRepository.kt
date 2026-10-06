package it.finardi.schiena.data

import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ProgramRepository @Inject constructor(
    private val database: AppDatabase,
    private val seedSource: SeedSource,
    private val importer: SeedImporter,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun initialize() {
        val seed = seedSource.load()
        Settings.fromSeed(seed.defaults)
        importer.initialize(seed)
        settingsRepository.initializeDefaults(seed.defaults)
    }

    suspend fun restoreDefaults() {
        importer.restoreDefaults(seedSource.load())
    }

    fun observePlan(): Flow<List<WeekPlanEntry>> = database.programDao().observePlan()
    fun observeState(): Flow<ProgramState?> = database.programDao().observeState()
    fun observePhases(): Flow<List<Phase>> = database.programDao().observePhases()

    suspend fun setPlanEntry(entry: WeekPlanEntry) {
        database.programDao().upsertPlanEntry(entry)
    }

    suspend fun setPlanEntry(dayOfWeek: DayOfWeek, sessionType: SessionType, reminderTime: LocalTime) {
        setPlanEntry(WeekPlanEntry(dayOfWeek, sessionType, reminderTime))
    }
}