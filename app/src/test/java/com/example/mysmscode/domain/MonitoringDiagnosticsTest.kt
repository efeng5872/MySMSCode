package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoringDiagnosticsTest {

    @Test
    fun `running service is healthy even when an older user stop exists`() {
        val snapshot = buildMonitoringDiagnosticSnapshot(
            state = MonitoringPersistenceState(
                monitoringEnabled = true,
                stoppedByUser = false,
                lastMonitoringStartedAt = 2_000L,
                lastMonitoringStoppedAt = 1_000L,
                lastServiceHeartbeatAt = 2_500L,
            ),
            isServiceRunning = true,
        )

        assertEquals(MonitoringRuntimeHealth.RUNNING, snapshot.health)
        assertNull(snapshot.relevantUserStoppedAt)
    }

    @Test
    fun `active monitoring without a running service is interrupted`() {
        val snapshot = buildMonitoringDiagnosticSnapshot(
            state = MonitoringPersistenceState(
                monitoringEnabled = true,
                stoppedByUser = false,
                lastMonitoringStartedAt = 2_000L,
                lastServiceHeartbeatAt = 2_500L,
            ),
            isServiceRunning = false,
        )

        assertEquals(MonitoringRuntimeHealth.INTERRUPTED, snapshot.health)
        assertEquals(2_500L, snapshot.lastHeartbeatAt)
    }

    @Test
    fun `user stopped monitoring reports the current stop`() {
        val snapshot = buildMonitoringDiagnosticSnapshot(
            state = MonitoringPersistenceState(
                monitoringEnabled = false,
                stoppedByUser = true,
                lastMonitoringStartedAt = 1_000L,
                lastMonitoringStoppedAt = 2_000L,
            ),
            isServiceRunning = false,
        )

        assertEquals(MonitoringRuntimeHealth.STOPPED_BY_USER, snapshot.health)
        assertEquals(2_000L, snapshot.relevantUserStoppedAt)
    }

    @Test
    fun `sticky restart records recovery event and heartbeat`() {
        val recovered = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        ).markMonitoringStarted(
            now = 3_000L,
            recoveryTriggerName = MonitoringRecoveryTrigger.SERVICE_RECOVERY.name,
        )

        assertEquals(3_000L, recovered.lastServiceHeartbeatAt)
        assertEquals(3_000L, recovered.lastRuntimeEventAt)
        assertEquals(MonitoringRuntimeEvent.RECOVERED.name, recovered.lastRuntimeEvent)
        assertEquals(MonitoringRecoveryTrigger.SERVICE_RECOVERY.name, recovered.lastRecoveryTrigger)
    }

    @Test
    fun `manual start preserves previous recovery history`() {
        val started = MonitoringPersistenceState(
            lastRecoveryStartedAt = 2_000L,
            lastRecoveryTrigger = MonitoringRecoveryTrigger.SERVICE_RECOVERY.name,
        ).markMonitoringStarted(
            now = 3_000L,
            recoveryTriggerName = null,
        )

        assertEquals(2_000L, started.lastRecoveryStartedAt)
        assertEquals(MonitoringRecoveryTrigger.SERVICE_RECOVERY.name, started.lastRecoveryTrigger)
        assertEquals(MonitoringRuntimeEvent.STARTED.name, started.lastRuntimeEvent)
    }

    @Test
    fun `new process exit observation is stored`() {
        val updated = MonitoringPersistenceState().recordProcessExit(
            ProcessExitObservation(
                timestamp = 4_000L,
                reason = ProcessExitReason.LOW_MEMORY,
                description = "系统内存压力",
            )
        )

        assertEquals(4_000L, updated.lastProcessExitAt)
        assertEquals(ProcessExitReason.LOW_MEMORY.name, updated.lastProcessExitReason)
        assertEquals("系统内存压力", updated.lastProcessExitDescription)
    }

    @Test
    fun `older process exit observation does not replace current diagnosis`() {
        val state = MonitoringPersistenceState(
            lastProcessExitAt = 4_000L,
            lastProcessExitReason = ProcessExitReason.LOW_MEMORY.name,
            lastProcessExitDescription = "系统内存压力",
        )

        val unchanged = state.recordProcessExit(
            ProcessExitObservation(
                timestamp = 3_000L,
                reason = ProcessExitReason.USER_REQUESTED,
                description = "旧记录",
            )
        )

        assertEquals(state, unchanged)
    }
}
