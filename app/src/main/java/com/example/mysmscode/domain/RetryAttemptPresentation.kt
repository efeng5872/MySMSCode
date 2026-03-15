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