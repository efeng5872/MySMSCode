package com.example.mysmscode

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.example.mysmscode.domain.ProcessExitObservation
import com.example.mysmscode.domain.ProcessExitReason

class ProcessExitDiagnosticsRecorder(
    private val context: Context,
) {
    suspend fun recordLatestExitIfNew() {
        runCatching {
            recordLatestExitIfNewInternal()
        }.onFailure { error ->
            DebugTraceLogger.w("process_exit_diagnostics_failed error=${error.javaClass.simpleName}")
        }
    }

    private suspend fun recordLatestExitIfNewInternal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val exitInfo = activityManager.getHistoricalProcessExitReasons(
            context.packageName,
            0,
            1,
        ).firstOrNull() ?: return
        val repository = (context.applicationContext as MySmsCodeApplication).container.settingsRepository
        val observation = exitInfo.toObservation()
        if (repository.recordProcessExitIfNew(observation)) {
            DebugTraceLogger.d(
                "process_exit_recorded reason=${observation.reason.name} at=${observation.timestamp}"
            )
        }
    }

    private fun ApplicationExitInfo.toObservation(): ProcessExitObservation {
        return ProcessExitObservation(
            timestamp = timestamp,
            reason = reason.toDomainReason(),
            description = description
                ?.trim()
                ?.take(MAX_DESCRIPTION_LENGTH)
                ?.takeIf(String::isNotEmpty),
        )
    }

    private fun Int.toDomainReason(): ProcessExitReason = when (this) {
        ApplicationExitInfo.REASON_EXIT_SELF -> ProcessExitReason.EXIT_SELF
        ApplicationExitInfo.REASON_SIGNALED -> ProcessExitReason.SIGNALED
        ApplicationExitInfo.REASON_LOW_MEMORY -> ProcessExitReason.LOW_MEMORY
        ApplicationExitInfo.REASON_CRASH -> ProcessExitReason.CRASH
        ApplicationExitInfo.REASON_CRASH_NATIVE -> ProcessExitReason.CRASH_NATIVE
        ApplicationExitInfo.REASON_ANR -> ProcessExitReason.ANR
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> ProcessExitReason.INITIALIZATION_FAILURE
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> ProcessExitReason.PERMISSION_CHANGE
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> ProcessExitReason.EXCESSIVE_RESOURCE_USAGE
        ApplicationExitInfo.REASON_USER_REQUESTED -> ProcessExitReason.USER_REQUESTED
        ApplicationExitInfo.REASON_USER_STOPPED -> ProcessExitReason.USER_STOPPED
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> ProcessExitReason.DEPENDENCY_DIED
        ApplicationExitInfo.REASON_OTHER -> ProcessExitReason.OTHER
        ApplicationExitInfo.REASON_FREEZER -> ProcessExitReason.FREEZER
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> ProcessExitReason.PACKAGE_STATE_CHANGE
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> ProcessExitReason.PACKAGE_UPDATED
        else -> ProcessExitReason.UNKNOWN
    }

    companion object {
        private const val MAX_DESCRIPTION_LENGTH = 200
    }
}
