package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinalizeForwardingOutcomeUseCaseTest {

    private val useCase = FinalizeForwardingOutcomeUseCase()

    @Test
    fun allSuccessfulDispatches_markRecordAsSuccess() {
        val finalized = useCase.finalize(
            outcome = ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = "10690001",
                    messageBody = "Code 1234",
                    receivedAt = 123456789L,
                    source = SmsSource.REAL_SMS,
                    status = SmsRecordStatus.PENDING_FORWARD,
                    matchedKeyword = "code",
                ),
                attempts = listOf(
                    ForwardAttemptDraft(robotId = 1L, channel = "FEISHU", attemptNumber = 1, status = ForwardAttemptStatus.PENDING, recoverable = true),
                    ForwardAttemptDraft(robotId = 2L, channel = "WECOM", attemptNumber = 1, status = ForwardAttemptStatus.PENDING, recoverable = true),
                ),
            ),
            results = listOf(
                ForwardDispatchResult(robotId = 1L, channel = "FEISHU", status = ForwardAttemptStatus.SUCCESS, responseCode = "200", responseMessage = "ok", recoverable = false),
                ForwardDispatchResult(robotId = 2L, channel = "WECOM", status = ForwardAttemptStatus.SUCCESS, responseCode = "200", responseMessage = "ok", recoverable = false),
            ),
        )

        assertEquals(SmsRecordStatus.SUCCESS, finalized.record.status)
        assertNull(finalized.record.failureReason)
        assertTrue(finalized.attempts.all { it.status == ForwardAttemptStatus.SUCCESS })
        assertEquals("200", finalized.attempts.first().responseCode)
    }

    @Test
    fun failedDispatch_marksRecordAsFailedAndCarriesReason() {
        val finalized = useCase.finalize(
            outcome = ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = "10690001",
                    messageBody = "Code 1234",
                    receivedAt = 123456789L,
                    source = SmsSource.REAL_SMS,
                    status = SmsRecordStatus.PENDING_FORWARD,
                    matchedKeyword = "code",
                ),
                attempts = listOf(
                    ForwardAttemptDraft(robotId = 1L, channel = "FEISHU", attemptNumber = 1, status = ForwardAttemptStatus.PENDING, recoverable = true),
                ),
            ),
            results = listOf(
                ForwardDispatchResult(robotId = 1L, channel = "FEISHU", status = ForwardAttemptStatus.FAILED, responseCode = null, responseMessage = "timeout", recoverable = true),
            ),
        )

        assertEquals(SmsRecordStatus.FAILED, finalized.record.status)
        assertTrue(finalized.record.failureReason!!.contains("timeout"))
        assertEquals(ForwardAttemptStatus.FAILED, finalized.attempts.single().status)
        assertTrue(finalized.attempts.single().recoverable)
    }
}
