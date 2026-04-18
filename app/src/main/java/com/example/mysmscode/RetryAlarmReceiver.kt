package com.example.mysmscode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RetryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROCESS_DUE_RETRIES) {
            return
        }
        DebugTraceLogger.d("keepalive_alarm_triggered action=${intent.action}")
        MonitoringForegroundService.processDueRetries(context.applicationContext)
    }

    companion object {
        const val ACTION_PROCESS_DUE_RETRIES = "com.example.mysmscode.action.PROCESS_DUE_RETRIES"
    }
}
