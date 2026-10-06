package it.finardi.schiena.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

private val Context.schienaSettings: DataStore<Preferences> by preferencesDataStore(name = "schiena_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "schiena.db").build()

    @Provides fun provideProgramDao(database: AppDatabase): ProgramDao = database.programDao()
    @Provides fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()
    @Provides @Singleton fun provideClock(): Clock = Clock.systemDefaultZone()
    @Provides @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.schienaSettings
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedSourceModule {
    @Binds abstract fun bindSeedSource(source: AssetSeedSource): SeedSource
}