package com.example.mysmscode.domain

class CreateProcessingOutcomeUseCase {
    fun create(
        senderNumber: String,
        messageBody: String,
        source: SmsSource,
        processingResult: SmsProcessingResult,
        receivedAt: Long,
    ): ProcessingOutcomeDraft? {
        return when (processingResult) {
            SmsProcessingResult.Ignored -> null
            is SmsProcessingResult.NotMatched -> ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    receivedAt = receivedAt,
                    source = source,
                    status = SmsRecordStatus.NOT_MATCHED,
                    matchedKeyword = processingResult.record.matchedKeyword,
                    failureReason = processingResult.record.failureReason,
                ),
                attempts = emptyList(),
            )
            is SmsProcessingResult.ConfigurationFailed -> ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    receivedAt = receivedAt,
                    source = source,
                    status = SmsRecordStatus.CONFIGURATION_FAILED,
                    matchedKeyword = processingResult.record.matchedKeyword,
                    failureReason = processingResult.record.failureReason,
                ),
                attempts = emptyList(),
            )
            is SmsProcessingResult.PendingForward -> ProcessingOutcomeDraft(
                record = SmsRecordDraft(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    receivedAt = receivedAt,
                    source = source,
                    status = SmsRecordStatus.PENDING_FORWARD,
                    matchedKeyword = processingResult.record.matchedKeyword,
                    failureReason = processingResult.record.failureReason,
                ),
                attempts = processingResult.attempts.map { attempt ->
                    ForwardAttemptDraft(
                        robotId = attempt.robotId,
                        channel = attempt.robotType.name,
                        attemptNumber = 1,
                        status = ForwardAttemptStatus.PENDING,
                        recoverable = true,
                    )
                }
            )
        }
    }
}