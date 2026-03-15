package com.example.mysmscode.domain

fun buildIncomingSmsTrace(
    senderNumber: String,
    messageBody: String,
    partCount: Int,
): String {
    return "incoming_sms sender=$senderNumber parts=$partCount length=${messageBody.length}"
}

fun buildProcessingTrace(
    source: SmsSource,
    result: SmsProcessingResult,
): String {
    return when (result) {
        SmsProcessingResult.Ignored -> "processing_result source=$source status=IGNORED attempts=0"
        is SmsProcessingResult.NotMatched -> "processing_result source=$source sender=${result.record.senderNumber} status=${result.record.status} attempts=0"
        is SmsProcessingResult.ConfigurationFailed -> {
            val failure = result.record.failureReason?.takeIf { it.isNotBlank() } ?: "n/a"
            "processing_result source=$source sender=${result.record.senderNumber} status=${result.record.status} attempts=0 failure=$failure"
        }
        is SmsProcessingResult.PendingForward -> {
            val keyword = result.record.matchedKeyword?.takeIf { it.isNotBlank() } ?: "n/a"
            "processing_result source=$source sender=${result.record.senderNumber} status=${result.record.status} attempts=${result.attempts.size} keyword=$keyword"
        }
    }
}

fun buildPersistenceTrace(
    outcome: ProcessingOutcomeDraft,
): String {
    val failure = outcome.record.failureReason?.takeIf { it.isNotBlank() } ?: "n/a"
    return "persistence_result source=${outcome.record.source} sender=${outcome.record.senderNumber} recordStatus=${outcome.record.status} attempts=${outcome.attempts.size} failure=$failure"
}
