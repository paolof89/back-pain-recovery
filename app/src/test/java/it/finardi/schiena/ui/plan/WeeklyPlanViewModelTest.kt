package it.finardi.schiena.ui.plan

import it.finardi.schiena.data.Phase
import it.finardi.schiena.data.ProgramState
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyPlanViewModelTest {
    private val phase = Phase(12, "Fase dal database", 3)
    private val programState = ProgramState(phase.id, LocalDate.of(2026, 10, 5), 4)
    private val plan = DayOfWeek.values().map { day ->
        WeekPlanEntry(day, SessionType.FREE, LocalTime.of(14, day.value))
    }

    @Test
    fun `complete plan is ordered Monday to Sunday without changing repository values`() {
        val snapshot = planSnapshot(plan.reversed(), programState, listOf(phase))

        assertFalse(snapshot.isLoading)
        assertFalse(snapshot.hasLoadError)
        assertEquals(plan, snapshot.plan)
        assertEquals(phase, snapshot.currentPhase)
    }

    @Test
    fun `current phase is selected by program state rather than phase list position`() {
        val otherPhase = Phase(1, "Altra fase", 4)
        val snapshot = planSnapshot(plan, programState, listOf(otherPhase, phase))

        assertEquals(phase, snapshot.currentPhase)
    }

    @Test
    fun `missing day is an actionable load error without invented defaults`() {
        val incompletePlan = plan.dropLast(1)
        val snapshot = planSnapshot(incompletePlan, programState, listOf(phase))

        assertTrue(snapshot.hasLoadError)
        assertFalse(snapshot.isLoading)
        assertEquals(incompletePlan, snapshot.plan)
    }

    @Test
    fun `duplicate day cannot masquerade as a seven day plan`() {
        val snapshot = planSnapshot(plan.dropLast(1) + plan.first(), programState, listOf(phase))

        assertTrue(snapshot.hasLoadError)
    }

    @Test
    fun `missing state or current phase is a load error`() {
        val missingState = planSnapshot(plan, null, listOf(phase))
        val missingPhase = planSnapshot(plan, programState, emptyList())

        assertTrue(missingState.hasLoadError)
        assertNull(missingState.currentPhase)
        assertTrue(missingPhase.hasLoadError)
        assertNull(missingPhase.currentPhase)
    }

    @Test
    fun `snapshot does not retain the repository mutable list`() {
        val repositoryPlan = plan.toMutableList()
        val snapshot = planSnapshot(repositoryPlan, programState, listOf(phase))
        repositoryPlan.clear()

        assertNotSame(repositoryPlan, snapshot.plan)
        assertEquals(plan, snapshot.plan)
    }
}