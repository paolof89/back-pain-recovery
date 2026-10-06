package it.finardi.schiena.ui.settings

import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class SettingsDraftTest {
    private val settings = Settings(LocalTime.of(8, 43), setOf(DayOfWeek.SUNDAY), LocalTime.of(10, 12), LocalTime.of(14, 37), 61)
    private val plan = DayOfWeek.entries.map { WeekPlanEntry(it, SessionType.FREE, LocalTime.of(20, it.value)) }
    private val draft = SettingsDraft.from(settings, plan)

    @Test
    fun defaultsComeFromPersistedDataAndRoundTripWithoutChanges() {
        assertEquals(plan, draft.validatedPlan())
        assertEquals(settings, draft.applyTo(settings, onboarding = false))
        assertEquals("08:43", draft.checkTime)
        assertEquals("20:07", draft.plan.last().time)
        assertEquals(61, draft.interval)
    }

    @Test
    fun timesRejectMalformedAndImpossibleValues() {
        listOf("", "9:00", "09:0", "24:00", "12:60", "09:00:00", "abc").forEach { value ->
            assertTrue(value, runCatching { draft.copy(checkTime = value).applyTo(settings, false) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun planRequiresExactlyOneEntryPerDay() {
        assertTrue(runCatching { draft.copy(plan = draft.plan.dropLast(1)).validatedPlan() }.isFailure)
        assertTrue(runCatching { draft.copy(plan = draft.plan.dropLast(1) + draft.plan.first()).validatedPlan() }.isFailure)
        assertTrue(runCatching { draft.copy(plan = draft.plan.map { it.copy(time = "25:00") }).validatedPlan() }.isFailure)
    }

    @Test
    fun onboardingRequiresAcceptanceAndPreservesUnrelatedCurrentSettings() {
        assertTrue(runCatching { draft.applyTo(settings, true) }.isFailure)
        val current = settings.copy(workOffToday = LocalDate.of(2026, 10, 6))
        val completed = draft.copy(accepted = true).applyTo(current, true)
        assertTrue(completed.onboardingComplete)
        assertTrue(completed.disclaimerAccepted)
        assertEquals(current.workOffToday, completed.workOffToday)
        assertEquals(completed, draft.applyTo(completed, false))
    }

    @Test
    fun workWindowAndIntervalValidateBeforeWrites() {
        listOf(draft.copy(workDays = emptySet()), draft.copy(workStart = draft.workEnd),
            draft.copy(interval = 59), draft.copy(interval = 91)).forEach { invalid ->
            assertTrue(runCatching { invalid.applyTo(settings, false) }.isFailure)
        }
        assertEquals(60, draft.copy(interval = 60).applyTo(settings, false).breakIntervalMin)
        assertEquals(90, draft.copy(interval = 90).applyTo(settings, false).breakIntervalMin)
    }
}