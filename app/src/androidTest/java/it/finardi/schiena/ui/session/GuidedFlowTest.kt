package it.finardi.schiena.ui.session

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.finardi.schiena.R
import it.finardi.schiena.data.Exercise
import it.finardi.schiena.data.ExercisePrescription
import it.finardi.schiena.data.Phase
import it.finardi.schiena.data.SessionLog
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.ExerciseKind
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.DiaryScreen
import it.finardi.schiena.ui.home.HomeScreen
import it.finardi.schiena.ui.home.HomeUiState
import it.finardi.schiena.ui.player.SessionPlayerScreen
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuidedFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val date = LocalDate.of(2026, 10, 7)
    private val home = HomeUiState(loading = false, today = date, phase = Phase(1, "Test phase", 4),
        entry = WeekPlanEntry(date.dayOfWeek, SessionType.STRENGTH_A, LocalTime.of(20, 0)))
    private val instructions = listOf("First complete test instruction", "Second instruction", "Third instruction", "Fourth instruction")

    private fun exercise(id: String, kind: ExerciseKind = ExerciseKind.REPS, holdSec: Int? = null) =
        ExercisePrescription(Exercise(id, id, "Test goal", instructions, kind, false),
            sets = 1, reps = if (kind == ExerciseKind.REPS) 2 else null,
            holdSec = holdSec, distanceM = null, restSec = 0)

    @Test
    fun preparationDoesNotStartTimerAndShowsEveryInstruction() {
        compose.setContent { MaterialTheme { SessionPlayerScreen(listOf(exercise("test_hold", ExerciseKind.HOLD, 30)), {}, {}) } }
        text(R.string.guided_ready).performScrollTo().assertIsEnabled()
        compose.mainClock.advanceTimeBy(5_000)
        text(R.string.player_seconds, 30L).assertDoesNotExist()
        instructions.forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        text(R.string.guided_ready).performScrollTo().performClick()
        text(R.string.player_seconds, 30L).assertExists()
        text(R.string.player_pause).performScrollTo().assertIsEnabled()
    }

    @Test
    fun completingManualStagePreparesNextExerciseWithoutStartingItsTimer() {
        var completed = 0
        compose.setContent { MaterialTheme { SessionPlayerScreen(listOf(
            exercise("first"), exercise("second", ExerciseKind.HOLD, 30),
        ), { completed++ }, {}) } }
        click(R.string.guided_ready)
        click(R.string.guided_set_done)
        compose.onNodeWithText("second").assertIsDisplayed()
        text(R.string.guided_prepare).assertExists()
        text(R.string.guided_ready).performScrollTo().assertIsEnabled()
        text(R.string.player_seconds, 30L).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, completed) }
    }

    @Test
    fun automaticTimerBoundaryPreparesNextExercise() {
        compose.setContent { MaterialTheme { SessionPlayerScreen(listOf(
            exercise("first", ExerciseKind.HOLD, 1), exercise("second", ExerciseKind.HOLD, 30),
        ), {}, {}) } }
        click(R.string.guided_ready)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("second").fetchSemanticsNodes().isNotEmpty()
        }
        text(R.string.guided_prepare).assertExists()
        text(R.string.player_seconds, 30L).assertDoesNotExist()
        text(R.string.guided_ready).performScrollTo().assertIsEnabled()
    }

    @Test
    fun reopeningInstructionsPausesAndRequiresExplicitConfirmation() {
        compose.setContent { MaterialTheme { SessionPlayerScreen(listOf(exercise("first", ExerciseKind.HOLD, 30)), {}, {}) } }
        click(R.string.guided_ready)
        click(R.string.guided_instructions)
        text(R.string.guided_prepare).assertExists()
        text(R.string.player_seconds, 30L).assertDoesNotExist()
        text(R.string.guided_ready).performScrollTo().assertIsEnabled()
    }

    @Test
    fun restoredRunningExerciseRequiresResumeRatherThanRestart() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { SessionPlayerScreen(listOf(exercise("first", ExerciseKind.HOLD, 30)), {}, {}) } }
        click(R.string.guided_ready)
        restoration.emulateSavedInstanceStateRestore()
        text(R.string.player_paused).assertExists()
        text(R.string.player_resume).performScrollTo().assertIsEnabled()
        text(R.string.guided_ready).assertDoesNotExist()
    }

    @Test
    fun skippingAnExerciseStillPreparesTheNextOne() {
        compose.setContent { MaterialTheme { SessionPlayerScreen(listOf(exercise("first"), exercise("second")), {}, {}) } }
        click(R.string.guided_other_actions)
        click(R.string.player_skip)
        compose.onNodeWithText("second").assertIsDisplayed()
        text(R.string.guided_ready).performScrollTo().assertIsEnabled()
    }

    @Test
    fun lastManualStageCompletesExactlyOnceEvenAfterRestoration() {
        var completed = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { SessionPlayerScreen(listOf(exercise("first")), { completed++ }, {}) } }
        click(R.string.guided_ready)
        click(R.string.guided_set_done)
        compose.runOnIdle { assertEquals(1, completed) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, completed) }
    }

    @Test
    fun todayPrioritizesSessionAndKeepsOtherActionsCollapsed() {
        var starts = 0
        compose.setContent { MaterialTheme { HomeScreen(home, {}, { starts++ }, {}, {}, {}, {}, {}) } }
        text(R.string.home_start).assertIsDisplayed().performClick()
        text(R.string.home_outcome_done).assertDoesNotExist()
        text(R.string.home_history_title).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test
    fun recordedTodayOffersEditRatherThanAnotherStart() {
        val log = SessionLog(7, date, SessionType.STRENGTH_A, 1, SessionOutcome.MINIMAL, painDuring = 2)
        val edited = mutableListOf<SessionOutcome>()
        compose.setContent { MaterialTheme { HomeScreen(home.copy(history = listOf(log)), {}, {}, {}, { edited += it }, {}, {}, {}) } }
        text(R.string.guided_recorded).assertExists()
        text(R.string.home_start).assertDoesNotExist()
        click(R.string.guided_edit_record)
        compose.runOnIdle { assertEquals(listOf(SessionOutcome.MINIMAL), edited) }
    }

    @Test
    fun anotherSessionTypeDoesNotSuppressTodayStart() {
        val log = SessionLog(7, date, SessionType.PILATES, 1, SessionOutcome.DONE, painDuring = 2)
        compose.setContent { MaterialTheme { HomeScreen(home.copy(history = listOf(log)), {}, {}, {}, {}, {}, {}, {}) } }
        text(R.string.home_start).assertIsDisplayed()
        text(R.string.guided_recorded).assertDoesNotExist()
    }

    @Test
    fun pendingCheckUsesItsIdAndDoesNotBlockSession() {
        var checked: Long? = null
        var starts = 0
        compose.setContent { MaterialTheme { HomeScreen(home.copy(pendingChecks = 1, pendingSessionId = 42),
            {}, { starts++ }, {}, {}, {}, {}, {}, onPainCheck = { checked = it }) } }
        text(R.string.guided_check).assertIsDisplayed().performClick()
        click(R.string.home_start)
        compose.runOnIdle { assertEquals(42L, checked); assertEquals(1, starts) }
    }

    @Test
    fun activitiesWithoutPlayerUseHonestRecordLabel() {
        compose.setContent { MaterialTheme { HomeScreen(home.copy(entry = home.entry!!.copy(sessionType = SessionType.AEROBIC)),
            {}, {}, {}, {}, {}, {}, {}) } }
        text(R.string.guided_record_activity).assertIsDisplayed()
        text(R.string.home_start).assertDoesNotExist()
    }

    @Test
    fun loadErrorDoesNotExposeFakeSessionActions() {
        compose.setContent { MaterialTheme { HomeScreen(home.copy(error = true), {}, {}, {}, {}, {}, {}, {}) } }
        text(R.string.retry).assertIsDisplayed()
        text(R.string.home_start).assertDoesNotExist()
        text(R.string.home_minimal).assertDoesNotExist()
    }

    @Test
    fun diaryPreservesTextualSafetyStatusAndBackAction() {
        var backs = 0
        var safetyOpens = 0
        val log = SessionLog(7, date, SessionType.STRENGTH_A, 1, SessionOutcome.DONE,
            painDuring = 7, status = SessionStatus.RED)
        compose.setContent { MaterialTheme { DiaryScreen(home.copy(history = listOf(log)), { backs++ }, { safetyOpens++ }) } }
        text(R.string.home_status_red).performScrollTo().assertIsDisplayed()
        click(R.string.home_red_flags)
        compose.onNodeWithContentDescription(context.getString(R.string.log_back)).performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(1, safetyOpens) }
    }

    private fun text(@StringRes resource: Int, vararg args: Any) = compose.onNodeWithText(context.getString(resource, *args))
    private fun click(@StringRes resource: Int) = text(resource).performScrollTo().performClick()
}