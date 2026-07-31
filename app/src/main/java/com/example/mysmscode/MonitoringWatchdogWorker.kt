package com.example.mysmscode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mysmscode.domain.MonitoringRecoveryTrigger
import com.example.mysmscode.domain.MonitoringRuntimeEvent
import com.example.mysmscode.domain.MonitoringWatchdogDecision
import com.example.mysmscode.domain.markRuntimeEvent
import com.example.mysmscode.domain.resolveMonitoringWatchdogDecision
import java.util.concurrent.TimeUnit

class MonitoringWatchdogWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val application = applicationContext as MySmsCodeApplication
        val repository = application.container.settingsRepository
        ProcessExitDiagnosticsRecorder(applicationContext).recordLatestExitIfNew()

        val now = System.currentTimeMillis()
        val state = repository.getMonitoringState()
        val decision = resolveMonitoringWatchdogDecision(state, now)
        val checkedState = state.copy(lastWatchdogCheckAt = now)
        when (decision) {
            MonitoringWatchdogDecision.NO_OP -> {
                repository.updateWatchdogCheckAt(now)
                DebugTraceLogger.d("keepalive_watchdog inactive")
            }

            MonitoringWatchdogDecision.HEALTHY -> {
                repository.updateWatchdogCheckAt(now)
                DebugTraceLogger.d("keepalive_watchdog healthy heartbeatAt=${state.lastServiceHeartbeatAt}")
            }

            MonitoringWatchdogDecision.RECOVER -> recoverMonitoring(repository, checkedState, now)
        }
        return Result.success()
    }

    private suspend fun recoverMonitoring(
        repository: com.example.mysmscode.data.RoomSettingsRepository,
        checkedState: com.example.mysmscode.domain.MonitoringPersistenceState,
        now: Long,
    ) {
        repository.saveMonitoringState(
            checkedState.markRuntimeEvent(MonitoringRuntimeEvent.WATCHDOG_STALE, now)
        )
        if (!canAttemptBackgroundRecovery()) {
            recordBlockedRecovery(repository)
            showRecoveryNotification()
            return
        }
        runCatching {
            MonitoringForegroundService.startMonitoring(
                applicationContext,
                MonitoringRecoveryTrigger.WATCHDOG,
            )
        }.onSuccess {
            DebugTraceLogger.d("keepalive_watchdog recovery_requested")
        }.onFailure { error ->
            DebugTraceLogger.w("keepalive_watchdog recovery_blocked error=${error.javaClass.simpleName}")
            recordBlockedRecovery(repository)
            showRecoveryNotification()
        }
    }

    private suspend fun recordBlockedRecovery(repository: com.example.mysmscode.data.RoomSettingsRepository) {
        val latest = repository.getMonitoringState()
        repository.saveMonitoringState(
            latest.markRuntimeEvent(
                MonitoringRuntimeEvent.WATCHDOG_RECOVERY_BLOCKED,
                System.currentTimeMillis(),
            )
        )
    }

    private fun canAttemptBackgroundRecovery(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = applicationContext.getSystemService(PowerManager::class.java)
        return powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)
    }

    private fun showRecoveryNotification() {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                RECOVERY_CHANNEL_ID,
                applicationContext.getString(R.string.notification_recovery_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            RECOVERY_NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, RECOVERY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notification_recovery_required_title))
            .setContentText(applicationContext.getString(R.string.notification_recovery_required_message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(RECOVERY_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val RECOVERY_CHANNEL_ID = "monitoring_recovery_channel"
        private const val RECOVERY_NOTIFICATION_ID = 1003
    }
}

object MonitoringWatchdogScheduler {
    const val UNIQUE_WORK_NAME = "monitoring_watchdog"
    const val WORK_TAG = "monitoring_watchdog_periodic"
    private const val INTERVAL_MINUTES = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonitoringWatchdogWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        DebugTraceLogger.d("keepalive_watchdog_scheduled intervalMinutes=$INTERVAL_MINUTES")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        DebugTraceLogger.d("keepalive_watchdog_cancelled")
    }
}
