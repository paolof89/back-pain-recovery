package it.finardi.schiena.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import it.finardi.schiena.data.AppDatabase
import it.finardi.schiena.data.OfficeBreakEvent
import it.finardi.schiena.data.PainCheckRepository
import it.finardi.schiena.data.SessionLog
import it.finardi.schiena.data.Settings
import it.finardi.schiena.data.SettingsRepository
import it.finardi.schiena.domain.OfficeBreakAction
import it.finardi.schiena.domain.PainCheckWindow
import it.finardi.schiena.domain.SessionOutcome
import it.finardi.schiena.domain.SessionStatus
import it.finardi.schiena.domain.SessionType
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val painChecks: PainCheckRepository,
    private val factory: NotificationFactory,
    private val debug: DebugStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val work get() = WorkManager.getInstance(context)
    @Volatile private var notificationClock: Clock = clock
    private fun localClock(): Clock = notificationClock

    internal fun updateTimeZone() {
        notificationClock = clock.withZone(ZoneId.systemDefault())
    }

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    fun notificationsAllowed(): Boolean = factory.allowed()

    suspend fun reschedule() = mutex.withLock { rescheduleLocked() }

    private suspend fun rescheduleLocked(weeklyWorker: Boolean = false) {
        factory.channels()
        painChecks.expire()
        work.enqueueUniquePeriodicWork(
            "notification_cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NotificationMaintenanceWorker>(24, TimeUnit.HOURS).build(),
        )
        val settings = settingsRepository.settings.first()
        val currentClock = localClock()
        val now = currentClock.instant()
        val today = LocalDate.now(currentClock)
        val plan = database.programDao().observePlan().first()
        val logs = database.historyDao().getSessions()
        val checked = database.historyDao().getPainChecks().map { it.sessionLogId }.toSet()
        val desired = mutableListOf<AlarmRecord>()
        val state = debug.state()
        val enabled = settings?.notificationsEnabled == true && notificationsAllowed()
        if (enabled && settings != null) {
            plan.forEach { entry ->
                val logged = logs.filter { it.sessionType == entry.sessionType }.map { it.date }.toSet()
                val next = AlarmTriggers.nextSession(currentClock, entry, logged)
                val date = next.atZone(currentClock.zone).toLocalDate()
                desired += record("session_${entry.dayOfWeek}", "session", next, "session:$date:${entry.sessionType}", date.toString(), entry.sessionType.name, expires = date.plusDays(1).atStartOfDay(currentClock.zone).toInstant())
                val recall = AlarmTriggers.recall(currentClock, entry, today in logged, "recall:$today" in state.delivered)
                if (recall != null) desired += record("recall", "recall", recall, "recall:$today", today.toString(), entry.sessionType.name, expires = today.plusDays(1).atStartOfDay(currentClock.zone).toInstant())
                val snoozeKey = "session:$today:${entry.sessionType}"
                val snooze = state.snoozes[snoozeKey]?.let(Instant::ofEpochMilli)
                if (entry.dayOfWeek == today.dayOfWeek && today !in logged && snooze != null && snooze > now && snooze.atZone(currentClock.zone).toLocalDate() == today) {
                    desired.removeAll { it.key == "session_${entry.dayOfWeek}" }
                    desired += record("session_${entry.dayOfWeek}", "session", snooze, "snooze:$snoozeKey:${snooze.toEpochMilli()}", today.toString(), entry.sessionType.name, expires = today.plusDays(1).atStartOfDay(currentClock.zone).toInstant())
                }
            }
            val officeSnooze = state.snoozes["office:$today"]?.let(Instant::ofEpochMilli)?.takeIf {
                val local = it.atZone(currentClock.zone)
                it > now && local.toLocalDate() == today && today.dayOfWeek in settings.workDays &&
                    !settings.isWorkOffToday(today) && local.toLocalTime() >= settings.workStart && local.toLocalTime() < settings.workEnd
            }
            val office = officeSnooze ?: AlarmTriggers.nextOffice(currentClock, settings)
            if (office != null) desired += record("office", "office", office, "office:${office.toEpochMilli()}", office.atZone(currentClock.zone).toLocalDate().toString(), expires = office.plus(Duration.ofHours(1)))
            val eligible = logs.filter { PainCheckWindow.required(it.sessionType, it.outcome) && it.id !in checked && it.status != SessionStatus.UNVERIFIED }
            eligible.forEach { log ->
                val due = PainCheckWindow.due(log.date, settings.checkTime, currentClock.zone)
                val expires = PainCheckWindow.expires(log.date, settings.checkTime, currentClock.zone)
                val occurrence = "pain:${log.id}:$due:${signature(log)}"
                val previous = state.alarms.find { it.occurrence == occurrence }
                val trigger = AlarmTriggers.painTrigger(currentClock, log.date, settings.checkTime, occurrence in state.delivered, previous?.let { Instant.ofEpochMilli(it.triggerMillis) })
                if (trigger != null) {
                    desired += record("pain_${log.id}", "pain", trigger, occurrence, log.date.toString(), log.sessionType.name, log.id, expires).copy(signature = signature(log))
                }
            }
            val expiry = eligible.map { PainCheckWindow.expires(it.date, settings.checkTime, currentClock.zone) }.filter { it > now }.minOrNull()
            if (expiry != null) desired += record("expiry", "expiry", expiry, "expiry:$expiry", expires = expiry.plusSeconds(3600))
            val weekly = AlarmTriggers.nextWeekly(currentClock)
            desired += record("weekly", "weekly", weekly, "weekly:$weekly", expires = weekly.plus(Duration.ofHours(24)))
            state.alarms.filter {
                NotificationRules.awaitingDelivery(it, now, state.delivered) &&
                    validForCurrentData(it, settings, currentClock)
            }.forEach { due ->
                desired.removeAll { it.key == due.key }
                desired += due
            }
        }
        val effective = desired.map { candidate ->
            state.alarms.find { it.key == candidate.key && it.occurrence == candidate.occurrence && it.triggerMillis == candidate.triggerMillis && it.expiresMillis == candidate.expiresMillis && it.signature == candidate.signature } ?: candidate
        }
        state.alarms.filter { old -> effective.none { it.token == old.token } }.forEach { old ->
            if (old.kind != "weekly") manager.cancel(pending(old))
        }
        if (!enabled) {
            factory.cancelAll()
            work.cancelUniqueWork("notification_weekly")
        }
        val active = state.active.filter { alarm ->
            enabled && alarm.expiresMillis > now.toEpochMilli() && when (alarm.kind) {
                "session", "recall" -> alarm.date == today.toString() && plan.any { it.dayOfWeek == today.dayOfWeek && it.sessionType.name == alarm.type } && logs.none { it.date.toString() == alarm.date && it.sessionType.name == alarm.type }
                "pain" -> logs.any { it.id == alarm.sessionId && signature(it) == alarm.signature && PainCheckWindow.required(it.sessionType, it.outcome) && it.status != SessionStatus.UNVERIFIED } && alarm.sessionId !in checked && settings != null && logs.find { it.id == alarm.sessionId }?.let { PainCheckWindow.pending(it.date, settings.checkTime, currentClock.zone, now) } == true
                "office" -> settings != null && !settings.isWorkOffToday(today) && today.dayOfWeek in settings.workDays && alarm.date == today.toString() && now.atZone(currentClock.zone).toLocalTime() < settings.workEnd
                else -> true
            }
        }
        state.active.filter { old -> active.none { it.token == old.token } }.forEach(factory::cancel)
        debug.update {
            it.copy(alarms = effective, active = active.takeLast(128), snoozes = it.snoozes.filterValues { trigger -> trigger > now.toEpochMilli() })
        }
        effective.forEach { alarm ->
            if (alarm.kind == "weekly") {
                val unchanged = state.alarms.any { it.token == alarm.token }
                work.enqueueUniqueWork("notification_weekly", if (unchanged) ExistingWorkPolicy.KEEP else if (weeklyWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<WeeklyReviewWorker>()
                            .setInitialDelay((alarm.triggerMillis - now.toEpochMilli()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                            .setInputData(workDataOf("token" to alarm.token)).build())
            } else schedule(alarm)
        }
        debug.event("rescheduled ${effective.size} alarms; exact=${canScheduleExactAlarms()} allowed=${notificationsAllowed()}")
    }

    internal suspend fun deliver(token: String, weeklyWorker: Boolean = false) = mutex.withLock {
        val alarm = debug.state().alarms.find { it.token == token }
        if (alarm == null) {
            debug.event("stale alarm rejected")
            return@withLock
        }
        val currentClock = localClock()
        val now = currentClock.instant()
        if (now.toEpochMilli() < alarm.triggerMillis) {
            debug.event("early alarm rejected ${alarm.kind}")
            if (alarm.kind != "weekly") schedule(alarm)
            return@withLock
        }
        val settings = settingsRepository.settings.first()
        val valid = settings?.notificationsEnabled == true && now.toEpochMilli() < alarm.expiresMillis && validForCurrentData(alarm, settings, currentClock)
        debug.update { it.copy(alarms = it.alarms.filterNot { entry -> entry.token == token }) }
        if (alarm.kind == "expiry") painChecks.expire()
        else if (valid && alarm.occurrence !in debug.state().delivered) {
            val log = if (alarm.kind == "pain") database.historyDao().getSessionById(alarm.sessionId) else null
            debug.update { it.copy(delivered = (it.delivered + alarm.occurrence).takeLast(512), active = (it.active + alarm).takeLast(128)) }
            factory.show(alarm, log?.let { it.radiating || it.status == SessionStatus.RED } == true)
        } else debug.event("stale or disabled delivery rejected ${alarm.kind}")
        rescheduleLocked(weeklyWorker)
    }

    private suspend fun validForCurrentData(alarm: AlarmRecord, settings: Settings, currentClock: Clock): Boolean {
        val now = currentClock.instant()
        val today = LocalDate.now(currentClock)
        return when (alarm.kind) {
            "session", "recall" -> {
                val entry = database.programDao().observePlan().first().find { it.dayOfWeek == today.dayOfWeek }
                alarm.date == today.toString() && entry?.sessionType?.name == alarm.type &&
                    database.historyDao().getSession(today, SessionType.valueOf(alarm.type)) == null
            }
            "pain" -> painChecks.pending().any { it.id == alarm.sessionId && signature(it) == alarm.signature }
            "office" -> alarm.date == today.toString() && today.dayOfWeek in settings.workDays && !settings.isWorkOffToday(today) &&
                now.atZone(currentClock.zone).toLocalTime() >= settings.workStart && now.atZone(currentClock.zone).toLocalTime() < settings.workEnd
            else -> true
        }
    }

    internal suspend fun handleAction(token: String, command: String) = mutex.withLock {
        val state = debug.state()
        val alarm = state.active.find { it.token == token }
        val currentClock = localClock()
        val settings = settingsRepository.settings.first()
        if (alarm == null || token in state.consumed || settings == null || !settings.notificationsEnabled ||
            alarm.expiresMillis <= currentClock.millis() || !validForCurrentData(alarm, settings, currentClock)) {
            debug.event("stale action rejected $command")
            return@withLock
        }
        if (!NotificationRules.actionAllowed(alarm.kind, command, Instant.ofEpochMilli(alarm.triggerMillis), Instant.ofEpochMilli(alarm.expiresMillis), currentClock.instant(), token in state.consumed)) {
            debug.event("invalid action rejected $command")
            return@withLock
        }
        when (command) {
            "office_done", "office_skip", "office_snooze" -> {
                val action = when (command) {
                    "office_done" -> OfficeBreakAction.DONE
                    "office_snooze" -> OfficeBreakAction.SNOOZED
                    else -> OfficeBreakAction.SKIPPED
                }
                val inserted = database.withTransaction {
                    val timestamp = Instant.ofEpochMilli(alarm.triggerMillis)
                    if (database.historyDao().getOfficeBreaks().none { it.timestamp == timestamp }) {
                        database.historyDao().upsertOfficeBreak(OfficeBreakEvent(timestamp, action))
                        true
                    } else false
                }
                if (!inserted) {
                    debug.event("duplicate office action rejected")
                    return@withLock
                }
                if (command == "office_snooze") {
                    val next = AlarmTriggers.officeSnooze(currentClock, settings)
                    if (next != null) debug.update { it.copy(snoozes = it.snoozes + ("office:${LocalDate.now(currentClock)}" to next.toEpochMilli())) }
                }
            }
            "session_snooze" -> {
                val trigger = AlarmTriggers.sessionSnooze(currentClock, LocalDate.parse(alarm.date))
                if (trigger != null) {
                    debug.update { it.copy(snoozes = it.snoozes + ("session:${alarm.date}:${alarm.type}" to trigger.toEpochMilli())) }
                }
            }
            "session_skip" -> database.withTransaction {
                val date = LocalDate.parse(alarm.date)
                val type = SessionType.valueOf(alarm.type)
                val program = database.programDao().getState()
                if (program != null && database.historyDao().getSession(date, type) == null) {
                    database.historyDao().insertSession(SessionLog(date = date, sessionType = type, phaseId = program.currentPhaseId, outcome = SessionOutcome.SKIPPED, status = SessionStatus.UNVERIFIED))
                }
            }
            "pain_ok" -> {
                val log = database.historyDao().getSessionById(alarm.sessionId)
                if (log == null || log.radiating || log.status == SessionStatus.RED || !painChecks.complete(alarm.sessionId, 3, true)) {
                    debug.event("quick pain check rejected")
                    return@withLock
                }
            }
        }
        debug.update { it.copy(consumed = (it.consumed + token).takeLast(512), active = it.active.filterNot { entry -> entry.token == token }) }
        factory.cancel(alarm)
        debug.event("action completed $command")
        rescheduleLocked()
    }

    private fun record(key: String, kind: String, trigger: Instant, occurrence: String, date: String = "", type: String = "", sessionId: Long = 0, expires: Instant): AlarmRecord =
        AlarmRecord(key, kind, trigger.toEpochMilli(), UUID.randomUUID().toString(), occurrence, date, type, sessionId, expires.toEpochMilli())

    private fun signature(log: SessionLog): String = "${log.date}:${log.sessionType}:${log.outcome}:${log.painDuring}:${log.radiating}:${log.phaseId}"

    private fun pending(alarm: AlarmRecord): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("schiena://alarm/${alarm.token}"))
            .putExtra("token", alarm.token)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(alarm: AlarmRecord) {
        val intent = pending(alarm)
        try {
            if (canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerMillis, intent)
            else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerMillis, intent)
                debug.event("inexact fallback ${alarm.kind}")
            }
        } catch (error: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerMillis, intent)
            debug.event("exact permission changed; inexact fallback ${alarm.kind}")
        }
    }
}