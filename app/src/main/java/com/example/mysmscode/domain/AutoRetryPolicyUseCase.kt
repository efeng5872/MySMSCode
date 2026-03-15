package com.example.mysmscode.domain

class AutoRetryPolicyUseCase {

    fun schedule(
        attemptNumber: Int,
        recoverable: Boolean,
        now: Long,
        config: RetryPolicyConfig,
    ): RetryScheduleDecision {
        if (!recoverable) {
            return RetryScheduleDecision(recoverable = false, nextRetryAt = null)
        }

        val delayMillis = when (attemptNumber) {
            1 -> config.firstRetryDelaySeconds * 1000L
            2 -> config.secondRetryDelaySeconds * 1000L
            3 -> config.thirdRetryDelaySeconds * 1000L
            else -> null
        }

        return if (delayMillis == null) {
            RetryScheduleDecision(recoverable = false, nextRetryAt = null)
        } else {
            RetryScheduleDecision(recoverable = true, nextRetryAt = now + delayMillis)
        }
    }
}