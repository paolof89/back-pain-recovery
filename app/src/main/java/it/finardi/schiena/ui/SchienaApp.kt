package it.finardi.schiena.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import it.finardi.schiena.ui.home.DiaryScreen
import it.finardi.schiena.ui.home.HomeViewModel
import it.finardi.schiena.ui.log.SessionLogScreen
import it.finardi.schiena.ui.plan.WeeklyPlanRoute
import it.finardi.schiena.ui.player.SessionPlayerScreen
import it.finardi.schiena.ui.redflags.RedFlagsScreen
import it.finardi.schiena.ui.onboarding.OnboardingScreen
import it.finardi.schiena.ui.settings.SettingsScreen
import it.finardi.schiena.ui.settings.SettingsViewModel
import it.finardi.schiena.ui.paincheck.PainCheckScreen
import it.finardi.schiena.ui.paincheck.PainCheckViewModel
import it.finardi.schiena.ui.debug.DebugScreen
import it.finardi.schiena.ui.debug.DebugViewModel
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey
@Serializable data object Diary : NavKey
@Serializable data object Plan : NavKey
@Serializable data object Player : NavKey
@Serializable data object RedFlags : NavKey
@Serializable data class Log(val outcome: String) : NavKey
@Serializable data object AppSettings : NavKey
@Serializable data object Debug : NavKey
@Serializable data class CheckPain(val sessionId: Long) : NavKey

@Composable
fun SchienaApp(
    viewModel: HomeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    notificationRequest: NotificationRequest? = null,
    onNotificationConsumed: (String) -> Unit = {},
) {
    val home by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val preferences by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    var consumedToken by rememberSaveable { mutableStateOf<String?>(null) }
    var notificationError by rememberSaveable { mutableStateOf(false) }
    var onboardingRedFlags by rememberSaveable { mutableStateOf(false) }
    val ready = !home.loading && !home.error && !preferences.loading && !preferences.loadError && preferences.persisted != null
    val onboarded = preferences.persisted?.let { it.onboardingComplete && it.disclaimerAccepted } == true
    val back = { if (backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }; Unit }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, settingsViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDate()
                settingsViewModel.refreshPermissions()
            }
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
    LaunchedEffect(notificationRequest?.token, ready, onboarded, session.saving, preferences.saving) {
        val request = notificationRequest
        if (request != null && ready && onboarded && !session.saving && !preferences.saving) {
            if (consumedToken != request.token) {
                notificationError = false
                val destination: NavKey? = when (request.action) {
                    "start", "minimal" -> {
                        val minimal = request.action == "minimal"
                        if (viewModel.beginFromNotification(request.sessionId, minimal)) {
                            val type = viewModel.sessionState.value.context?.type
                            if (minimal || type in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B)) Player else Log(SessionOutcome.DONE.name)
                        } else null
                    }
                    "pain" -> (request.sessionId ?: home.pendingSessionId)?.let { CheckPain(it) }
                    "weekly" -> Plan
                    else -> null
                }
                backStack.clear()
                backStack.add(Home)
                if (destination != null) backStack.add(destination) else notificationError = true
                consumedToken = request.token
            }
            onNotificationConsumed(request.token)
        }
    }
    if (!ready) {
        HomeScreen(home.copy(loading = home.loading || preferences.loading, error = home.error || preferences.loadError),
            onRetry = { viewModel.retry(); settingsViewModel.retry() }, onStart = {}, onMinimal = {}, onLog = {},
            onPlan = {}, onRedFlags = {}, onWorkOff = {})
        return
    }
    if (!onboarded) {
        BackHandler(enabled = onboardingRedFlags) { onboardingRedFlags = false }
        if (onboardingRedFlags) RedFlagsScreen(onBack = { onboardingRedFlags = false })
        else OnboardingScreen(preferences, settingsViewModel::edit, { settingsViewModel.save(onboarding = true) },
            settingsViewModel::retry, { onboardingRedFlags = true }, settingsViewModel::refreshPermissions)
        return
    }
    NavDisplay(
        backStack = backStack,
        onBack = { if (!session.saving && !preferences.saving) back() },
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
                        onSettings = { backStack.add(AppSettings) },
                        onDiary = { backStack.add(Diary) },
                        onPainCheck = { backStack.add(CheckPain(it)) },
                        notificationsDisabled = preferences.persisted?.notificationsEnabled != true || !preferences.notificationsAllowed,
                        notificationRequestError = notificationError,
                    )
                    Diary -> DiaryScreen(home, onBack = back, onRedFlags = { backStack.add(RedFlags) })
                    Plan -> Column(Modifier.fillMaxSize()) {
                        IconButton(onClick = back, modifier = Modifier.statusBarsPadding()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.log_back))
                        }
                        Box(Modifier.weight(1f)) { WeeklyPlanRoute() }
                    }
                    RedFlags -> RedFlagsScreen(onBack = back)
                    AppSettings -> SettingsScreen(preferences, onboarding = false, onEdit = settingsViewModel::edit,
                        onSave = { settingsViewModel.save(onboarding = false) }, onRetry = settingsViewModel::retry,
                        onBack = back, onRedFlags = { backStack.add(RedFlags) }, onDebug = { backStack.add(Debug) },
                        onPermissionsChanged = settingsViewModel::refreshPermissions)
                    Debug -> {
                        val debugViewModel: DebugViewModel = hiltViewModel()
                        val debug by debugViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(Unit) { debugViewModel.refresh() }
                        DebugScreen(debug, { debugViewModel.refresh() }, { debugViewModel.refresh(test = true) }, back)
                    }
                    is CheckPain -> {
                        val checkViewModel: PainCheckViewModel = hiltViewModel()
                        val check by checkViewModel.uiState.collectAsStateWithLifecycle()
                        LaunchedEffect(destination.sessionId) { checkViewModel.load(destination.sessionId) }
                        LaunchedEffect(check.saved) {
                            if (check.saved && backStack.lastOrNull() == destination) {
                                backStack.clear(); backStack.add(Home); viewModel.refreshDate()
                            }
                        }
                        BackHandler(enabled = check.saving) {}
                        PainCheckScreen(destination.sessionId, check, checkViewModel::save, checkViewModel::retry, back)
                    }
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