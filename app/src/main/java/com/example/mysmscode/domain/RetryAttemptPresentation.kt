package com.example.mysmscode.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val retryTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
