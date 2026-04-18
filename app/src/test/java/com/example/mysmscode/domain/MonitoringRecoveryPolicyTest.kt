package com.example.mysmscode.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringRecoveryPolicyTest {

    @Test
    fun shouldRecover_returnsTrue_forBootWhenMonitoringEnabledAndNotStoppedByUser() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        )

        assertTrue(
            shouldRecoverMonitoring(
                state = state,
                trigger = MonitoringRecoveryTrigger.BOOT_COMPLETED,
            )
        )
    }

    @Test
    fun shouldRecover_returnsTrue_forPackageReplacedWhenMonitoringEnabledAndNotStoppedByUser() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = true,
            stoppedByUser = false,
        )

        assertTrue(
            shouldRecoverMonitoring(
                state = state,
                trigger = MonitoringRecoveryTrigger.PACKAGE_REPLACED,
            )
        )
    }

    @Test
    fun shouldRecover_returnsFalse_whenMonitoringDisabled() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = false,
        )

        assertFalse(
            shouldRecoverMonitoring(
                state = state,
                trigger = MonitoringRecoveryTrigger.BOOT_COMPLETED,
            )
        )
    }

    @Test
    fun shouldRecover_returnsFalse_whenStoppedByUser() {
        val state = MonitoringPersistenceState(
            monitoringEnabled = false,
            stoppedByUser = true,
        )

        assertFalse(
            shouldRecoverMonitoring(
                state = state,
                trigger = MonitoringRecoveryTrigger.PACKAGE_REPLACED,
            )
        )
    }
}
