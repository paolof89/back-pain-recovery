package it.finardi.schiena.data

import android.app.Application
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PainCheckRepositoryTest : ProgramDataFixture() {
    private val date = LocalDate.of(2026, 10, 6)
    private fun checks(at: String) = PainCheckRepository(database, settingsRepository,
        Clock.fixed(Instant.parse(at), clock.zone))

    private suspend fun log(type: SessionType = SessionType.STRENGTH_A, pain: Int = 2,
        radiating: Boolean = false, outcome: SessionOutcome = SessionOutcome.DONE): Long =
        SessionRepository(database).save(date, type, 1, outcome, pain, radiating, null, null)

    @Test fun windowRejectsEarlyAndExpiredChecksAndAcceptsDueBoundary() = runBlocking {
        repository.initialize()
        val sessionId = log()
        assertFalse(checks("2026-10-07T06:59:59Z").complete(sessionId, 0, true))
        assertTrue(checks("2026-10-07T06:59:59Z").pending().isEmpty())
        assertEquals(sessionId, checks("2026-10-07T07:00:00Z").pending().single().id)
        assertFalse(checks("2026-10-09T07:00:00Z").complete(sessionId, 0, true))
        assertTrue(checks("2026-10-09T07:00:00Z").pending().isEmpty())
        assertTrue(checks("2026-10-07T07:00:00Z").complete(sessionId, 3, true))
    }

    @Test fun completionIsAtomicIdempotentAndPreservesRedAndYellow() = runBlocking {
        repository.initialize()
        val checker = checks("2026-10-07T07:00:00Z")
        for (pain in 0..10) {
            for (radiating in listOf(false, true)) {
                for (baseline in listOf(false, true)) {
                    val sessionId = log(pain = pain, radiating = radiating)
                    assertTrue(checker.complete(sessionId, 3, baseline))
                    assertFalse(checker.complete(sessionId, 0, !baseline))
                    val expected = when {
                        radiating || pain > 5 -> SessionStatus.RED
                        pain >= 4 || !baseline -> SessionStatus.YELLOW
                        else -> SessionStatus.GREEN
                    }
                    assertEquals(expected, database.historyDao().getSessionById(sessionId)?.status)
                    assertEquals(PainCheck(sessionId, Instant.parse("2026-10-07T07:00:00Z"), 3, baseline),
                        database.historyDao().getPainChecks().single())
                }
            }
        }
    }

    @Test fun onlyEligibleOutcomesAndTypesCanBeChecked() = runBlocking {
        repository.initialize()
        val checker = checks("2026-10-07T07:00:00Z")
        for (type in SessionType.entries) {
            for (outcome in SessionOutcome.entries) {
                val sessionId = log(type = type, outcome = outcome)
                val eligible = outcome != SessionOutcome.SKIPPED &&
                    type in setOf(SessionType.STRENGTH_A, SessionType.STRENGTH_B, SessionType.PILATES)
                assertEquals(eligible, checker.complete(sessionId, 0, true))
            }
        }
        assertFalse(checker.complete(Long.MAX_VALUE, 0, true))
    }

    @Test fun expirationDoesNotEraseSafetyOrVerifiedStatus() = runBlocking {
        repository.initialize()
        val green = log()
        val pending = log(type = SessionType.PILATES)
        val red = log(type = SessionType.STRENGTH_B, pain = 6)
        assertTrue(checks("2026-10-07T07:00:00Z").complete(green, 0, true))
        checks("2026-10-09T07:00:00Z").expire()
        assertEquals(SessionStatus.GREEN, database.historyDao().getSessionById(green)?.status)
        assertEquals(SessionStatus.UNVERIFIED, database.historyDao().getSessionById(pending)?.status)
        assertEquals(SessionStatus.RED, database.historyDao().getSessionById(red)?.status)
        assertEquals(1, database.historyDao().getPainChecks().size)
    }

    @Test fun invalidPainDoesNotChangeEitherTable() = runBlocking {
        repository.initialize()
        val sessionId = log()
        val before = database.historyDao().getSessions()
        for (pain in listOf(-1, 11)) {
            assertTrue(runCatching { checks("2026-10-07T07:00:00Z").complete(sessionId, pain, true) }
                .exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(before, database.historyDao().getSessions())
        assertTrue(database.historyDao().getPainChecks().isEmpty())
    }
}