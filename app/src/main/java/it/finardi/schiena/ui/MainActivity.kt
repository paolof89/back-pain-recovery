package it.finardi.schiena.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import it.finardi.schiena.ui.theme.SchienaTheme
import it.finardi.schiena.notif.NotificationNavigation
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationRequest(val token: String, val action: String, val sessionId: Long? = null)

internal class NotificationInbox {
    private val pending = MutableStateFlow<NotificationRequest?>(null)
    val requests = pending.asStateFlow()

    fun restore(savedState: Bundle?, intent: Intent) {
        pending.value = if (savedState == null) parse(intent) else {
            savedState.getString("notification.request.action")?.let { action ->
                NotificationRequest(
                    savedState.getString("notification.request.token") ?: UUID.randomUUID().toString(), action,
                    if (savedState.containsKey("notification.request.session")) savedState.getLong("notification.request.session") else null,
                )
            }
        }
    }

    fun receive(intent: Intent) { parse(intent)?.let { pending.value = it } }

    fun save(outState: Bundle) {
        pending.value?.let { request ->
            outState.putString("notification.request.action", request.action)
            outState.putString("notification.request.token", request.token)
            request.sessionId?.let { outState.putLong("notification.request.session", it) }
        }
    }

    fun consume(token: String): Boolean {
        if (pending.value?.token != token) return false
        pending.value = null
        return true
    }

    private fun parse(intent: Intent): NotificationRequest? {
        val action = intent.getStringExtra(NotificationNavigation.ACTION)
        if (action !in setOf("start", "minimal", "pain", "weekly")) return null
        val sessionId = if (intent.hasExtra(NotificationNavigation.SESSION_ID)) intent.getLongExtra(NotificationNavigation.SESSION_ID, -1L).takeUnless { it == 0L } else null
        return NotificationRequest(UUID.randomUUID().toString(), checkNotNull(action), sessionId)
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val inbox = NotificationInbox()
    val notificationRequests = inbox.requests

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inbox.restore(savedInstanceState, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            SchienaTheme {
                val request by notificationRequests.collectAsStateWithLifecycle()
                SchienaApp(notificationRequest = request, onNotificationConsumed = ::consumeRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inbox.receive(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        inbox.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun consumeRequest(token: String) {
        if (inbox.consume(token)) {
            intent.removeExtra(NotificationNavigation.ACTION)
            intent.removeExtra(NotificationNavigation.SESSION_ID)
        }
    }

}