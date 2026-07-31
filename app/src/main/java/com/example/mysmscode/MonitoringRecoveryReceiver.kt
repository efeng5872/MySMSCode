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
        handleRecoveryAsync(
            context = context,
            action = intent.action,
            finish = goAsync()::finish,
        )
    }

    internal fun handleRecoveryForTest(context: Context, action: String?) {
        handleRecoveryAsync(
            context = context,
            action = action,
            finish = null,
        )
    }

    private fun handleRecoveryAsync(
        context: Context,
        action: String?,
        finish: (() -> Unit)?,
    ) {
        val trigger = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> MonitoringRecoveryTrigger.BOOT_COMPLETED

            Intent.ACTION_MY_PACKAGE_REPLACED -> MonitoringRecoveryTrigger.PACKAGE_REPLACED
            else -> return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ProcessExitDiagnosticsRecorder(context.applicationContext).recordLatestExitIfNew()
                val settingsRepository = (context.applicationContext as MySmsCodeApplication).container.settingsRepository
                val state = settingsRepository.getMonitoringState()
                if (shouldRecoverMonitoring(state, trigger)) {
                    DebugTraceLogger.d("keepalive_recovery trigger=${trigger.name} action=$action")
                    MonitoringForegroundService.startMonitoring(context.applicationContext, trigger)
                } else {
                    DebugTraceLogger.d("keepalive_recovery_skipped trigger=${trigger.name} enabled=${state.monitoringEnabled} stoppedByUser=${state.stoppedByUser}")
                }
            } finally {
                finish?.invoke()
            }
        }
    }
}
