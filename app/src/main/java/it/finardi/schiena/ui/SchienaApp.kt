package it.finardi.schiena.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import it.finardi.schiena.R
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.HomeScreen
import it.finardi.schiena.ui.home.HomeViewModel
import it.finardi.schiena.ui.log.SessionLogScreen
import it.finardi.schiena.ui.plan.WeeklyPlanRoute
import it.finardi.schiena.ui.player.SessionPlayerScreen
import it.finardi.schiena.ui.redflags.RedFlagsScreen
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey
@Serializable data object Plan : NavKey
@Serializable data object Player : NavKey
@Serializable data object RedFlags : NavKey
@Serializable data class Log(val outcome: String) : NavKey

@Composable
fun SchienaApp(viewModel: HomeViewModel = hiltViewModel()) {
    val home by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    val back = { if (backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }; Unit }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session.saved) {
        if (session.saved) {
            backStack.clear()
            backStack.add(Home)
            viewModel.acknowledgeSave()
        }
    }
    NavDisplay(
        backStack = backStack,
        onBack = { if (!session.saving) back() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = { destination ->
            NavEntry(destination) {
                when (destination) {
                    Home -> HomeScreen(
                        state = home, onRetry = viewModel::retry,
                        onStart = {
                            viewModel.begin(false)
                            if (home.entry?.sessionType in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)) backStack.add(Player)
                            else backStack.add(Log(SessionOutcome.DONE.name))
                        },
                        onMinimal = { viewModel.begin(true); backStack.add(Player) },
                        onLog = { outcome -> viewModel.begin(outcome == SessionOutcome.MINIMAL, requiresPlayer = false); backStack.add(Log(outcome.name)) },
                        onPlan = { backStack.add(Plan) }, onRedFlags = { backStack.add(RedFlags) },
                        onWorkOff = viewModel::setWorkOff,
                    )
                    Plan -> Column(Modifier.fillMaxSize()) {
                        IconButton(onClick = back, modifier = Modifier.statusBarsPadding()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back))
                        }
                        Box(Modifier.weight(1f)) { WeeklyPlanRoute() }
                    }
                    RedFlags -> RedFlagsScreen(onBack = back)
                    Player, is Log -> {
                        when {
                            session.loading -> SessionLoading(false, viewModel::retrySession, back)
                            session.context == null || session.error -> SessionLoading(true, viewModel::retrySession, back)
                            destination == Player -> SessionPlayerScreen(
                                session.exercises,
                                onComplete = {
                                    if (backStack.lastOrNull() == Player) {
                                        backStack.removeAt(backStack.lastIndex)
                                        backStack.add(Log(if (session.context?.minimal == true) SessionOutcome.MINIMAL.name else SessionOutcome.DONE.name))
                                    }
                                }, onBack = back,
                            )
                            destination is Log -> SessionLogScreen(
                                state = session, initialOutcome = SessionOutcome.valueOf(destination.outcome),
                                onSave = viewModel::save, onBack = back,
                                onRedFlags = { backStack.add(RedFlags) },
                                onMinimalPlayer = {
                                    viewModel.beginMinimalFromSession()
                                    backStack.removeAt(backStack.lastIndex)
                                    backStack.add(Player)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SessionLoading(error: Boolean, onRetry: () -> Unit, onBack: () -> Unit) {
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.log_back)) }
            if (error) {
                Text(stringResource(R.string.session_load_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            } else { CircularProgressIndicator(); Text(stringResource(R.string.plan_loading)) }
        }
    }
}