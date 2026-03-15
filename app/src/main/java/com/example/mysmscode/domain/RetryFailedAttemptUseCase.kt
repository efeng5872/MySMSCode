package com.example.mysmscode.domain

class RetryFailedAttemptUseCase(
    private val autoRetryPolicyUseCase: AutoRetryPolicyUseCase = AutoRetryPolicyUseCase(),
) {

    fun retry(
        failedAttempt: RetryableAttempt,
        dispatchResult: ForwardDispatchResult,
        attemptedAt: Long,
    ): RetryExecution {
        val isSuccess = dispatchResult.status == ForwardAttemptStatus.SUCCESS
        val retryDecision = if (isSuccess) {
            RetryScheduleDecision(recoverable = false, nextRetryAt = null)
        } else {
            autoRetryPolicyUseCase.schedule(
                attemptNumber = failedAttempt.attemptNumber + 1,
                recoverable = dispatchResult.recoverable,
                now = attemptedAt,
            )
        }

        return RetryExecution(
            recordStatus = if (isSuccess) SmsRecordStatus.SUCCESS else SmsRecordStatus.FAILED,
            recordFailureReason = if (isSuccess) null else "${dispatchResult.channel}: ${dispatchResult.responseMessage.orEmpty()}".trim(),
            nextAttempt = ForwardAttemptDraft(
                robotId = failedAttempt.robotId,
                channel = failedAttempt.robotType.name,
                attemptNumber = failedAttempt.attemptNumber + 1,
                status = dispatchResult.status,
                recoverable = retryDecision.recoverable,
                responseCode = dispatchResult.responseCode,
                responseMessage = dispatchResult.responseMessage,
                nextRetryAt = retryDecision.nextRetryAt,
            ),
        )
    }
}