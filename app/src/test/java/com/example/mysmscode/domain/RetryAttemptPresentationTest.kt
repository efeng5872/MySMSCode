package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

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
        assertEquals("已安排自动重试", scheduledAttempt.autoRetryStatusLabel())
    }

    @Test
    fun autoRetryStatusLabel_reportsExhaustedWhenRecoverableRetryWindowIsGone() {
        val exhaustedAttempt = scheduledAttempt.copy(
            attemptNumber = 4,
            nextRetryAt = null,
        )

        assertEquals("自动重试次数已耗尽", exhaustedAttempt.autoRetryStatusLabel())
    }

    @Test
    fun autoRetryStatusLabel_reportsNonRecoverableFailuresClearly() {
        val nonRecoverableAttempt = scheduledAttempt.copy(
            attemptNumber = 1,
            recoverable = false,
            nextRetryAt = null,
        )

        assertEquals("不可自动重试", nonRecoverableAttempt.autoRetryStatusLabel())
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
