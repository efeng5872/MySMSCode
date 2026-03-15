package com.example.mysmscode.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class DebugTraceMessagesTest {

    @Test
    fun incomingSmsTrace_summarizesSenderLengthAndPartCount() {
        val trace = buildIncomingSmsTrace(
            senderNumber = "10690001",
            messageBody = "Your verification code is 123456",
            partCount = 2,
        )

        assertTrue(trace.contains("sender=10690001"))
        assertTrue(trace.contains("parts=2"))
        assertTrue(trace.contains("length=32"))
    }

    @Test
    fun processingTrace_summarizesResultAndAttemptCount() {
        val trace = buildProcessingTrace(
            source = SmsSource.REAL_SMS,
            result = SmsProcessingResult.PendingForward(
                record = SmsProcessingRecord(
                    senderNumber = "10690001",
                    messageBody = "Your verification code is 123456",
                    status = SmsProcessingStatus.PENDING_FORWARD,
                    matchedKeyword = "verification code",
                ),
                attempts = listOf(
                    ForwardPlan(robotId = 1L, robotType = RobotType.FEISHU),
                    ForwardPlan(robotId = 2L, robotType = RobotType.WECOM),
                ),
            ),
        )

        assertTrue(trace.contains("source=REAL_SMS"))
        assertTrue(trace.contains("sender=10690001"))
        assertTrue(trace.contains("status=PENDING_FORWARD"))
        assertTrue(trace.contains("attempts=2"))
        assertTrue(trace.contains("keyword=verification code"))
    }

    @Test
    fun persistenceTrace_summarizesRecordStatusAndAttempts() {
        val trace = buildPersistenceTrace(
            outcome = ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = "10690001",
                    messageBody = "Your verification code is 123456",
                    receivedAt = 123456789L,
                    source = SmsSource.SIMULATION,
                    status = SmsRecordStatus.FAILED,
                    matchedKeyword = "verification code",
                    failureReason = "FEISHU: timeout",
                ),
                attempts = listOf(
                    ForwardAttemptDraft(
                        robotId = 1L,
                        channel = "FEISHU",
                        attemptNumber = 1,
                        status = ForwardAttemptStatus.FAILED,
                        recoverable = true,
                        responseMessage = "timeout",
                        nextRetryAt = 123456999L,
                    ),
                ),
            ),
        )

        assertTrue(trace.contains("source=SIMULATION"))
        assertTrue(trace.contains("sender=10690001"))
        assertTrue(trace.contains("recordStatus=FAILED"))
        assertTrue(trace.contains("attempts=1"))
        assertTrue(trace.contains("failure=FEISHU: timeout"))
    }
}
