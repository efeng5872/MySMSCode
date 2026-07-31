package com.example.mysmscode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mysmscode.domain.RetryAlarmSchedulingMode
import com.example.mysmscode.domain.resolveRetryAlarmSchedulingMode

class RetryAlarmScheduler(
    private val context: Context,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun schedule(triggerAtMillis: Long) {
        val pendingIntent = buildPendingIntent()
        val mode = resolveRetryAlarmSchedulingMode(
            sdkInt = Build.VERSION.SDK_INT,
            canScheduleExactAlarms = canScheduleExactAlarms(),
        )
        try {
            scheduleWithMode(mode, triggerAtMillis, pendingIntent)
            DebugTraceLogger.d("keepalive_alarm_scheduled triggerAt=$triggerAtMillis mode=${mode.name}")
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                throw error
            }
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            DebugTraceLogger.w(
                "keepalive_alarm_exact_denied triggerAt=$triggerAtMillis fallback=${RetryAlarmSchedulingMode.INEXACT_ALLOW_IDLE.name}"
            )
        }
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

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun scheduleWithMode(
        mode: RetryAlarmSchedulingMode,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
    ) {
        when (mode) {
            RetryAlarmSchedulingMode.EXACT -> alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )

            RetryAlarmSchedulingMode.EXACT_ALLOW_IDLE -> alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )

            RetryAlarmSchedulingMode.INEXACT_ALLOW_IDLE -> alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    companion object {
        private const val REQUEST_CODE_RETRY_ALARM = 2001
    }
}
