package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringProcessingPolicyTest {

    @Test
    fun shouldProcessIncomingSms_returnsFalse_whenStoppedByUser() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = true,
        )

        assertFalse(state.shouldProcessIncomingSms())
    }

    @Test
    fun shouldProcessIncomingSms_returnsFalse_whenMonitoringDisabled() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = false,
        )

        assertFalse(state.shouldProcessIncomingSms())
    }

    @Test
    fun shouldProcessIncomingSms_returnsTrue_whenMonitoringActive() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        )

        assertTrue(state.shouldProcessIncomingSms())
    }

    @Test
    fun shouldProcessRetryWork_returnsFalse_whenStoppedByUser() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = true,
        )

        assertFalse(state.shouldProcessRetryWork())
    }

    @Test
    fun shouldProcessRetryWork_returnsTrue_whenMonitoringActive() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        )

        assertTrue(state.shouldProcessRetryWork())
    }

    @Test
    fun markMonitoringStoppedByUser_marksStateAsStopped() {
        val initialState = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
            lastMonitoringStartedAt = 100L,
            lastRecoveryStartedAt = 200L,
            lastRecoveryTrigger = MonitoringRecoveryTrigger.BOOT_COMPLETED.name,
        )

        val stoppedState = initialState.markMonitoringStoppedByUser(now = 300L)

        assertFalse(stoppedState.monitoringEnabled)
        assertTrue(stoppedState.stoppedByUser)
        assertEquals(300L, stoppedState.lastMonitoringStoppedAt)
        assertEquals(100L, stoppedState.lastMonitoringStartedAt)
        assertEquals(200L, stoppedState.lastRecoveryStartedAt)
        assertEquals(MonitoringRecoveryTrigger.BOOT_COMPLETED.name, stoppedState.lastRecoveryTrigger)
    }

    @Test
    fun markMonitoringStarted_updatesRecoveryMetadataOnlyWhenTriggerExists() {
        val initialState = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = true,
            lastMonitoringStoppedAt = 300L,
        )

        val startedState = initialState.markMonitoringStarted(
            now = 500L,
            recoveryTriggerName = null,
        )

        assertTrue(startedState.monitoringEnabled)
        assertFalse(startedState.stoppedByUser)
        assertEquals(500L, startedState.lastMonitoringStartedAt)
        assertEquals(300L, startedState.lastMonitoringStoppedAt)
        assertNull(startedState.lastRecoveryStartedAt)
        assertNull(startedState.lastRecoveryTrigger)
    }

    @Test
    fun markMonitoringStarted_updatesRecoveryMetadataWhenTriggerExists() {
        val startedState = MonitoringPersistenceState().markMonitoringStarted(
            now = 700L,
            recoveryTriggerName = MonitoringRecoveryTrigger.PACKAGE_REPLACED.name,
        )

        assertTrue(startedState.monitoringEnabled)
        assertFalse(startedState.stoppedByUser)
        assertEquals(700L, startedState.lastMonitoringStartedAt)
        assertEquals(700L, startedState.lastRecoveryStartedAt)
        assertEquals(MonitoringRecoveryTrigger.PACKAGE_REPLACED.name, startedState.lastRecoveryTrigger)
    }
}
