package com.example.mysmscode.domain

class RetryFailedAttemptUseCase {

    fun retry(
        failedAttempt: RetryableAttempt,
        dispatchResult: ForwardDispatchResult,
    ): RetryExecution {
        val isSuccess = dispatchResult.status == ForwardAttemptStatus.SUCCESS
        return RetryExecution(
            recordStatus = if (isSuccess) SmsRecordStatus.SUCCESS else SmsRecordStatus.FAILED,
            recordFailureReason = if (isSuccess) null else "${dispatchResult.channel}: ${dispatchResult.responseMessage.orEmpty()}".trim(),
            nextAttempt = ForwardAttemptDraft(
                robotId = failedAttempt.robotId,
                channel = failedAttempt.robotType.name,
                attemptNumber = failedAttempt.attemptNumber + 1,
                status = dispatchResult.status,
                recoverable = dispatchResult.recoverable,
                responseCode = dispatchResult.responseCode,
                responseMessage = dispatchResult.responseMessage,
            ),
        )
    }
}