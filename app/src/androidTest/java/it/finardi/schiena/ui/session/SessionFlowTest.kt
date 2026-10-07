package it.finardi.schiena.ui.session

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.finardi.schiena.R
import it.finardi.schiena.data.Phase
import it.finardi.schiena.data.SessionLog
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.HomeScreen
import it.finardi.schiena.ui.home.HomeUiState
import it.finardi.schiena.ui.home.SessionContext
import it.finardi.schiena.ui.home.SessionUiState
import it.finardi.schiena.ui.log.SessionLogScreen
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 7)
    private val phase = Phase(12, "Test database phase", 6)

    @Test
    fun homeQuickDoneSavesInThreeTapsWithRequiredDefaultPain() {
        val host = render(startInLog = false)
        var taps = 0

        click(R.string.guided_other_actions)
        taps++
        click(R.string.home_outcome_done)
        taps++
        text(R.string.log_title).assertIsDisplayed()
        text(R.string.log_done).assertIsSelected()
        text(R.string.log_pain, 0).assertExists()
        slider().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..10f, 9),
        ))
        click(R.string.log_save)
        taps++

        compose.runOnIdle {
            assertTrue(taps <= 3)
            assertEquals(listOf(SavedForm(SessionOutcome.DONE, 0, false, null, "")), host.saves)
        }
    }

    @Test
    fun homeQuickMinimalSavesWithoutDurationInThreeTaps() {
        val host = render(type = SessionType.AEROBIC, startInLog = false)

        click(R.string.guided_other_actions)
        click(R.string.home_outcome_minimal)
        text(R.string.log_minimal).assertIsSelected()
        field(R.string.log_duration).assertDoesNotExist()
        click(R.string.log_save)

        compose.runOnIdle {
            assertEquals(listOf(SavedForm(SessionOutcome.MINIMAL, 0, false, null, "")), host.saves)
        }
    }

    @Test
    fun homeQuickSkippedSavesInThreeTapsWithoutPainOrDuration() {
        val host = render(type = SessionType.PILATES, startInLog = false)

        click(R.string.guided_other_actions)
        click(R.string.home_outcome_skipped)
        text(R.string.log_skipped).assertIsSelected()
        slider().assertDoesNotExist()
        text(R.string.log_radiating).assertDoesNotExist()
        field(R.string.log_duration).assertDoesNotExist()
        click(R.string.log_save)

        compose.runOnIdle {
            assertEquals(listOf(SavedForm(SessionOutcome.SKIPPED, null, false, null, "")), host.saves)
        }
    }

    @Test
    fun sliderSelectionIsTheRequiredPainValueSentToSave() {
        val host = render()

        setPain(4f)
        text(R.string.log_pain, 4).assertExists()
        click(R.string.log_save)

        compose.runOnIdle { assertEquals(4, host.saves.single().pain) }
    }

    @Test
    fun switchingToSkippedClearsPainRadiatingAndDurationFromPreviousLogPayload() {
        val previous = SessionLog(
            id = 1, date = today, sessionType = SessionType.AEROBIC, phaseId = phase.id,
            outcome = SessionOutcome.DONE, painDuring = 7, radiating = true, durationMin = 35,
            note = "Previous note",
        )
        val host = render(type = previous.sessionType, previous = previous)

        text(R.string.log_replace).assertExists()
        text(R.string.log_pain, 7).assertExists()
        click(R.string.log_skipped)
        slider().assertDoesNotExist()
        radiatingSwitch().assertDoesNotExist()
        field(R.string.log_duration).assertDoesNotExist()
        click(R.string.log_save)

        compose.runOnIdle {
            assertEquals(listOf(SavedForm(SessionOutcome.SKIPPED, null, false, null, "Previous note")), host.saves)
        }
    }

    @Test
    fun nonstrengthDurationRejectsEmptyNonnumericOverflowAndOutOfRangeValues() {
        val host = render(type = SessionType.AEROBIC)

        listOf("", "abc", "1.5", "-1", "0", "1441", "999999999999").forEach { invalid ->
            field(R.string.log_duration).performScrollTo().performTextReplacement(invalid)
            text(R.string.log_save).performScrollTo().assertIsNotEnabled()
        }
        listOf("1", "1440").forEach { valid ->
            field(R.string.log_duration).performScrollTo().performTextReplacement(valid)
            text(R.string.log_save).performScrollTo().assertIsEnabled()
        }
        click(R.string.log_save)

        compose.runOnIdle { assertEquals(1440, host.saves.single().duration) }
    }

    @Test
    fun everyNonstrengthDoneTypeRequiresDuration() {
        val host = render(type = SessionType.AEROBIC)

        listOf(SessionType.AEROBIC, SessionType.PILATES, SessionType.FREE, SessionType.ACTIVE_REST).forEach { type ->
            compose.runOnIdle { host.state = host.state.copy(context = host.state.context!!.copy(type = type)) }
            field(R.string.log_duration).assertExists()
            text(R.string.log_save).performScrollTo().assertIsNotEnabled()
        }
        compose.runOnIdle { assertTrue(host.saves.isEmpty()) }
    }

    @Test
    fun bothStrengthDoneTypesCanSaveWithoutDuration() {
        val host = render(type = SessionType.STRENGTH_A)

        listOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B).forEach { type ->
            compose.runOnIdle { host.state = host.state.copy(context = host.state.context!!.copy(type = type)) }
            field(R.string.log_duration).assertDoesNotExist()
            text(R.string.log_save).performScrollTo().assertIsEnabled()
        }
        click(R.string.log_save)
        compose.runOnIdle { assertEquals(null, host.saves.single().duration) }
    }

    @Test
    fun homeMinimalButtonInvokesItsCallbackWithoutQuickLogging() {
        val host = render(startInLog = false)

        click(R.string.home_minimal)

        compose.runOnIdle {
            assertEquals(1, host.homeMinimalCalls)
            assertEquals(0, host.logMinimalCalls)
            assertTrue(host.saves.isEmpty())
        }
        text(R.string.log_title).assertDoesNotExist()
    }

    @Test
    fun logMinimalButtonInvokesItsCallbackWithoutSaving() {
        val host = render()

        click(R.string.log_start_minimal)

        compose.runOnIdle {
            assertEquals(1, host.logMinimalCalls)
            assertEquals(0, host.homeMinimalCalls)
            assertTrue(host.saves.isEmpty())
        }
    }

    @Test
    fun radiatingSwitchInvokesSafetyCallbackImmediatelyAndOnlyWhenEnabled() {
        val host = render()

        radiatingSwitch().performScrollTo().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, host.redFlagsCalls)
            assertTrue(host.saves.isEmpty())
        }
        text(R.string.log_red_warning).assertExists()
        radiatingSwitch().performScrollTo().performClick().assertIsOff()
        compose.runOnIdle { assertEquals(1, host.redFlagsCalls) }
        radiatingSwitch().performClick()
        click(R.string.log_save)
        compose.runOnIdle {
            assertEquals(2, host.redFlagsCalls)
            assertTrue(host.saves.single().radiating)
        }
    }

    @Test
    fun failedSaveKeepsFormAndRetryEnabled() {
        val host = render()
        compose.runOnIdle { host.failNextSave = true }
        setPain(3f)
        field(R.string.log_note).performScrollTo().performTextReplacement("Retry note")

        click(R.string.log_save)
        text(R.string.log_save_error).performScrollTo().assertIsDisplayed()
        text(R.string.log_save).performScrollTo().assertIsEnabled()
        text(R.string.log_pain, 3).assertExists()
        field(R.string.log_note).assertTextContains("Retry note")
        click(R.string.log_save)

        compose.runOnIdle {
            assertEquals(2, host.saves.size)
            assertEquals(host.saves.first(), host.saves.last())
            assertTrue(host.state.saved)
        }
        text(R.string.log_save_error).assertDoesNotExist()
    }

    @Test
    fun savingDisablesSaveAndFormAndCannotDispatchASecondSave() {
        val host = render()
        compose.runOnIdle { host.holdSaving = true }

        click(R.string.log_save)
        text(R.string.log_saving).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        field(R.string.log_note).assertIsNotEnabled()
        text(R.string.log_skipped).assertIsNotEnabled()
        slider().assertIsNotEnabled()
        radiatingSwitch().assertIsNotEnabled()
        text(R.string.log_start_minimal).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.log_back)).assertIsNotEnabled()

        compose.runOnIdle {
            assertEquals(1, host.saves.size)
            host.state = host.state.copy(saving = false, saved = true)
        }
        text(R.string.log_save).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, host.saves.size) }
    }

    @Test
    fun formRestoresOutcomePainRadiatingDurationAndNote() {
        val restoration = StateRestorationTester(compose)
        val host = render(type = SessionType.AEROBIC, restoration = restoration)
        setPain(4f)
        radiatingSwitch().performScrollTo().performClick()
        field(R.string.log_duration).performScrollTo().performTextReplacement("37")
        field(R.string.log_note).performScrollTo().performTextReplacement("Restored note")
        click(R.string.log_minimal)

        restoration.emulateSavedInstanceStateRestore()

        text(R.string.log_minimal).assertIsSelected()
        text(R.string.log_pain, 4).assertExists()
        radiatingSwitch().assertIsOn()
        field(R.string.log_note).assertTextContains("Restored note")
        field(R.string.log_duration).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, host.redFlagsCalls) }
        click(R.string.log_done)
        field(R.string.log_duration).assertTextContains("37")
        click(R.string.log_save)

        compose.runOnIdle {
            assertEquals(listOf(SavedForm(SessionOutcome.DONE, 4, true, 37, "Restored note")), host.saves)
        }
    }

    private fun string(@StringRes resource: Int, vararg arguments: Any): String = context.getString(resource, *arguments)

    private fun text(@StringRes resource: Int, vararg arguments: Any) =
        compose.onNodeWithText(string(resource, *arguments))

    private fun field(@StringRes label: Int) = compose.onNode(hasSetTextAction() and hasText(string(label)))

    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))

    private fun radiatingSwitch() = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    private fun click(@StringRes resource: Int) {
        val lazyLists = compose.onAllNodes(hasScrollToIndexAction())
        if (lazyLists.fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(string(resource)))
            text(resource).performClick()
        } else {
            text(resource).performScrollTo().performClick()
        }
    }

    private fun setPain(value: Float) {
        slider().performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(value))
        }
    }

    private fun render(
        type: SessionType = SessionType.STRENGTH_A, startInLog: Boolean = true,
        previous: SessionLog? = null, restoration: StateRestorationTester? = null,
    ): Host {
        val host = Host(type, startInLog, previous)
        val content: @Composable () -> Unit = { MaterialTheme { host.Content() } }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        return host
    }

    private data class SavedForm(
        val outcome: SessionOutcome, val pain: Int?, val radiating: Boolean, val duration: Int?, val note: String,
    )

    private inner class Host(type: SessionType, startInLog: Boolean, previous: SessionLog?) {
        var logging by mutableStateOf(startInLog)
        var initialOutcome = SessionOutcome.DONE
        var state by mutableStateOf(SessionUiState(
            context = SessionContext(today, type, phase.id, false), previous = previous,
        ))
        val saves = mutableListOf<SavedForm>()
        var homeMinimalCalls = 0
        var logMinimalCalls = 0
        var redFlagsCalls = 0
        var failNextSave = false
        var holdSaving = false
        private val homeState = HomeUiState(
            loading = false, today = today, phase = phase,
            entry = WeekPlanEntry(today.dayOfWeek, type, LocalTime.of(21, 17)), target = 5,
        )

        @Composable
        fun Content() {
            if (logging) {
                SessionLogScreen(
                    state = state, initialOutcome = initialOutcome,
                    onSave = { outcome, pain, radiating, duration, note ->
                        saves += SavedForm(outcome, pain, radiating, duration, note)
                        state = when {
                            holdSaving -> state.copy(saving = true, saveError = false)
                            failNextSave -> {
                                failNextSave = false
                                state.copy(saving = false, saveError = true)
                            }
                            else -> state.copy(saving = false, saved = true, saveError = false)
                        }
                    },
                    onBack = { logging = false }, onRedFlags = { redFlagsCalls++ },
                    onMinimalPlayer = { logMinimalCalls++ },
                )
            } else {
                HomeScreen(
                    state = homeState, onRetry = {}, onStart = {}, onMinimal = { homeMinimalCalls++ },
                    onLog = { outcome ->
                        initialOutcome = outcome
                        state = state.copy(context = state.context!!.copy(minimal = outcome == SessionOutcome.MINIMAL))
                        logging = true
                    },
                    onPlan = {}, onRedFlags = { redFlagsCalls++ }, onWorkOff = {},
                )
            }
        }
    }
}