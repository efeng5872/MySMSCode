package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateProcessingOutcomeUseCaseTest {

    private val useCase = CreateProcessingOutcomeUseCase()

    @Test
    fun pendingForward_createsRecordAndAttempts() {
        val result = requireNotNull(
            useCase.create(
                senderNumber = "10690001",
                messageBody = "Code 1234",
                source = SmsSource.REAL_SMS,
                processingResult = SmsProcessingResult.PendingForward(
                    record = SmsProcessingRecord(
                        senderNumber = "10690001",
                        messageBody = "Code 1234",
                        status = SmsProcessingStatus.PENDING_FORWARD,
                        matchedKeyword = "code",
                    ),
                    attempts = listOf(
                        ForwardPlan(robotId = 1L, robotType = RobotType.FEISHU),
                        ForwardPlan(robotId = 2L, robotType = RobotType.WECOM),
                    )
                ),
                receivedAt = 123456789L,
            )
        )

        assertEquals(SmsRecordStatus.PENDING_FORWARD, result.record.status)
        assertEquals("code", result.record.matchedKeyword)
        assertEquals(2, result.attempts.size)
        assertEquals(ForwardAttemptStatus.PENDING, result.attempts.first().status)
        assertEquals("FEISHU", result.attempts.first().channel)
    }

    @Test
    fun notMatched_createsRecordWithoutAttempts() {
        val result = requireNotNull(
            useCase.create(
                senderNumber = "10690001",
                messageBody = "Balance update",
                source = SmsSource.REAL_SMS,
                processingResult = SmsProcessingResult.NotMatched(
                    record = SmsProcessingRecord(
                        senderNumber = "10690001",
                        messageBody = "Balance update",
                        status = SmsProcessingStatus.NOT_MATCHED,
                    )
                ),
                receivedAt = 123456789L,
            )
        )

        assertEquals(SmsRecordStatus.NOT_MATCHED, result.record.status)
        assertTrue(result.attempts.isEmpty())
    }

    @Test
    fun configurationFailed_createsRecordWithoutAttempts() {
        val result = requireNotNull(
            useCase.create(
                senderNumber = "10690001",
                messageBody = "Code 1234",
                source = SmsSource.SIMULATION,
                processingResult = SmsProcessingResult.ConfigurationFailed(
                    record = SmsProcessingRecord(
                        senderNumber = "10690001",
                        messageBody = "Code 1234",
                        status = SmsProcessingStatus.CONFIGURATION_FAILED,
                        failureReason = "No enabled robot endpoint selected for matched rule.",
                    )
                ),
                receivedAt = 123456789L,
            )
        )

        assertEquals(SmsRecordStatus.CONFIGURATION_FAILED, result.record.status)
        assertEquals(SmsSource.SIMULATION, result.record.source)
        assertEquals("No enabled robot endpoint selected for matched rule.", result.record.failureReason)
        assertTrue(result.attempts.isEmpty())
    }

    @Test
    fun ignored_returnsNoOutcome() {
        val result = useCase.create(
            senderNumber = "10690001",
            messageBody = "ignored",
            source = SmsSource.REAL_SMS,
            processingResult = SmsProcessingResult.Ignored,
            receivedAt = 123456789L,
        )

        assertNull(result)
    }
}