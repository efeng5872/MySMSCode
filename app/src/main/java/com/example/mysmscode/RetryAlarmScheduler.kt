package com.example.mysmscode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class RetryAlarmScheduler(
    private val context: Context,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun schedule(triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
        DebugTraceLogger.d("keepalive_alarm_scheduled triggerAt=$triggerAtMillis")
    }

    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        DebugTraceLogger.d("keepalive_alarm_cancelled")
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, RetryAlarmReceiver::class.java).apply {
            action = RetryAlarmReceiver.ACTION_PROCESS_DUE_RETRIES
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_RETRY_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val REQUEST_CODE_RETRY_ALARM = 2001
    }
}
