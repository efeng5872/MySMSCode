package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRetryPolicyUseCaseTest {

    private val useCase = AutoRetryPolicyUseCase()
    private val config = RetryPolicyConfig(
        firstRetryDelaySeconds = 10,
        secondRetryDelaySeconds = 30,
        thirdRetryDelaySeconds = 60,
    )

    @Test
    fun firstFailure_schedulesRetryUsingConfiguredDelay() {
        val decision = useCase.schedule(
            attemptNumber = 1,
            recoverable = true,
            now = 1_000L,
            config = config,
        )

        assertTrue(decision.recoverable)
        assertEquals(11_000L, decision.nextRetryAt)
    }

    @Test
    fun secondFailure_schedulesRetryUsingConfiguredDelay() {
        val decision = useCase.schedule(
            attemptNumber = 2,
            recoverable = true,
            now = 1_000L,
            config = config,
        )

        assertTrue(decision.recoverable)
        assertEquals(31_000L, decision.nextRetryAt)
    }

    @Test
    fun thirdFailure_schedulesRetryUsingConfiguredDelay() {
        val decision = useCase.schedule(
            attemptNumber = 3,
            recoverable = true,
            now = 1_000L,
            config = config,
        )

        assertTrue(decision.recoverable)
        assertEquals(61_000L, decision.nextRetryAt)
    }

    @Test
    fun fourthFailure_stopsAutomaticRetry() {
        val decision = useCase.schedule(
            attemptNumber = 4,
            recoverable = true,
            now = 1_000L,
            config = config,
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
            config = config,
        )

        assertFalse(decision.recoverable)
        assertNull(decision.nextRetryAt)
    }

    @Test
    fun defaultConfig_matchesVerificationFriendlyValues() {
        assertEquals(10, RetryPolicyConfig.default().firstRetryDelaySeconds)
        assertEquals(30, RetryPolicyConfig.default().secondRetryDelaySeconds)
        assertEquals(60, RetryPolicyConfig.default().thirdRetryDelaySeconds)
    }
}