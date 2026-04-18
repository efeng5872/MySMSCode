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

fun shouldRecoverMonitoring(
    state: MonitoringPersistenceState,
    trigger: MonitoringRecoveryTrigger,
): Boolean {
    if (!state.monitoringEnabled) {
        return false
    }
    if (state.stoppedByUser) {
        return false
    }
    return when (trigger) {
        MonitoringRecoveryTrigger.BOOT_COMPLETED,
        MonitoringRecoveryTrigger.PACKAGE_REPLACED,
        MonitoringRecoveryTrigger.SERVICE_RECOVERY,
        -> true
    }
}
