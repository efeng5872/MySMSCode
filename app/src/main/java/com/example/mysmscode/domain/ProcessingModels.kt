package com.example.mysmscode.domain

enum class RobotType {
    FEISHU,
    WECOM,
}

data class RobotEndpoint(
    val id: Long = 0L,
    val name: String,
    val type: RobotType,
    val enabled: Boolean,
    val webhookUrl: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

data class SenderRule(
    val id: Long = 0L,
    val senderNumber: String,
    val enabled: Boolean,
    val keywords: List<String>,
    val selectedRobotIds: List<Long>,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

enum class SmsProcessingStatus {
    NOT_MATCHED,
    PENDING_FORWARD,
    CONFIGURATION_FAILED,
}

data class SmsProcessingRecord(
    val senderNumber: String,
    val messageBody: String,
    val status: SmsProcessingStatus,
    val matchedKeyword: String? = null,
    val failureReason: String? = null,
)

data class ForwardPlan(
    val robotId: Long,
    val robotType: RobotType,
)

enum class SmsSource {
    REAL_SMS,
    SIMULATION,
}

enum class SmsRecordStatus {
    NOT_MATCHED,
    PENDING_FORWARD,
    CONFIGURATION_FAILED,
    SUCCESS,
    FAILED,
}

enum class ForwardAttemptStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

data class SmsRecordDraft(
    val senderNumber: String,
    val messageBody: String,
    val receivedAt: Long,
    val source: SmsSource,
    val status: SmsRecordStatus,
    val matchedKeyword: String? = null,
    val failureReason: String? = null,
)

data class ForwardAttemptDraft(
    val robotId: Long,
    val channel: String,
    val attemptNumber: Int,
    val status: ForwardAttemptStatus,
    val recoverable: Boolean,
    val responseCode: String? = null,
    val responseMessage: String? = null,
)

data class ForwardDispatchResult(
    val robotId: Long,
    val channel: String,
    val status: ForwardAttemptStatus,
    val responseCode: String?,
    val responseMessage: String?,
    val recoverable: Boolean,
)

data class RetryableAttempt(
    val attemptId: Long,
    val smsRecordId: Long,
    val senderNumber: String,
    val messageBody: String,
    val matchedKeyword: String?,
    val receivedAt: Long,
    val robotId: Long,
    val robotName: String,
    val robotType: RobotType,
    val attemptNumber: Int,
    val lastErrorMessage: String?,
    val recoverable: Boolean,
)

data class RetryExecution(
    val recordStatus: SmsRecordStatus,
    val recordFailureReason: String?,
    val nextAttempt: ForwardAttemptDraft,
)

data class ProcessingOutcomeDraft(
    val record: SmsRecordDraft,
    val attempts: List<ForwardAttemptDraft>,
)

sealed interface SmsProcessingResult {
    data object Ignored : SmsProcessingResult

    data class NotMatched(
        val record: SmsProcessingRecord,
    ) : SmsProcessingResult

    data class PendingForward(
        val record: SmsProcessingRecord,
        val attempts: List<ForwardPlan>,
    ) : SmsProcessingResult

    data class ConfigurationFailed(
        val record: SmsProcessingRecord,
    ) : SmsProcessingResult
}