package com.example.mysmscode.domain

class AutoRetryPolicyUseCase {

    fun schedule(
        attemptNumber: Int,
        recoverable: Boolean,
        now: Long,
    ): RetryScheduleDecision {
        if (!recoverable) {
            return RetryScheduleDecision(recoverable = false, nextRetryAt = null)
        }

        val delayMillis = when (attemptNumber) {
            1 -> 60_000L
            2 -> 5 * 60_000L
            3 -> 15 * 60_000L
            else -> null
        }

        return if (delayMillis == null) {
            RetryScheduleDecision(recoverable = false, nextRetryAt = null)
        } else {
            RetryScheduleDecision(recoverable = true, nextRetryAt = now + delayMillis)
        }
    }
}