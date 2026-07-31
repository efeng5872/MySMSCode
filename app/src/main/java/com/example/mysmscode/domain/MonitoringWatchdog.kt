package com.example.mysmscode.domain

const val MONITORING_WATCHDOG_STALE_AFTER_MS = 45 * 60 * 1_000L

enum class MonitoringWatchdogDecision {
    NO_OP,
    HEALTHY,
    RECOVER,
}

fun resolveMonitoringWatchdogDecision(
    state: MonitoringPersistenceState,
    now: Long,
): MonitoringWatchdogDecision {
    if (!state.isMonitoringActive()) {
        return MonitoringWatchdogDecision.NO_OP
    }
    val heartbeatAt = state.lastServiceHeartbeatAt ?: return MonitoringWatchdogDecision.RECOVER
    return if (now - heartbeatAt >= MONITORING_WATCHDOG_STALE_AFTER_MS) {
        MonitoringWatchdogDecision.RECOVER
    } else {
        MonitoringWatchdogDecision.HEALTHY
    }
}
