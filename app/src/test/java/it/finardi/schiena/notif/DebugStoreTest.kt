package it.finardi.schiena.notif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DebugStoreTest {
    private lateinit var context: Context
    private val clock = Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("notification_backend", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun lastFiftyEventsSurviveNewStoreInstance() {
        val store = DebugStore(context, clock)
        repeat(60) { store.event("event $it") }
        val restored = DebugStore(context, clock).snapshot()
        assertEquals(50, restored.events.size)
        assertTrue(restored.events.first().endsWith("event 10"))
        assertTrue(restored.events.last().endsWith("event 59"))
    }

    @Test fun rotationPersistsAndDoesNotRepeatAtWrap() {
        assertEquals(0, DebugStore(context, clock).nextCopy("session", 2))
        assertEquals(1, DebugStore(context, clock).nextCopy("session", 2))
        assertEquals(0, DebugStore(context, clock).nextCopy("session", 2))
        assertEquals(0, DebugStore(context, clock).nextCopy("office", 2))
    }

    @Test fun alarmSnapshotPersistsLedger() {
        val store = DebugStore(context, clock)
        store.update { it.copy(alarms = listOf(AlarmRecord("office", "office", clock.millis(), "token", "office:1", expiresMillis = clock.millis() + 3600))) }
        assertEquals(listOf(DebugAlarm("office", clock.instant().toString())), DebugStore(context, clock).snapshot().alarms)
    }

    @Test fun crashIsPersistedAndOriginalHandlerIsDelegated() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
            val store = DebugStore(context, clock)
            store.installCrashHandler()
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), IllegalStateException("test crash"))
            assertTrue(delegated)
            assertTrue(DebugStore(context, clock).snapshot().lastCrash!!.contains("test crash"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}