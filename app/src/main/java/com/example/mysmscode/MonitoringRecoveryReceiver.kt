package com.example.mysmscode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mysmscode.domain.MonitoringRecoveryTrigger
import com.example.mysmscode.domain.shouldRecoverMonitoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitoringRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> MonitoringRecoveryTrigger.BOOT_COMPLETED

            Intent.ACTION_MY_PACKAGE_REPLACED -> MonitoringRecoveryTrigger.PACKAGE_REPLACED
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsRepository = (context.applicationContext as MySmsCodeApplication).container.settingsRepository
                val state = settingsRepository.getMonitoringState()
                if (shouldRecoverMonitoring(state, trigger)) {
                    DebugTraceLogger.d("keepalive_recovery trigger=${trigger.name} action=${intent.action}")
                    MonitoringForegroundService.startMonitoring(context.applicationContext, trigger)
                } else {
                    DebugTraceLogger.d("keepalive_recovery_skipped trigger=${trigger.name} enabled=${state.monitoringEnabled} stoppedByUser=${state.stoppedByUser}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
