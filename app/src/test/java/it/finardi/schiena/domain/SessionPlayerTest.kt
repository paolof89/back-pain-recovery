package it.finardi.schiena.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlayerTest {
    private fun exercise(
        kind: ExerciseKind = ExerciseKind.HOLD,
        sets: Int = 1,
        reps: Int? = null,
        holdSec: Int? = if (kind == ExerciseKind.HOLD) 8 else null,
        distanceM: Int? = if (kind == ExerciseKind.DISTANCE) 20 else null,
        restSec: Int = 0,
        perSide: Boolean = false,
    ) = PlayerExercise(kind, sets, reps, holdSec, distanceM, restSec, perSide)

    @Test
    fun birdDogRunsSixHoldsOnEachSideBeforeDatabaseRest() {
        val player = SessionPlayer(listOf(exercise(sets = 3, reps = 6, restSec = 45, perSide = true)))
        assertEquals(38, player.stages.size)
        val firstSet = player.stages.take(12)
        assertEquals(List(6) { PlayerSide.LEFT } + List(6) { PlayerSide.RIGHT }, firstSet.map { it.side })
        assertEquals((1..6).toList() + (1..6).toList(), firstSet.map { it.repetition })
        assertTrue(firstSet.all { it.setNumber == 1 && it.durationMillis == 8000L })
        assertEquals(PlayerStageKind.RECOVERY, player.stages[12].kind)
        assertEquals(45000L, player.stages[12].durationMillis)
        assertEquals(2, player.stages[13].setNumber)
        assertEquals(PlayerSide.LEFT, player.stages[13].side)
        assertEquals(PlayerStageKind.WORK, player.stages.last().kind)
    }

    @Test
    fun timerAdvancesAtExactBoundaryNotOneMillisecondEarly() {
        val player = SessionPlayer(listOf(exercise(reps = 2)))
        val initial = player.initialState()
        val almost = player.elapse(initial, 7999, initial.revision)
        assertEquals(0, almost.stageIndex)
        assertEquals(1L, almost.remainingMillis)
        val advanced = player.elapse(almost, 1, almost.revision)
        assertEquals(1, advanced.stageIndex)
        assertEquals(8000L, advanced.remainingMillis)
    }

    @Test
    fun duplicatedTimerEventCannotAdvanceTwice() {
        val player = SessionPlayer(listOf(exercise(reps = 3)))
        val initial = player.initialState()
        val advanced = player.elapse(initial, 8000, initial.revision)
        assertEquals(advanced, player.elapse(advanced, 8000, initial.revision))
    }

    @Test
    fun lateTickAdvancesOnlyOneStageWithoutConsumingNextHold() {
        val player = SessionPlayer(listOf(exercise(reps = 6, perSide = true)))
        val initial = player.initialState()
        val advanced = player.elapse(initial, 100000, initial.revision)
        assertEquals(1, advanced.stageIndex)
        assertEquals(8000L, advanced.remainingMillis)
    }

    @Test
    fun holdsWithoutRepsUseOneTimerPerSideAndSet() {
        val player = SessionPlayer(listOf(exercise(sets = 2, holdSec = 20, perSide = true)))
        assertEquals(4, player.stages.size)
        assertEquals(listOf(1, 1, 2, 2), player.stages.map { it.setNumber })
        assertTrue(player.stages.all { it.repetition == 1 && it.durationMillis == 20000L })
    }

    @Test
    fun recoveryAutomaticallyStartsNextSetAtBoundary() {
        val player = SessionPlayer(listOf(exercise(sets = 2, restSec = 20)))
        val initial = player.initialState()
        val resting = player.elapse(initial, 8000, initial.revision)
        assertEquals(PlayerStageKind.RECOVERY, player.stage(resting)?.kind)
        assertEquals(20000L, resting.remainingMillis)
        val almost = player.elapse(resting, 19999, resting.revision)
        assertEquals(PlayerStageKind.RECOVERY, player.stage(almost)?.kind)
        val nextSet = player.elapse(almost, 1, almost.revision)
        assertEquals(2, player.stage(nextSet)?.setNumber)
        assertEquals(PlayerStageKind.WORK, player.stage(nextSet)?.kind)
    }

    @Test
    fun noRecoveryBetweenSidesRepetitionsOrAfterLastSetOrExercise() {
        val player = SessionPlayer(listOf(
            exercise(reps = 2, restSec = 45, perSide = true),
            exercise(holdSec = 30),
        ))
        assertEquals(5, player.stages.size)
        assertTrue(player.stages.all { it.kind == PlayerStageKind.WORK })
        assertEquals(1, player.stages.last().exerciseIndex)
    }

    @Test
    fun repsDistanceAndMobilityRequireManualAdvancementIncludingSides() {
        listOf(ExerciseKind.REPS, ExerciseKind.DISTANCE, ExerciseKind.MOBILITY).forEach { kind ->
            val player = SessionPlayer(listOf(exercise(kind = kind, sets = 2, reps = 6, perSide = true)))
            val initial = player.initialState()
            assertEquals(4, player.stages.size)
            assertNull(player.stage(initial)?.durationMillis)
            assertEquals(initial, player.elapse(initial, 100000, initial.revision))
            val right = player.next(initial)
            assertEquals(PlayerSide.RIGHT, player.stage(right)?.side)
            assertEquals(2, player.stage(player.next(right))?.setNumber)
        }
    }

    @Test
    fun manualExerciseStillHasAutomaticRecovery() {
        val player = SessionPlayer(listOf(exercise(kind = ExerciseKind.REPS, sets = 2, reps = 12, restSec = 30)))
        val resting = player.next(player.initialState())
        assertEquals(30000L, resting.remainingMillis)
        val nextSet = player.elapse(resting, 30000, resting.revision)
        assertEquals(2, player.stage(nextSet)?.setNumber)
        assertNull(player.stage(nextSet)?.durationMillis)
    }

    @Test
    fun previousRestartsPriorStageAndClampsAtStart() {
        val player = SessionPlayer(listOf(exercise(reps = 2)))
        val initial = player.initialState()
        assertEquals(0, player.previous(initial).stageIndex)
        val second = player.next(initial)
        val partial = player.elapse(second, 3000, second.revision)
        val previous = player.previous(partial)
        assertEquals(0, previous.stageIndex)
        assertEquals(8000L, previous.remainingMillis)
        assertTrue(previous.revision > partial.revision)
    }

    @Test
    fun previousFromRecoveryReturnsFinalSideOfPriorSet() {
        val player = SessionPlayer(listOf(exercise(sets = 2, reps = 2, restSec = 45, perSide = true)))
        var state = player.initialState()
        repeat(4) { state = player.next(state) }
        assertEquals(PlayerStageKind.RECOVERY, player.stage(state)?.kind)
        val previous = player.stage(player.previous(state))!!
        assertEquals(PlayerSide.RIGHT, previous.side)
        assertEquals(2, previous.repetition)
        assertEquals(1, previous.setNumber)
    }

    @Test
    fun skipBypassesRemainingSidesSetsAndRecovery() {
        val player = SessionPlayer(listOf(
            exercise(sets = 3, reps = 6, restSec = 45, perSide = true),
            exercise(kind = ExerciseKind.REPS, reps = 12),
        ))
        val skipped = player.skipExercise(player.next(player.initialState()))
        assertEquals(1, player.stage(skipped)?.exerciseIndex)
        assertEquals(1, player.stage(skipped)?.setNumber)
        assertNull(player.stage(skipped)?.durationMillis)
    }

    @Test
    fun skipFromRecoveryBypassesNextSet() {
        val player = SessionPlayer(listOf(exercise(sets = 2, restSec = 45), exercise()))
        val skipped = player.skipExercise(player.next(player.initialState()))
        assertEquals(1, player.stage(skipped)?.exerciseIndex)
    }

    @Test
    fun skippingLastExerciseCompletesAndFurtherEventsAreNoOps() {
        val player = SessionPlayer(listOf(exercise(sets = 3, reps = 6, perSide = true)))
        val complete = player.skipExercise(player.initialState())
        assertTrue(player.isComplete(complete))
        assertNull(player.stage(complete))
        assertEquals(complete, player.next(complete))
        assertEquals(complete, player.skipExercise(complete))
        assertEquals(complete, player.elapse(complete, 8000, complete.revision))
        assertFalse(player.isComplete(player.previous(complete)))
    }

    @Test
    fun finalHoldAutomaticallyCompletes() {
        val player = SessionPlayer(listOf(exercise()))
        val initial = player.initialState()
        assertTrue(player.isComplete(player.elapse(initial, 8000, initial.revision)))
    }

    @Test
    fun emptySessionIsCompleteWithoutStages() {
        val player = SessionPlayer(emptyList())
        assertTrue(player.isComplete(player.initialState()))
        assertNull(player.stage(player.initialState()))
    }

    @Test
    fun pauseAccountsForPartialTickAndInvalidatesPendingEvents() {
        val player = SessionPlayer(listOf(exercise(reps = 2)))
        val initial = player.initialState()
        val paused = player.pause(initial, 1500)
        assertEquals(6500L, paused.remainingMillis)
        assertTrue(paused.paused)
        assertEquals(paused, player.elapse(paused, 100000, paused.revision))
        val resumed = player.resume(paused)
        assertFalse(resumed.paused)
        assertEquals(resumed, player.elapse(resumed, 8000, initial.revision))
        assertEquals(1, player.elapse(resumed, 6500, resumed.revision).stageIndex)
    }

    @Test
    fun lifecyclePauseNeverProgressesEvenWhenTimerIsDue() {
        val player = SessionPlayer(listOf(exercise(reps = 2)))
        val paused = player.pause(player.initialState(), 100000)
        assertEquals(0, paused.stageIndex)
        assertEquals(1L, paused.remainingMillis)
        assertTrue(paused.paused)
    }

    @Test
    fun manualNavigationPreservesPauseAndInvalidatesOldTimer() {
        val player = SessionPlayer(listOf(exercise(reps = 3)))
        val initial = player.initialState()
        val advanced = player.next(initial)
        assertEquals(advanced, player.elapse(advanced, 8000, initial.revision))
        val paused = player.pause(advanced)
        assertTrue(player.next(paused).paused)
        assertTrue(player.previous(paused).paused)
        assertTrue(player.skipExercise(paused).paused)
    }

    @Test
    fun restoredPlaybackPreservesPositionAndTimeButRequiresResume() {
        val player = SessionPlayer(listOf(exercise(reps = 6, perSide = true)))
        val saved = PlaybackState(7, 3456, false, 12)
        val restored = player.restore(saved)
        assertEquals(7, restored.stageIndex)
        assertEquals(3456L, restored.remainingMillis)
        assertTrue(restored.paused)
        assertEquals(PlayerSide.RIGHT, player.stage(restored)?.side)
        assertEquals(2, player.stage(restored)?.repetition)
        assertTrue(restored.revision > saved.revision)
    }

    @Test
    fun restoredValuesAreClampedToCurrentStage() {
        val player = SessionPlayer(listOf(exercise()))
        assertEquals(8000L, player.restore(PlaybackState(-1, 99999)).remainingMillis)
        assertEquals(1L, player.restore(PlaybackState(0, -100)).remainingMillis)
        assertTrue(player.isComplete(player.restore(PlaybackState(999, 4000))))
        val manual = SessionPlayer(listOf(exercise(kind = ExerciseKind.MOBILITY)))
        assertEquals(0L, manual.restore(PlaybackState(0, 4000)).remainingMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun holdWithoutDurationIsRejected() {
        exercise(holdSec = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeRecoveryIsRejected() {
        exercise(restSec = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSetsAreRejected() {
        exercise(sets = 0)
    }
}