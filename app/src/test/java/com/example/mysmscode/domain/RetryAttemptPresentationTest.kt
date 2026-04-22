package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

class RetryAttemptPresentationTest {

    private val scheduledAttempt = RetryableAttempt(
        attemptId = 1L,
        smsRecordId = 2L,
        senderNumber = "10690001",
        messageBody = "Your code is 123456",
        matchedKeyword = "code",
        receivedAt = 0L,
        robotId = 3L,
        robotName = "Ops Feishu",
        robotType = RobotType.FEISHU,
        attemptNumber = 3,
        lastErrorMessage = "timeout",
        recoverable = true,
        nextRetryAt = 65_000L,
    )

    @Test
    fun completedRetryCount_isDerivedFromAttemptNumber() {
        assertEquals(2, scheduledAttempt.completedRetryCount())
    }

    @Test
    fun autoRetryStatusLabel_reportsScheduledRetryWhenNextWindowExists() {
        assertEquals(zh(0x5DF2, 0x5B89, 0x6392, 0x81EA, 0x52A8, 0x91CD, 0x8BD5), scheduledAttempt.autoRetryStatusLabel())
    }

    @Test
    fun autoRetryStatusLabel_reportsExhaustedWhenRecoverableRetryWindowIsGone() {
        val exhaustedAttempt = scheduledAttempt.copy(
            attemptNumber = 4,
            nextRetryAt = null,
        )

        assertEquals(zh(0x81EA, 0x52A8, 0x91CD, 0x8BD5, 0x6B21, 0x6570, 0x5DF2, 0x8017, 0x5C3D), exhaustedAttempt.autoRetryStatusLabel())
    }

    @Test
    fun autoRetryStatusLabel_reportsNonRecoverableFailuresClearly() {
        val nonRecoverableAttempt = scheduledAttempt.copy(
            attemptNumber = 1,
            recoverable = false,
            nextRetryAt = null,
        )

        assertEquals(zh(0x4E0D, 0x53EF, 0x81EA, 0x52A8, 0x91CD, 0x8BD5), nonRecoverableAttempt.autoRetryStatusLabel())
    }

    @Test
    fun formatRetryTimestamp_rendersReadableLocalTime() {
        val formatted = formatRetryTimestamp(
            timestampMillis = 0L,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertEquals("1970-01-01 08:00:00", formatted)
    }
}
