package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchDashboardModelTest {

    @Test
    fun buildMonitoringControlState_returnsStartActionWhenServiceIsStopped() {
        val state = buildMonitoringControlState(
            isServiceRunning = false,
            transition = MonitoringControlTransition.IDLE,
        )

        assertEquals(MonitoringRuntimeState.STOPPED, state.runtimeState)
        assertEquals(MonitoringAction.START, state.primaryAction)
        assertTrue(state.primaryActionEnabled)
    }

    @Test
    fun buildMonitoringControlState_returnsStartingActionWhileStartIsPending() {
        val state = buildMonitoringControlState(
            isServiceRunning = false,
            transition = MonitoringControlTransition.STARTING,
        )

        assertEquals(MonitoringRuntimeState.STARTING, state.runtimeState)
        assertEquals(MonitoringAction.STARTING, state.primaryAction)
        assertFalse(state.primaryActionEnabled)
    }

    @Test
    fun buildMonitoringControlState_returnsStopActionWhenServiceIsRunning() {
        val state = buildMonitoringControlState(
            isServiceRunning = true,
            transition = MonitoringControlTransition.IDLE,
        )

        assertEquals(MonitoringRuntimeState.RUNNING, state.runtimeState)
        assertEquals(MonitoringAction.STOP, state.primaryAction)
        assertTrue(state.primaryActionEnabled)
    }

    @Test
    fun buildMonitoringControlState_returnsStoppingActionWhileStopIsPending() {
        val state = buildMonitoringControlState(
            isServiceRunning = true,
            transition = MonitoringControlTransition.STOPPING,
        )

        assertEquals(MonitoringRuntimeState.STOPPING, state.runtimeState)
        assertEquals(MonitoringAction.STOPPING, state.primaryAction)
        assertFalse(state.primaryActionEnabled)
    }

    @Test
    fun buildMonitoringDashboard_limitsRecentRecordsToFive() {
        val records = (1..7).map { index ->
            SmsRecordPreview(
                senderNumber = "sender-$index",
                messageBody = "message-$index",
                status = "SUCCESS",
                source = "REAL_SMS",
                receivedAt = index.toLong(),
            )
        }

        val dashboard = buildMonitoringDashboard(records = records, failedAttempts = emptyList())

        assertEquals(5, dashboard.recentRecords.size)
        assertEquals("sender-1", dashboard.recentRecords.first().senderNumber)
        assertEquals("sender-5", dashboard.recentRecords.last().senderNumber)
    }

    @Test
    fun buildMonitoringDashboard_summarizesFailureBuckets() {
        val failedAttempts = listOf(
            retryableAttempt(attemptId = 1L, recoverable = true, nextRetryAt = 1000L),
            retryableAttempt(attemptId = 2L, recoverable = true, nextRetryAt = null),
            retryableAttempt(attemptId = 3L, recoverable = false, nextRetryAt = null),
        )

        val dashboard = buildMonitoringDashboard(records = emptyList(), failedAttempts = failedAttempts)

        assertTrue(dashboard.hasFailures)
        assertTrue(dashboard.shouldExpandFailureCard)
        assertEquals(3, dashboard.failureSummary.totalCount)
        assertEquals(1, dashboard.failureSummary.scheduledCount)
        assertEquals(1, dashboard.failureSummary.exhaustedCount)
        assertEquals(1, dashboard.failureSummary.nonRecoverableCount)
        assertEquals(3, dashboard.failedAttempts.size)
        assertEquals(3, dashboard.visibleFailedAttempts.size)
    }

    @Test
    fun buildMonitoringDashboard_marksNoFailuresWhenQueueIsEmpty() {
        val dashboard = buildMonitoringDashboard(records = emptyList(), failedAttempts = emptyList())

        assertFalse(dashboard.hasFailures)
        assertFalse(dashboard.shouldExpandFailureCard)
        assertEquals(0, dashboard.failureSummary.totalCount)
        assertEquals(0, dashboard.failureSummary.scheduledCount)
        assertEquals(0, dashboard.failureSummary.exhaustedCount)
        assertEquals(0, dashboard.failureSummary.nonRecoverableCount)
        assertTrue(dashboard.visibleFailedAttempts.isEmpty())
    }

    @Test
    fun buildMonitoringDashboard_limitsVisibleFailedAttemptsToThree() {
        val failedAttempts = (1..5).map { index ->
            retryableAttempt(
                attemptId = index.toLong(),
                recoverable = true,
                nextRetryAt = 1000L + index,
            )
        }

        val dashboard = buildMonitoringDashboard(records = emptyList(), failedAttempts = failedAttempts)

        assertEquals(5, dashboard.failureSummary.totalCount)
        assertEquals(3, dashboard.visibleFailedAttempts.size)
        assertEquals(2, dashboard.hiddenFailedAttemptCount)
    }

    private fun retryableAttempt(
        attemptId: Long,
        recoverable: Boolean,
        nextRetryAt: Long?,
    ): RetryableAttempt = RetryableAttempt(
        attemptId = attemptId,
        smsRecordId = 100L + attemptId,
        senderNumber = "sender-$attemptId",
        messageBody = "message-$attemptId",
        matchedKeyword = "code",
        receivedAt = 1000L + attemptId,
        robotId = 10L + attemptId,
        robotName = "Robot $attemptId",
        robotType = RobotType.FEISHU,
        attemptNumber = 1,
        lastErrorMessage = "failure-$attemptId",
        recoverable = recoverable,
        nextRetryAt = nextRetryAt,
    )
}

