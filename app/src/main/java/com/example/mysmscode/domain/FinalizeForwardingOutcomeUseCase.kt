package com.example.mysmscode.domain

class FinalizeForwardingOutcomeUseCase(
    private val autoRetryPolicyUseCase: AutoRetryPolicyUseCase = AutoRetryPolicyUseCase(),
) {

    fun finalize(
        outcome: ProcessingOutcomeDraft,
        results: List<ForwardDispatchResult>,
        attemptedAt: Long,
        retryPolicyConfig: RetryPolicyConfig = RetryPolicyConfig.default(),
    ): ProcessingOutcomeDraft {
        if (outcome.attempts.isEmpty()) {
            return outcome
        }

        val resultByRobotAndChannel = results.associateBy { it.robotId to it.channel }
        val finalizedAttempts = outcome.attempts.map { attempt ->
            val result = resultByRobotAndChannel[attempt.robotId to attempt.channel]
            if (result == null) {
                val retryDecision = autoRetryPolicyUseCase.schedule(
                    attemptNumber = attempt.attemptNumber,
                    recoverable = true,
                    now = attemptedAt,
                    config = retryPolicyConfig,
                )
                attempt.copy(
                    status = ForwardAttemptStatus.FAILED,
                    recoverable = retryDecision.recoverable,
                    responseMessage = "Missing dispatch result for ${attempt.channel}.",
                    nextRetryAt = retryDecision.nextRetryAt,
                )
            } else {
                val retryDecision = if (result.status == ForwardAttemptStatus.FAILED) {
                    autoRetryPolicyUseCase.schedule(
                        attemptNumber = attempt.attemptNumber,
                        recoverable = result.recoverable,
                        now = attemptedAt,
                        config = retryPolicyConfig,
                    )
                } else {
                    RetryScheduleDecision(recoverable = false, nextRetryAt = null)
                }
                attempt.copy(
                    status = result.status,
                    recoverable = retryDecision.recoverable,
                    responseCode = result.responseCode,
                    responseMessage = result.responseMessage,
                    nextRetryAt = retryDecision.nextRetryAt,
                )
            }
        }

        val failedAttempts = finalizedAttempts.filter { it.status == ForwardAttemptStatus.FAILED }
        val finalStatus = if (failedAttempts.isEmpty()) SmsRecordStatus.SUCCESS else SmsRecordStatus.FAILED
        val failureReason = failedAttempts
            .mapNotNull { attempt -> attempt.responseMessage?.takeIf { it.isNotBlank() }?.let { "${attempt.channel}: $it" } }
            .joinToString(separator = "; ")
            .ifBlank { null }

        return outcome.copy(
            record = outcome.record.copy(
                status = finalStatus,
                failureReason = failureReason,
            ),
            attempts = finalizedAttempts,
        )
    }
}