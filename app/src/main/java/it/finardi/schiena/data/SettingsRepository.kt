package it.finardi.schiena.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val checkTime: LocalTime,
    val workDays: Set<DayOfWeek>,
    val workStart: LocalTime,
    val workEnd: LocalTime,
    val breakIntervalMin: Int,
    val notificationsEnabled: Boolean = false,
    val workOffToday: LocalDate? = null,
    val onboardingComplete: Boolean = false,
    val disclaimerAccepted: Boolean = false,
) {
    fun isWorkOffToday(today: LocalDate): Boolean = workOffToday == today

    fun validated(): Settings {
        require(!onboardingComplete || disclaimerAccepted) { "Disclaimer acceptance is required" }
        require(workDays.isNotEmpty()) { "At least one work day is required" }
        require(workStart < workEnd) { "Work start must precede work end" }
        require(breakIntervalMin in 60..90) { "Break interval must be between 60 and 90 minutes" }
        return this
    }

    companion object {
        fun fromSeed(defaults: SeedDefaults): Settings = Settings(
            checkTime = LocalTime.parse(defaults.painCheckTime),
            workDays = defaults.officeBreak.days.map(DayOfWeek::valueOf).toSet(),
            workStart = LocalTime.parse(defaults.officeBreak.start),
            workEnd = LocalTime.parse(defaults.officeBreak.end),
            breakIntervalMin = defaults.officeBreak.intervalMin,
        ).validated()
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val clock: Clock,
) {
    private object Keys {
        val initialized = booleanPreferencesKey("seed_defaults_initialized")
        val checkTime = stringPreferencesKey("pain_check_time")
        val workDays = stringSetPreferencesKey("work_days")
        val workStart = stringPreferencesKey("work_start")
        val workEnd = stringPreferencesKey("work_end")
        val interval = intPreferencesKey("office_break_interval_min")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val workOffDate = stringPreferencesKey("work_off_date")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val disclaimer = booleanPreferencesKey("disclaimer_accepted")
    }

    val settings: Flow<Settings?> = store.data.map { preferences ->
        if (preferences[Keys.initialized] == true) decode(preferences) else null
    }

    suspend fun initializeDefaults(defaults: SeedDefaults) {
        val initial = Settings.fromSeed(defaults)
        store.edit { preferences ->
            if (preferences[Keys.initialized] != true) {
                if (!preferences.contains(Keys.checkTime)) preferences[Keys.checkTime] = initial.checkTime.toString()
                if (!preferences.contains(Keys.workDays)) preferences[Keys.workDays] = initial.workDays.map { it.name }.toSet()
                if (!preferences.contains(Keys.workStart)) preferences[Keys.workStart] = initial.workStart.toString()
                if (!preferences.contains(Keys.workEnd)) preferences[Keys.workEnd] = initial.workEnd.toString()
                if (!preferences.contains(Keys.interval)) preferences[Keys.interval] = initial.breakIntervalMin
                if (!preferences.contains(Keys.notifications)) preferences[Keys.notifications] = initial.notificationsEnabled
                decode(preferences).validated()
                preferences[Keys.initialized] = true
            }
        }
    }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { preferences ->
            check(preferences[Keys.initialized] == true) { "Initialize the program before editing settings" }
            val next = transform(decode(preferences)).validated()
            preferences[Keys.checkTime] = next.checkTime.toString()
            preferences[Keys.workDays] = next.workDays.map { it.name }.toSet()
            preferences[Keys.workStart] = next.workStart.toString()
            preferences[Keys.workEnd] = next.workEnd.toString()
            preferences[Keys.interval] = next.breakIntervalMin
            preferences[Keys.notifications] = next.notificationsEnabled
            preferences[Keys.onboarding] = next.onboardingComplete
            preferences[Keys.disclaimer] = next.disclaimerAccepted
            if (next.workOffToday == null) preferences.remove(Keys.workOffDate)
            else preferences[Keys.workOffDate] = next.workOffToday.toString()
        }
    }

    suspend fun setWorkOffToday(enabled: Boolean) {
        update { it.copy(workOffToday = if (enabled) LocalDate.now(clock) else null) }
    }

    suspend fun isWorkOffToday(): Boolean = settings.first()?.isWorkOffToday(LocalDate.now(clock)) == true

    private fun decode(preferences: Preferences): Settings = Settings(
        checkTime = LocalTime.parse(checkNotNull(preferences[Keys.checkTime])),
        workDays = checkNotNull(preferences[Keys.workDays]).map(DayOfWeek::valueOf).toSet(),
        workStart = LocalTime.parse(checkNotNull(preferences[Keys.workStart])),
        workEnd = LocalTime.parse(checkNotNull(preferences[Keys.workEnd])),
        breakIntervalMin = checkNotNull(preferences[Keys.interval]),
        notificationsEnabled = preferences[Keys.notifications] ?: false,
        workOffToday = preferences[Keys.workOffDate]?.let(LocalDate::parse),
        onboardingComplete = preferences[Keys.onboarding] ?: false,
        disclaimerAccepted = preferences[Keys.disclaimer] ?: false,
    )
}