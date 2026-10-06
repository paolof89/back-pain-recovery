package it.finardi.schiena.data

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SettingsRepositoryTest : ProgramDataFixture() {
    @Test
    fun onboardingFlagsDefaultFalseForInitializedLegacyPreferences() = runTest {
        repository.initialize()
        val current = settingsRepository.settings.first()!!
        assertFalse(current.disclaimerAccepted)
        assertFalse(current.onboardingComplete)
    }

    @Test
    fun onboardingRequiresExplicitAcceptanceAndPersistsAcrossRepositoryRecreation() = runTest {
        repository.initialize()
        val before = settingsRepository.settings.first()
        assertTrue(runCatching { settingsRepository.update { it.copy(onboardingComplete = true) } }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, settingsRepository.settings.first())
        settingsRepository.update { it.copy(disclaimerAccepted = true) }
        assertFalse(settingsRepository.settings.first()!!.onboardingComplete)
        settingsRepository.update { it.copy(onboardingComplete = true) }
        val recreated = SettingsRepository(store, clock)
        assertTrue(recreated.settings.first()!!.disclaimerAccepted)
        assertTrue(recreated.settings.first()!!.onboardingComplete)
        settingsRepository.setWorkOffToday(true)
        repository.initialize()
        repository.restoreDefaults()
        assertTrue(recreated.settings.first()!!.onboardingComplete)
        assertTrue(recreated.settings.first()!!.disclaimerAccepted)
    }

    @Test
    fun settingsAreAbsentUntilSeedDefaultsInitialize() = runTest {
        assertNull(settingsRepository.settings.first())
        assertTrue(runCatching { settingsRepository.update { it.copy(breakIntervalMin = 60) } }.exceptionOrNull() is IllegalStateException)
        repository.initialize()
        assertEquals(Settings.fromSeed(seed.defaults), settingsRepository.settings.first())
    }

    @Test
    fun firstInitializationFillsMissingKeysWithoutOverwritingExistingPreferences() = runTest {
        store.edit {
            it[stringPreferencesKey("pain_check_time")] = "08:45"
            it[intPreferencesKey("office_break_interval_min")] = 60
            it[booleanPreferencesKey("notifications_enabled")] = true
            it[stringPreferencesKey("work_off_date")] = LocalDate.now(clock).toString()
        }
        repository.initialize()
        val current = settingsRepository.settings.first()!!
        assertEquals(LocalTime.of(8, 45), current.checkTime)
        assertEquals(60, current.breakIntervalMin)
        assertTrue(current.notificationsEnabled)
        assertTrue(current.isWorkOffToday(LocalDate.now(clock)))
        assertEquals(Settings.fromSeed(seed.defaults).workDays, current.workDays)
    }

    @Test
    fun changedSeedDefaultsAndResetNeverOverwriteInitializedSettings() = runTest {
        repository.initialize()
        settingsRepository.update {
            it.copy(checkTime = LocalTime.of(7, 0), workDays = setOf(DayOfWeek.SATURDAY),
                workStart = LocalTime.of(10, 0), workEnd = LocalTime.of(14, 0), breakIntervalMin = 90,
                notificationsEnabled = true)
        }
        settingsRepository.setWorkOffToday(true)
        val before = settingsRepository.settings.first()
        val changed = seed.defaults.copy(painCheckTime = "12:00",
            officeBreak = seed.defaults.officeBreak.copy(intervalMin = 65))
        settingsRepository.initializeDefaults(changed)
        seed = seed.copy(defaults = changed)
        repository.initialize()
        repository.restoreDefaults()
        assertEquals(before, settingsRepository.settings.first())
    }

    @Test
    fun invalidUpdatesFailAtomicallyAndBoundaryIntervalsAreAccepted() = runTest {
        repository.initialize()
        val before = settingsRepository.settings.first()
        val invalid: List<(Settings) -> Settings> = listOf(
            { it.copy(breakIntervalMin = 59) },
            { it.copy(breakIntervalMin = 91) },
            { it.copy(workStart = it.workEnd) },
            { it.copy(workStart = LocalTime.of(20, 0), workEnd = LocalTime.of(9, 0)) },
            { it.copy(workDays = emptySet()) },
        )
        invalid.forEach { transform ->
            assertTrue(runCatching { settingsRepository.update(transform) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, settingsRepository.settings.first())
        }
        settingsRepository.update { it.copy(breakIntervalMin = 60) }
        assertEquals(60, settingsRepository.settings.first()!!.breakIntervalMin)
        settingsRepository.update { it.copy(breakIntervalMin = 90) }
        assertEquals(90, settingsRepository.settings.first()!!.breakIntervalMin)
    }

    @Test
    fun workOffTodayExpiresOnNextLocalDateWithoutRewritingPreferences() = runTest {
        repository.initialize()
        settingsRepository.setWorkOffToday(true)
        assertTrue(settingsRepository.isWorkOffToday())
        val tomorrow = SettingsRepository(store, Clock.offset(clock, Duration.ofDays(1)))
        assertFalse(tomorrow.isWorkOffToday())
        assertEquals(LocalDate.now(clock), tomorrow.settings.first()!!.workOffToday)
        settingsRepository.setWorkOffToday(false)
        assertFalse(settingsRepository.isWorkOffToday())
        assertNull(settingsRepository.settings.first()!!.workOffToday)
    }

    @Test
    fun concurrentUpdatesDoNotLoseIndependentChanges() = runTest {
        repository.initialize()
        listOf(
            async { settingsRepository.update { it.copy(checkTime = LocalTime.of(8, 0)) } },
            async { settingsRepository.update { it.copy(notificationsEnabled = true) } },
            async { settingsRepository.update { it.copy(breakIntervalMin = 60) } },
        ).awaitAll()
        val current = settingsRepository.settings.first()!!
        assertEquals(LocalTime.of(8, 0), current.checkTime)
        assertTrue(current.notificationsEnabled)
        assertEquals(60, current.breakIntervalMin)
    }
}