package com.example.mysmscode.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val retryTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

enum class HistoryFilterOption(val label: String) {
    ALL("All"),
    SUCCESS("Success"),
    FAILED("Failed"),
    NOT_MATCHED("Not Matched"),
    CONFIGURATION_FAILED("Config Failed");

    fun apply(records: List<SmsRecordPreview>): List<SmsRecordPreview> = when (this) {
        ALL -> records
        SUCCESS -> records.filter { it.status == "SUCCESS" }
        FAILED -> records.filter { it.status == "FAILED" }
        NOT_MATCHED -> records.filter { it.status == "NOT_MATCHED" }
        CONFIGURATION_FAILED -> records.filter { it.status == "CONFIGURATION_FAILED" }
    }
}

enum class FailedRetryFilterOption(val label: String) {
    ALL("All"),
    SCHEDULED("Scheduled"),
    EXHAUSTED("Exhausted"),
    NON_RECOVERABLE("Non-Recoverable");

    fun apply(attempts: List<RetryableAttempt>): List<RetryableAttempt> = when (this) {
        ALL -> attempts
        SCHEDULED -> attempts.filter { it.nextRetryAt != null }
        EXHAUSTED -> attempts.filter { it.nextRetryAt == null && it.recoverable }
        NON_RECOVERABLE -> attempts.filter { !it.recoverable }
    }
}

fun RetryableAttempt.completedRetryCount(): Int = (attemptNumber - 1).coerceAtLeast(0)

fun RetryableAttempt.autoRetryStatusLabel(): String = when {
    nextRetryAt != null -> "Automatic retry scheduled"
    !recoverable -> "Automatic retry disabled (non-recoverable)"
    else -> "Automatic retries exhausted"
}

fun formatRetryTimestamp(
    timestampMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(zoneId)
        .format(retryTimestampFormatter)
}

fun SmsRecordPreview.receivedAtLabel(zoneId: ZoneId = ZoneId.systemDefault()): String =
    formatRetryTimestamp(timestampMillis = receivedAt, zoneId = zoneId)

fun SmsRecordPreview.statusLabel(): String = when (status) {
    "NOT_MATCHED" -> "Keyword not matched"
    "PENDING_FORWARD" -> "Waiting to forward"
    "CONFIGURATION_FAILED" -> "Configuration failed"
    "SUCCESS" -> "Forwarded successfully"
    "FAILED" -> "Forward failed"
    else -> status
}

fun SmsRecordPreview.sourceLabel(): String = when (source) {
    "REAL_SMS" -> "Incoming SMS"
    "SIMULATION" -> "Simulation"
    else -> source
}
