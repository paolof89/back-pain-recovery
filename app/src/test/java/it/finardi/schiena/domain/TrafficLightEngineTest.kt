package it.finardi.schiena.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficLightEngineTest {
    @Test
    fun provisionalCoversEveryPainAndRadiatingCombinationForEveryOutcome() {
        for (outcome in SessionOutcome.values()) {
            for (pain in 0..10) {
                for (radiating in listOf(false, true)) {
                    val expected = when {
                        outcome == SessionOutcome.SKIPPED -> SessionStatus.UNVERIFIED
                        radiating || pain > 5 -> SessionStatus.RED
                        pain >= 4 -> SessionStatus.YELLOW
                        else -> SessionStatus.PENDING
                    }
                    assertEquals("outcome=$outcome pain=$pain radiating=$radiating", expected,
                        TrafficLightEngine.provisional(outcome, pain, radiating))
                }
            }
        }
    }

    @Test
    fun skippedDoesNotRequirePainEvenWhenRadiating() {
        for (pain in listOf(null, -1, 11)) {
            for (radiating in listOf(false, true)) {
                assertEquals(SessionStatus.UNVERIFIED,
                    TrafficLightEngine.provisional(SessionOutcome.SKIPPED, pain, radiating))
            }
        }
    }

    @Test
    fun nonSkippedRequiresPainInRangeEvenWhenRadiating() {
        for (outcome in listOf(SessionOutcome.DONE, SessionOutcome.MINIMAL)) {
            for (pain in listOf(null, -1, 11, Int.MIN_VALUE, Int.MAX_VALUE)) {
                for (radiating in listOf(false, true)) {
                    val failure = runCatching {
                        TrafficLightEngine.provisional(outcome, pain, radiating)
                    }.exceptionOrNull()
                    assertTrue("outcome=$outcome pain=$pain radiating=$radiating",
                        failure is IllegalArgumentException)
                }
            }
        }
    }

    @Test
    fun pendingCheckYesIsGreenAndNoIsYellowRegardlessOfExpiry() {
        for (expired in listOf(false, true)) {
            assertEquals(SessionStatus.GREEN,
                TrafficLightEngine.verified(SessionStatus.PENDING, true, expired))
            assertEquals(SessionStatus.YELLOW,
                TrafficLightEngine.verified(SessionStatus.PENDING, false, expired))
        }
    }

    @Test
    fun answeredChecksCannotDowngradeRedOrYellow() {
        for (status in listOf(SessionStatus.RED, SessionStatus.YELLOW)) {
            for (backToBaseline in listOf(false, true)) {
                for (expired in listOf(false, true)) {
                    assertEquals("status=$status baseline=$backToBaseline expired=$expired", status,
                        TrafficLightEngine.verified(status, backToBaseline, expired))
                }
            }
        }
    }

    @Test
    fun absentCheckRetainsProvisionalStatusUntilExpiryThenIsUnverified() {
        for (status in listOf(SessionStatus.PENDING, SessionStatus.RED, SessionStatus.YELLOW)) {
            assertEquals(status, TrafficLightEngine.verified(status, null, false))
            assertEquals(SessionStatus.UNVERIFIED, TrafficLightEngine.verified(status, null, true))
        }
    }
}