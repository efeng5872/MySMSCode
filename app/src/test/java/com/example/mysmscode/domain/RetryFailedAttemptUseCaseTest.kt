package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryFailedAttemptUseCaseTest {

    private val useCase = RetryFailedAttemptUseCase()

    @Test
    fun successfulRetry_promotesRecordToSuccessAndIncrementsAttemptNumber() {
        val execution = useCase.retry(
            failedAttempt = RetryableAttempt(
                attemptId = 9L,
                smsRecordId = 3L,
                senderNumber = "10690001",
                messageBody = "Code 1234",
                matchedKeyword = "code",
                receivedAt = 123456789L,
                robotId = 1L,
                robotName = "Ops",
                robotType = RobotType.FEISHU,
                attemptNumber = 1,
                lastErrorMessage = "timeout",
                recoverable = true,
                nextRetryAt = 60_000L,
            ),
            dispatchResult = ForwardDispatchResult(
                robotId = 1L,
                channel = "FEISHU",
                status = ForwardAttemptStatus.SUCCESS,
                responseCode = "200",
                responseMessage = "ok",
                recoverable = false,
            ),
            attemptedAt = 120_000L,
        )

        assertEquals(SmsRecordStatus.SUCCESS, execution.recordStatus)
        assertNull(execution.recordFailureReason)
        assertEquals(2, execution.nextAttempt.attemptNumber)
        assertEquals(ForwardAttemptStatus.SUCCESS, execution.nextAttempt.status)
        assertEquals("200", execution.nextAttempt.responseCode)
        assertNull(execution.nextAttempt.nextRetryAt)
    }

    @Test
    fun failedRetry_keepsRecordFailedAndSchedulesNextAttempt() {
        val execution = useCase.retry(
            failedAttempt = RetryableAttempt(
                attemptId = 9L,
                smsRecordId = 3L,
                senderNumber = "10690001",
                messageBody = "Code 1234",
                matchedKeyword = "code",
                receivedAt = 123456789L,
                robotId = 2L,
                robotName = "Ops WeCom",
                robotType = RobotType.WECOM,
                attemptNumber = 2,
                lastErrorMessage = "connection reset",
                recoverable = true,
                nextRetryAt = 300_000L,
            ),
            dispatchResult = ForwardDispatchResult(
                robotId = 2L,
                channel = "WECOM",
                status = ForwardAttemptStatus.FAILED,
                responseCode = null,
                responseMessage = "timeout",
                recoverable = true,
            ),
            attemptedAt = 120_000L,
        )

        assertEquals(SmsRecordStatus.FAILED, execution.recordStatus)
        assertTrue(execution.recordFailureReason!!.contains("timeout"))
        assertEquals(3, execution.nextAttempt.attemptNumber)
        assertTrue(execution.nextAttempt.recoverable)
        assertEquals(1_020_000L, execution.nextAttempt.nextRetryAt)
    }
}