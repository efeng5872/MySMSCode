package com.example.mysmscode.domain

class FinalizeForwardingOutcomeUseCase {

    fun finalize(
        outcome: ProcessingOutcomeDraft,
        results: List<ForwardDispatchResult>,
    ): ProcessingOutcomeDraft {
        if (outcome.attempts.isEmpty()) {
            return outcome
        }

        val resultByRobotAndChannel = results.associateBy { it.robotId to it.channel }
        val finalizedAttempts = outcome.attempts.map { attempt ->
            val result = resultByRobotAndChannel[attempt.robotId to attempt.channel]
            if (result == null) {
                attempt.copy(
                    status = ForwardAttemptStatus.FAILED,
                    recoverable = true,
                    responseMessage = "Missing dispatch result for ${attempt.channel}.",
                )
            } else {
                attempt.copy(
                    status = result.status,
                    recoverable = result.recoverable,
                    responseCode = result.responseCode,
                    responseMessage = result.responseMessage,
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