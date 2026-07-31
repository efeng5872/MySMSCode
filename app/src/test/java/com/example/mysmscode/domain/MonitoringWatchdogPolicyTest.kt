package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringWatchdogPolicyTest {

    @Test
    fun `inactive monitoring does not recover`() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = true,
            lastServiceHeartbeatAt = 1_000L,
        )

        assertEquals(
            MonitoringWatchdogDecision.NO_OP,
            resolveMonitoringWatchdogDecision(state, now = 10_000L),
        )
    }

    @Test
    fun `fresh heartbeat is healthy`() {
        val now = 100_000L
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
            lastServiceHeartbeatAt = now - MONITORING_WATCHDOG_STALE_AFTER_MS + 1L,
        )

        assertEquals(
            MonitoringWatchdogDecision.HEALTHY,
            resolveMonitoringWatchdogDecision(state, now),
        )
    }

    @Test
    fun `missing or stale heartbeat requests recovery`() {
        val now = MONITORING_WATCHDOG_STALE_AFTER_MS * 2
        val missingHeartbeat = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        )
        val staleHeartbeat = missingHeartbeat.copy(
            lastServiceHeartbeatAt = now - MONITORING_WATCHDOG_STALE_AFTER_MS,
        )

        assertEquals(
            MonitoringWatchdogDecision.RECOVER,
            resolveMonitoringWatchdogDecision(missingHeartbeat, now),
        )
        assertEquals(
            MonitoringWatchdogDecision.RECOVER,
            resolveMonitoringWatchdogDecision(staleHeartbeat, now),
        )
    }

    @Test
    fun `future heartbeat is treated as healthy`() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
            lastServiceHeartbeatAt = 20_000L,
        )

        assertEquals(
            MonitoringWatchdogDecision.HEALTHY,
            resolveMonitoringWatchdogDecision(state, now = 10_000L),
        )
    }
}
