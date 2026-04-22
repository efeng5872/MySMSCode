package com.example.mysmscode.domain

enum class MonitoringRecoveryTrigger {
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    SERVICE_RECOVERY,
}

data class MonitoringPersistenceState(
    val monitoringEnabled: Boolean = false,
    val stoppedByUser: Boolean = false,
    val lastMonitoringStartedAt: Long? = null,
    val lastMonitoringStoppedAt: Long? = null,
    val lastRecoveryStartedAt: Long? = null,
    val lastRecoveryTrigger: String? = null,
)

fun MonitoringPersistenceState.isMonitoringActive(): Boolean {
    return monitoringEnabled && !stoppedByUser
}

fun MonitoringPersistenceState.shouldProcessIncomingSms(): Boolean {
    return isMonitoringActive()
}

fun MonitoringPersistenceState.shouldProcessRetryWork(): Boolean {
    return isMonitoringActive()
}

fun MonitoringPersistenceState.markMonitoringStarted(
    now: Long,
    recoveryTriggerName: String?,
): MonitoringPersistenceState {
    return copy(
        monitoringEnabled = true,
        stoppedByUser = false,
        lastMonitoringStartedAt = now,
        lastRecoveryStartedAt = recoveryTriggerName?.let { now },
        lastRecoveryTrigger = recoveryTriggerName,
    )
}

fun MonitoringPersistenceState.markMonitoringStoppedByUser(now: Long): MonitoringPersistenceState {
    return copy(
        monitoringEnabled = false,
        stoppedByUser = true,
        lastMonitoringStoppedAt = now,
    )
}

fun shouldRecoverMonitoring(
    state: MonitoringPersistenceState,
    trigger: MonitoringRecoveryTrigger,
): Boolean {
    if (!state.isMonitoringActive()) {
        return false
    }
    return when (trigger) {
        MonitoringRecoveryTrigger.BOOT_COMPLETED,
        MonitoringRecoveryTrigger.PACKAGE_REPLACED,
        MonitoringRecoveryTrigger.SERVICE_RECOVERY,
        -> true
    }
}
