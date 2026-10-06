package it.finardi.schiena.domain

data class PlayerExercise(
    val kind: ExerciseKind,
    val sets: Int,
    val reps: Int?,
    val holdSec: Int?,
    val distanceM: Int?,
    val restSec: Int,
    val perSide: Boolean,
) {
    init {
        require(sets > 0 && restSec >= 0)
        require(reps == null || reps > 0)
        require(holdSec == null || holdSec > 0)
        require(distanceM == null || distanceM > 0)
        require(kind != ExerciseKind.HOLD || holdSec != null)
        require(kind != ExerciseKind.DISTANCE || distanceM != null)
    }
}

enum class PlayerStageKind { WORK, RECOVERY }
enum class PlayerSide { BOTH, LEFT, RIGHT }

data class PlayerStage(
    val exerciseIndex: Int,
    val setNumber: Int,
    val repetition: Int,
    val side: PlayerSide,
    val kind: PlayerStageKind,
    val durationMillis: Long?,
)

data class PlaybackState(
    val stageIndex: Int,
    val remainingMillis: Long,
    val paused: Boolean = false,
    val revision: Long = 0,
)

class SessionPlayer(exercises: List<PlayerExercise>) {
    val stages: List<PlayerStage> = buildList {
        exercises.forEachIndexed { exerciseIndex, exercise ->
            val sides = if (exercise.perSide) listOf(PlayerSide.LEFT, PlayerSide.RIGHT)
                else listOf(PlayerSide.BOTH)
            val repetitions = if (exercise.kind == ExerciseKind.HOLD) exercise.reps ?: 1 else 1
            for (setNumber in 1..exercise.sets) {
                sides.forEach { side ->
                    for (repetition in 1..repetitions) {
                        add(PlayerStage(exerciseIndex, setNumber, repetition, side,
                            PlayerStageKind.WORK,
                            if (exercise.kind == ExerciseKind.HOLD) exercise.holdSec!!.toLong() * 1000 else null))
                    }
                }
                if (setNumber < exercise.sets && exercise.restSec > 0) {
                    add(PlayerStage(exerciseIndex, setNumber, 1, PlayerSide.BOTH,
                        PlayerStageKind.RECOVERY, exercise.restSec.toLong() * 1000))
                }
            }
        }
    }

    fun initialState(): PlaybackState = stateAt(0, false, 0)

    fun stage(state: PlaybackState): PlayerStage? = stages.getOrNull(state.stageIndex)

    fun isComplete(state: PlaybackState): Boolean = state.stageIndex >= stages.size

    fun next(state: PlaybackState): PlaybackState =
        if (isComplete(state)) state else stateAt(state.stageIndex + 1, state.paused, state.revision + 1)

    fun previous(state: PlaybackState): PlaybackState =
        stateAt((state.stageIndex - 1).coerceAtLeast(0), state.paused, state.revision + 1)

    fun skipExercise(state: PlaybackState): PlaybackState {
        val current = stage(state) ?: return state
        val nextIndex = stages.indexOfFirst { it.exerciseIndex > current.exerciseIndex }
        return stateAt(if (nextIndex < 0) stages.size else nextIndex, state.paused, state.revision + 1)
    }

    fun elapse(state: PlaybackState, elapsedMillis: Long, expectedRevision: Long): PlaybackState {
        require(elapsedMillis >= 0)
        if (state.paused || state.revision != expectedRevision || stage(state)?.durationMillis == null) return state
        return if (elapsedMillis >= state.remainingMillis) next(state)
            else state.copy(remainingMillis = state.remainingMillis - elapsedMillis)
    }

    fun pause(state: PlaybackState, elapsedMillis: Long = 0): PlaybackState {
        require(elapsedMillis >= 0)
        if (state.paused) return state
        val remaining = if (stage(state)?.durationMillis != null)
            (state.remainingMillis - elapsedMillis).coerceAtLeast(1) else state.remainingMillis
        return state.copy(remainingMillis = remaining, paused = true, revision = state.revision + 1)
    }

    fun resume(state: PlaybackState): PlaybackState =
        if (!state.paused || isComplete(state)) state
        else state.copy(paused = false, revision = state.revision + 1)

    fun restore(saved: PlaybackState): PlaybackState {
        val restored = stateAt(saved.stageIndex.coerceIn(0, stages.size), true, saved.revision + 1)
        return restored.copy(remainingMillis = if (restored.remainingMillis > 0)
            saved.remainingMillis.coerceIn(1, restored.remainingMillis) else 0)
    }

    private fun stateAt(index: Int, paused: Boolean, revision: Long) =
        PlaybackState(index, stages.getOrNull(index)?.durationMillis ?: 0, paused, revision)
}