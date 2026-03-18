package com.example.mysmscode.domain

enum class MonitoringControlTransition {
    IDLE,
    STARTING,
    STOPPING,
}

enum class MonitoringRuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
}

enum class MonitoringAction {
    START,
    STARTING,
    STOP,
    STOPPING,
}

data class MonitoringControlState(
    val runtimeState: MonitoringRuntimeState,
    val primaryAction: MonitoringAction,
    val primaryActionEnabled: Boolean,
)

data class MonitoringFailureSummary(
    val totalCount: Int,
    val scheduledCount: Int,
    val exhaustedCount: Int,
    val nonRecoverableCount: Int,
)

data class MonitoringDashboard(
    val recentRecords: List<SmsRecordPreview>,
    val failedAttempts: List<RetryableAttempt>,
    val visibleFailedAttempts: List<RetryableAttempt>,
    val hiddenFailedAttemptCount: Int,
    val failureSummary: MonitoringFailureSummary,
) {
    val hasFailures: Boolean
        get() = failureSummary.totalCount > 0

    val shouldExpandFailureCard: Boolean
        get() = hasFailures
}

fun buildMonitoringControlState(
    isServiceRunning: Boolean,
    transition: MonitoringControlTransition,
): MonitoringControlState {
    return when {
        transition == MonitoringControlTransition.STARTING -> MonitoringControlState(
            runtimeState = MonitoringRuntimeState.STARTING,
            primaryAction = MonitoringAction.STARTING,
            primaryActionEnabled = false,
        )

        transition == MonitoringControlTransition.STOPPING -> MonitoringControlState(
            runtimeState = MonitoringRuntimeState.STOPPING,
            primaryAction = MonitoringAction.STOPPING,
            primaryActionEnabled = false,
        )

        isServiceRunning -> MonitoringControlState(
            runtimeState = MonitoringRuntimeState.RUNNING,
            primaryAction = MonitoringAction.STOP,
            primaryActionEnabled = true,
        )

        else -> MonitoringControlState(
            runtimeState = MonitoringRuntimeState.STOPPED,
            primaryAction = MonitoringAction.START,
            primaryActionEnabled = true,
        )
    }
}

fun buildMonitoringDashboard(
    records: List<SmsRecordPreview>,
    failedAttempts: List<RetryableAttempt>,
    recentRecordLimit: Int = 5,
    visibleFailedAttemptLimit: Int = 3,
): MonitoringDashboard {
    val scheduledCount = failedAttempts.count { it.nextRetryAt != null }
    val nonRecoverableCount = failedAttempts.count { !it.recoverable }
    val exhaustedCount = failedAttempts.count { it.recoverable && it.nextRetryAt == null }
    val visibleFailedAttempts = failedAttempts.take(visibleFailedAttemptLimit)
    return MonitoringDashboard(
        recentRecords = records.take(recentRecordLimit),
        failedAttempts = failedAttempts,
        visibleFailedAttempts = visibleFailedAttempts,
        hiddenFailedAttemptCount = (failedAttempts.size - visibleFailedAttempts.size).coerceAtLeast(0),
        failureSummary = MonitoringFailureSummary(
            totalCount = failedAttempts.size,
            scheduledCount = scheduledCount,
            exhaustedCount = exhaustedCount,
            nonRecoverableCount = nonRecoverableCount,
        ),
    )
}
