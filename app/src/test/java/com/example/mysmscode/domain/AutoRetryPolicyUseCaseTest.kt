package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRetryPolicyUseCaseTest {

    private val useCase = AutoRetryPolicyUseCase()

    @Test
    fun firstFailure_schedulesRetryAfterOneMinute() {
        val decision = useCase.schedule(
            attemptNumber = 1,
            recoverable = true,
            now = 1_000L,
        )

        assertTrue(decision.recoverable)
        assertEquals(61_000L, decision.nextRetryAt)
    }

    @Test
    fun secondFailure_schedulesRetryAfterFiveMinutes() {
        val decision = useCase.schedule(
            attemptNumber = 2,
            recoverable = true,
            now = 1_000L,
        )

        assertTrue(decision.recoverable)
        assertEquals(301_000L, decision.nextRetryAt)
    }

    @Test
    fun thirdFailure_schedulesRetryAfterFifteenMinutes() {
        val decision = useCase.schedule(
            attemptNumber = 3,
            recoverable = true,
            now = 1_000L,
        )

        assertTrue(decision.recoverable)
        assertEquals(901_000L, decision.nextRetryAt)
    }

    @Test
    fun fourthFailure_stopsAutomaticRetry() {
        val decision = useCase.schedule(
            attemptNumber = 4,
            recoverable = true,
            now = 1_000L,
        )

        assertFalse(decision.recoverable)
        assertNull(decision.nextRetryAt)
    }

    @Test
    fun nonRecoverableFailure_doesNotScheduleRetry() {
        val decision = useCase.schedule(
            attemptNumber = 1,
            recoverable = false,
            now = 1_000L,
        )

        assertFalse(decision.recoverable)
        assertNull(decision.nextRetryAt)
    }
}