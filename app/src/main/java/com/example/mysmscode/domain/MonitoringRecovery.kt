package com.example.mysmscode.domain

enum class MonitoringRecoveryTrigger {
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    SERVICE_RECOVERY,
    APP_RESUME,
    WATCHDOG,
}

enum class MonitoringRuntimeEvent {
    STARTED,
    RECOVERED,
    USER_STOPPED,
    HEARTBEAT,
    INTERRUPTION_DETECTED,
    SERVICE_DESTROYED,
    TASK_REMOVED,
    SERVICE_TIMEOUT,
    WATCHDOG_STALE,
    WATCHDOG_RECOVERY_BLOCKED,
}

enum class MonitoringRuntimeHealth {
    RUNNING,
    INTERRUPTED,
    STOPPED_BY_USER,
    DISABLED,
}

data class MonitoringPersistenceState(
    val monitoringEnabled: Boolean = false,
    val stoppedByUser: Boolean = false,
    val lastMonitoringStartedAt: Long? = null,
    val lastMonitoringStoppedAt: Long? = null,
    val lastRecoveryStartedAt: Long? = null,
    val lastRecoveryTrigger: String? = null,
    val lastServiceHeartbeatAt: Long? = null,
    val lastRuntimeEventAt: Long? = null,
    val lastRuntimeEvent: String? = null,
    val lastWatchdogCheckAt: Long? = null,
    val lastProcessExitAt: Long? = null,
    val lastProcessExitReason: String? = null,
    val lastProcessExitDescription: String? = null,
)

data class MonitoringDiagnosticSnapshot(
    val health: MonitoringRuntimeHealth,
    val lastHeartbeatAt: Long?,
    val lastRuntimeEventAt: Long?,
    val lastRuntimeEvent: String?,
    val relevantUserStoppedAt: Long?,
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
    val isRecovery = recoveryTriggerName != null
    return copy(
        monitoringEnabled = true,
        stoppedByUser = false,
        lastMonitoringStartedAt = now,
        lastRecoveryStartedAt = recoveryTriggerName?.let { now } ?: lastRecoveryStartedAt,
        lastRecoveryTrigger = recoveryTriggerName ?: lastRecoveryTrigger,
        lastServiceHeartbeatAt = now,
        lastRuntimeEventAt = now,
        lastRuntimeEvent = if (isRecovery) MonitoringRuntimeEvent.RECOVERED.name else MonitoringRuntimeEvent.STARTED.name,
    )
}

fun MonitoringPersistenceState.markMonitoringStoppedByUser(now: Long): MonitoringPersistenceState {
    return copy(
        monitoringEnabled = false,
        stoppedByUser = true,
        lastMonitoringStoppedAt = now,
        lastRuntimeEventAt = now,
        lastRuntimeEvent = MonitoringRuntimeEvent.USER_STOPPED.name,
    )
}

fun MonitoringPersistenceState.markServiceHeartbeat(now: Long): MonitoringPersistenceState {
    return copy(lastServiceHeartbeatAt = now)
}

fun MonitoringPersistenceState.markRuntimeEvent(
    event: MonitoringRuntimeEvent,
    now: Long,
): MonitoringPersistenceState {
    return copy(
        lastRuntimeEventAt = now,
        lastRuntimeEvent = event.name,
    )
}

fun buildMonitoringDiagnosticSnapshot(
    state: MonitoringPersistenceState,
    isServiceRunning: Boolean,
): MonitoringDiagnosticSnapshot {
    val health = when {
        state.stoppedByUser -> MonitoringRuntimeHealth.STOPPED_BY_USER
        !state.monitoringEnabled -> MonitoringRuntimeHealth.DISABLED
        isServiceRunning -> MonitoringRuntimeHealth.RUNNING
        else -> MonitoringRuntimeHealth.INTERRUPTED
    }
    val relevantUserStoppedAt = state.lastMonitoringStoppedAt?.takeIf { stoppedAt ->
        health == MonitoringRuntimeHealth.STOPPED_BY_USER &&
            stoppedAt >= (state.lastMonitoringStartedAt ?: Long.MIN_VALUE)
    }
    return MonitoringDiagnosticSnapshot(
        health = health,
        lastHeartbeatAt = state.lastServiceHeartbeatAt,
        lastRuntimeEventAt = state.lastRuntimeEventAt,
        lastRuntimeEvent = state.lastRuntimeEvent,
        relevantUserStoppedAt = relevantUserStoppedAt,
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
        MonitoringRecoveryTrigger.APP_RESUME,
        MonitoringRecoveryTrigger.WATCHDOG,
        -> true
    }
}
