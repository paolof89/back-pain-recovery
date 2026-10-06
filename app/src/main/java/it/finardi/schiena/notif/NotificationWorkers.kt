package it.finardi.schiena.notif

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationWorkerEntryPoint {
    fun scheduler(): AlarmScheduler
    fun debug(): DebugStore
}

class WeeklyReviewWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, NotificationWorkerEntryPoint::class.java)
        return try {
            val token = inputData.getString("token") ?: return Result.failure()
            dependencies.scheduler().deliver(token, weeklyWorker = true)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            dependencies.debug().event("weekly worker failed: ${error.javaClass.simpleName}")
            Result.retry()
        }
    }
}

class NotificationMaintenanceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, NotificationWorkerEntryPoint::class.java)
        return try {
            dependencies.scheduler().reschedule()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            dependencies.debug().event("maintenance worker failed: ${error.javaClass.simpleName}")
            Result.retry()
        }
    }
}