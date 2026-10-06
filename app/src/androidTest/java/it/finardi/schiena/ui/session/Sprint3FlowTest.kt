package it.finardi.schiena.ui.session

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.WeekPlanEntry
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionType
import it.finardi.schiena.ui.home.HomeScreen
import it.finardi.schiena.ui.home.HomeUiState
import it.finardi.schiena.ui.paincheck.PainCheckScreen
import it.finardi.schiena.ui.paincheck.PainCheckUiState
import it.finardi.schiena.ui.settings.SettingsDraft
import it.finardi.schiena.ui.settings.SettingsScreen
import it.finardi.schiena.ui.settings.SettingsUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Sprint3FlowTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val date = LocalDate.of(2026, 10, 6)
    private val log = SessionLog(17, date.minusDays(1), SessionType.STRENGTH_A, 1, SessionOutcome.DONE, painDuring = 2)
    private val settings = Settings(LocalTime.of(9, 0), setOf(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(18, 0), 75)
    private val plan = DayOfWeek.entries.map { WeekPlanEntry(it, SessionType.FREE, LocalTime.of(20, it.value)) }

    @Test
    fun baselineMustBeExplicitAndCheckSavesWithDefaultPainInTwoTaps() {
        val saves = mutableListOf<Pair<Int, Boolean>>()
        compose.setContent { MaterialTheme { PainCheckScreen(log.id, PainCheckUiState(loading = false, log = log),
            onSave = { pain, baseline -> saves += pain to baseline }, onRetry = {}, onBack = {}) } }
        text(R.string.s3_save).performScrollTo().assertIsNotEnabled()
        text(R.string.s3_yes).assertIsNotSelected()
        text(R.string.s3_no).assertIsNotSelected()
        text(R.string.s3_yes).performScrollTo().performClick()
        text(R.string.s3_save).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(0 to true), saves) }
    }

    @Test
    fun failedSaveKeepsSliderAndBaselineAcrossRestorationAndRetry() {
        var state by mutableStateOf(PainCheckUiState(loading = false, log = log))
        val saves = mutableListOf<Pair<Int, Boolean>>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { PainCheckScreen(log.id, state,
            onSave = { pain, baseline -> saves += pain to baseline; state = state.copy(error = true) }, onRetry = {}, onBack = {}) } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        text(R.string.s3_no).performScrollTo().performClick()
        text(R.string.s3_save).performScrollTo().performClick()
        text(R.string.s3_check_error).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        text(R.string.s3_no).assertIsSelected()
        text(R.string.s3_pain_now, 6).assertExists()
        text(R.string.retry).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(6 to false, 6 to false), saves) }
    }

    @Test
    fun staleCheckCannotSaveAndOffersRefresh() {
        var refreshes = 0
        compose.setContent { MaterialTheme { PainCheckScreen(log.id, PainCheckUiState(loading = false, stale = true),
            onSave = { _, _ -> fail("Stale check must not save") }, onRetry = { refreshes++ }, onBack = {}) } }
        text(R.string.s3_check_stale).assertIsDisplayed()
        text(R.string.s3_save).assertDoesNotExist()
        text(R.string.s3_refresh).performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun onboardingRequiresCheckboxAndPreservesDraftAfterFailedSave() {
        var state by mutableStateOf(SettingsUiState(loading = false, persisted = settings, draft = SettingsDraft.from(settings, plan),
            notificationsAllowed = true, exactAllowed = true))
        var saves = 0
        compose.setContent { MaterialTheme { SettingsScreen(state, onboarding = true,
            onEdit = { transform -> state = state.copy(draft = transform(state.draft!!)) },
            onSave = { saves++; state = state.copy(saveError = true) }, onRetry = {}, onBack = {}, onRedFlags = {}, onDebug = {}, onPermissionsChanged = {}) } }
        text(R.string.s3_finish).performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable() and hasAnySibling(hasText(context.getString(R.string.s3_accept)))).performScrollTo().performClick()
        text(R.string.s3_finish).performScrollTo().assertIsEnabled().performClick()
        text(R.string.s3_save_error).performScrollTo().assertIsDisplayed()
        text(R.string.retry).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, saves); assertTrue(state.draft!!.accepted) }
    }

    @Test
    fun homeCardUsesPendingIdAndSettingsRemainReachableWhenNotificationsDenied() {
        var selected: Long? = null
        var settingsOpens = 0
        val home = HomeUiState(loading = false, today = date, entry = plan.first { it.dayOfWeek == date.dayOfWeek },
            phase = Phase(1, "Fase test", 4), pendingChecks = 1, pendingSessionId = log.id)
        compose.setContent { MaterialTheme { HomeScreen(home, onRetry = {}, onStart = {}, onMinimal = {}, onLog = {},
            onPlan = {}, onRedFlags = {}, onWorkOff = {}, onSettings = { settingsOpens++ }, onPainCheck = { selected = it }, notificationsDisabled = true) } }
        text(R.string.s3_notifications_off).assertIsDisplayed()
        text(R.string.s3_settings).performClick()
        text(R.string.s3_check_open).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(log.id, selected); assertEquals(1, settingsOpens) }
    }

    @Test
    fun versionLongPressOpensDebugAndRedFlagsRemainReachable() {
        var debug = 0
        var redFlags = 0
        val state = SettingsUiState(loading = false, persisted = settings, draft = SettingsDraft.from(settings, plan), notificationsAllowed = true, exactAllowed = true)
        compose.setContent { MaterialTheme { SettingsScreen(state, onboarding = false, onEdit = {}, onSave = {}, onRetry = {}, onBack = {},
            onRedFlags = { redFlags++ }, onDebug = { debug++ }, onPermissionsChanged = {}) } }
        text(R.string.home_red_flags).performScrollTo().performClick()
        compose.onNode(hasText("Versione", substring = true)).performScrollTo().performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, debug); assertEquals(1, redFlags) }
    }

    private fun text(@StringRes resource: Int, vararg args: Any) = compose.onNodeWithText(context.getString(resource, *args))
}