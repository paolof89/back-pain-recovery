package it.finardi.schiena.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment

abstract class ProgramDataFixture {
    @get:Rule val temporaryFolder = TemporaryFolder()
    protected lateinit var database: AppDatabase
    protected lateinit var seed: ProgramSeed
    protected lateinit var repository: ProgramRepository
    protected lateinit var settingsRepository: SettingsRepository
    protected lateinit var store: DataStore<Preferences>
    protected lateinit var importer: SeedImporter
    protected val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T23:30:00Z"), ZoneId.of("Europe/Rome"))
    private lateinit var storeJob: kotlinx.coroutines.Job

    @Before
    fun setUpData() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        seed = AssetSeedSource(context).load()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        storeJob = SupervisorJob()
        store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(storeJob + Dispatchers.IO),
            produceFile = { temporaryFolder.root.resolve("settings.preferences_pb") },
        )
        settingsRepository = SettingsRepository(store, clock)
        importer = SeedImporter(database, clock)
        repository = newRepository()
    }

    protected fun newRepository(): ProgramRepository = ProgramRepository(
        database, SeedSource { seed }, SeedImporter(database, clock), settingsRepository,
    )

    @After
    fun tearDownData() = runBlocking {
        if (::storeJob.isInitialized) storeJob.cancelAndJoin()
        if (::database.isInitialized) database.close()
    }
}