package it.finardi.schiena.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import it.finardi.schiena.notif.NotificationNavigation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NotificationInboxTest {
    private fun intent(action: String, id: Long = 0) = Intent()
        .putExtra(NotificationNavigation.ACTION, action).putExtra(NotificationNavigation.SESSION_ID, id)

    @Test
    fun coldStartAcceptsEveryPublicActionAndNormalizesBackendZeroId() {
        listOf("start", "minimal", "pain", "weekly").forEach { action ->
            val inbox = NotificationInbox()
            inbox.restore(null, intent(action))
            assertEquals(action, inbox.requests.value!!.action)
            assertNull(inbox.requests.value!!.sessionId)
        }
    }

    @Test
    fun pendingRequestSurvivesRotationAndProcessRestoreWithSameToken() {
        val inbox = NotificationInbox()
        inbox.restore(null, intent("pain", 17))
        val pending = inbox.requests.value
        val saved = Bundle()
        inbox.save(saved)
        val restored = NotificationInbox()
        restored.restore(saved, intent("start"))
        assertEquals(pending, restored.requests.value)
        assertTrue(restored.consume(pending!!.token))
        assertFalse(restored.consume(pending.token))
    }

    @Test
    fun consumedIntentIsNotReplayedAlongsideRestoredNavigation() {
        val original = intent("minimal", 42)
        val inbox = NotificationInbox()
        inbox.restore(null, original)
        inbox.consume(inbox.requests.value!!.token)
        val saved = Bundle()
        inbox.save(saved)
        val restored = NotificationInbox()
        restored.restore(saved, original)
        assertNull(restored.requests.value)
    }

    @Test
    fun warmIntentReplacesPendingAndOldConsumptionCannotDropIt() {
        val inbox = NotificationInbox()
        inbox.restore(null, intent("start"))
        val old = inbox.requests.value!!.token
        inbox.receive(intent("pain", 24))
        assertFalse(inbox.consume(old))
        assertEquals(24L, inbox.requests.value!!.sessionId)
        inbox.receive(intent("pain_ok"))
        assertEquals("pain", inbox.requests.value!!.action)
    }
}