package it.finardi.schiena.ui.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.finardi.schiena.R
import it.finardi.schiena.data.ExercisePrescription
import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.PlaybackState
import it.finardi.schiena.domain.PlayerExercise
import it.finardi.schiena.domain.PlayerSide
import it.finardi.schiena.domain.PlayerStageKind
import it.finardi.schiena.domain.SessionPlayer
import kotlinx.coroutines.delay

@Composable
fun SessionPlayerScreen(
    exercises: List<ExercisePrescription>,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val engine = remember(exercises) {
        SessionPlayer(exercises.map { prescription ->
            PlayerExercise(prescription.exercise.kind, prescription.sets, prescription.reps,
                prescription.holdSec, prescription.distanceM, prescription.restSec,
                prescription.exercise.perSide)
        })
    }
    val playbackSaver = remember(engine) {
        listSaver<PlaybackState, Any>(
            save = { listOf(it.stageIndex, it.remainingMillis, it.paused, it.revision) },
            restore = { engine.restore(PlaybackState(it[0] as Int, it[1] as Long,
                it[2] as Boolean, it[3] as Long)) },
        )
    }
    var playback by rememberSaveable(stateSaver = playbackSaver) { mutableStateOf(engine.pause(engine.initialState())) }
    var preparing by rememberSaveable { mutableStateOf(true) }
    var otherActions by rememberSaveable { mutableStateOf(false) }
    var abandonDialog by rememberSaveable { mutableStateOf(false) }
    var completionDelivered by rememberSaveable { mutableStateOf(false) }
    var backDelivered by rememberSaveable { mutableStateOf(false) }
    val latestComplete by rememberUpdatedState(onComplete)
    val latestBack by rememberUpdatedState(onBack)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val lastTick = remember { longArrayOf(0L) }
    val tone = remember(context) {
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull()
    }
    val pausePlayback = {
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (lastTick[0] == 0L) 0L else (now - lastTick[0]).coerceAtLeast(0)
        playback = engine.pause(playback, elapsed)
        lastTick[0] = 0L
    }
    val updatePlayback: (PlaybackState) -> Unit = { updated ->
        val previousExercise = engine.stage(playback)?.exerciseIndex
        val nextExercise = engine.stage(updated)?.exerciseIndex
        if (nextExercise != null && nextExercise != previousExercise) {
            preparing = true
            otherActions = false
            playback = engine.pause(updated)
        } else playback = updated
        lastTick[0] = 0L
    }
    val requestBack = {
        if (!backDelivered && !completionDelivered) {
            pausePlayback()
            abandonDialog = true
        }
    }

    BackHandler { requestBack() }
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP ||
                event == Lifecycle.Event.ON_DESTROY) {
                foreground = false
                pausePlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pausePlayback()
        }
    }
    val window = context.playerActivity()?.window
    DisposableEffect(window, foreground) {
        val alreadyKeptOn = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            && window != null
        if (foreground) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (foreground && !alreadyKeptOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(tone) {
        onDispose { runCatching { tone?.release() } }
    }

    val currentStage = engine.stage(playback)
    val scrollState = rememberScrollState()
    LaunchedEffect(currentStage?.exerciseIndex, preparing) {
        if (preparing) scrollState.scrollTo(0)
    }
    LaunchedEffect(engine, playback.revision, playback.paused, preparing, foreground, abandonDialog, backDelivered) {
        if (!foreground || playback.paused || preparing || abandonDialog || backDelivered || currentStage?.durationMillis == null)
            return@LaunchedEffect
        val expectedRevision = playback.revision
        lastTick[0] = SystemClock.elapsedRealtime()
        while (true) {
            delay(100)
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                playback.paused || preparing || playback.revision != expectedRevision || abandonDialog || backDelivered)
                break
            val now = SystemClock.elapsedRealtime()
            val updated = engine.elapse(playback, (now - lastTick[0]).coerceAtLeast(0), expectedRevision)
            lastTick[0] = now
            if (updated.revision != expectedRevision) {
                updatePlayback(updated)
                signalTimerFinished(context, tone)
                break
            } else playback = updated
        }
    }
    LaunchedEffect(engine.isComplete(playback), foreground, abandonDialog, backDelivered) {
        if (exercises.isNotEmpty() && engine.isComplete(playback) && foreground && !abandonDialog &&
            !completionDelivered && !backDelivered) {
            completionDelivered = true
            latestComplete()
        }
    }

    Scaffold { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).verticalScroll(scrollState).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { requestBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_back))
                }
                Text(stringResource(R.string.player_title), style = MaterialTheme.typography.headlineSmall)
            }
            if (exercises.isEmpty()) {
                Text(stringResource(R.string.player_empty))
            } else if (currentStage == null) {
                Text(stringResource(R.string.player_complete), style = MaterialTheme.typography.headlineSmall)
            } else {
                val prescription = exercises[currentStage.exerciseIndex]
                val exercise = prescription.exercise
                Text(stringResource(R.string.player_exercise_progress, currentStage.exerciseIndex + 1, exercises.size))
                LinearProgressIndicator(
                    progress = { playback.stageIndex.toFloat() / engine.stages.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
                if (preparing) {
                    Text(stringResource(R.string.guided_prepare), style = MaterialTheme.typography.titleMedium)
                    Text(exercise.goal)
                    ExerciseInstructions(exercise)
                }
                Text(stringResource(R.string.player_set, currentStage.setNumber, prescription.sets),
                    style = MaterialTheme.typography.titleLarge)
                prescription.reps?.let { Text(stringResource(R.string.player_reps, it)) }
                prescription.holdSec?.let { Text(stringResource(R.string.player_hold, it)) }
                prescription.distanceM?.let { Text(stringResource(R.string.player_distance, it)) }
                if (preparing) {
                    Text(stringResource(R.string.player_rest_prescription, prescription.restSec))
                    if (exercise.perSide) Text(stringResource(R.string.guided_per_side))
                }
                if (currentStage.kind == PlayerStageKind.RECOVERY) {
                    Text(stringResource(R.string.player_recovery), style = MaterialTheme.typography.titleLarge)
                } else {
                    if (exercise.perSide) Text(stringResource(
                        if (currentStage.side == PlayerSide.LEFT) R.string.player_left else R.string.player_right,
                    ), style = MaterialTheme.typography.titleLarge)
                    if (exercise.kind == ExerciseKind.HOLD && prescription.reps != null) {
                        Text(stringResource(R.string.player_repetition, currentStage.repetition, prescription.reps))
                    }
                }
                if (!preparing && currentStage.durationMillis != null) {
                    Text(stringResource(R.string.player_seconds, (playback.remainingMillis + 999) / 1000),
                        style = MaterialTheme.typography.displayMedium)
                }
                if (playback.paused && !preparing) Text(stringResource(R.string.player_paused))
                Button(
                    onClick = {
                        if (preparing || playback.paused) {
                            preparing = false
                            lastTick[0] = 0L
                            playback = engine.resume(playback)
                        } else if (currentStage.durationMillis == null) updatePlayback(engine.next(playback))
                        else pausePlayback()
                    },
                    enabled = foreground && !backDelivered && !completionDelivered,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(when {
                        preparing -> R.string.guided_ready
                        playback.paused -> R.string.player_resume
                        currentStage.durationMillis == null -> R.string.guided_set_done
                        else -> R.string.player_pause
                    }))
                }
                if (!preparing) TextButton(onClick = { pausePlayback(); preparing = true },
                    enabled = foreground && !backDelivered && !completionDelivered) {
                    Text(stringResource(R.string.guided_instructions))
                }
                TextButton(onClick = { otherActions = !otherActions }) {
                    Text(stringResource(R.string.guided_other_actions))
                }
                if (otherActions) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(
                            onClick = { updatePlayback(engine.previous(playback)) },
                            enabled = foreground && playback.stageIndex > 0 && !backDelivered && !completionDelivered,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.player_previous)) }
                        IconButton(
                            onClick = { updatePlayback(engine.next(playback)) },
                            enabled = foreground && !preparing && !backDelivered && !completionDelivered,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.player_next)) }
                    }
                    OutlinedButton(
                        onClick = { updatePlayback(engine.skipExercise(playback)) },
                        enabled = foreground && !backDelivered && !completionDelivered,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.player_skip)) }
                }
            }
        }
    }
    if (abandonDialog) {
        AlertDialog(
            onDismissRequest = { abandonDialog = false },
            title = { Text(stringResource(R.string.player_abandon_title)) },
            text = { Text(stringResource(R.string.player_abandon_message)) },
            confirmButton = {
                TextButton(onClick = {
                    if (!backDelivered) {
                        backDelivered = true
                        abandonDialog = false
                        latestBack()
                    }
                }) { Text(stringResource(R.string.player_abandon)) }
            },
            dismissButton = {
                TextButton(onClick = { abandonDialog = false }) { Text(stringResource(R.string.player_stay)) }
            },
        )
    }
}

private fun Context.playerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.playerActivity()
    else -> null
}

private fun signalTimerFinished(context: Context, tone: ToneGenerator?) {
    runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
    runCatching {
        if (context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
            context.getSystemService(Vibrator::class.java)?.let { vibrator ->
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
    }
}