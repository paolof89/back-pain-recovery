package it.finardi.schiena.ui.session

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.ui.Home
import it.finardi.schiena.ui.Log
import it.finardi.schiena.ui.Plan
import it.finardi.schiena.ui.Player
import it.finardi.schiena.ui.RedFlags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun actualNavigationKeysRoundTripThroughSavedState() {
        val restoration = StateRestorationTester(compose)
        val expected = listOf(Home, Plan, Player, Log(SessionOutcome.MINIMAL.name), RedFlags)
        var restoredKeys: List<Any> = emptyList()
        restoration.setContent {
            val stack = rememberNavBackStack(*expected.toTypedArray())
            SideEffect { restoredKeys = stack.toList() }
            NavDisplay(
                backStack = stack,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                entryProvider = { key -> NavEntry(key) { Text(key.toString()) } },
            )
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(expected, restoredKeys) }
        compose.onNodeWithText(RedFlags.toString()).assertExists()
    }
}