package com.example.mysmscode.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")
private val retryTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

enum class HistoryFilterOption(val label: String) {
    ALL(zh(0x5168, 0x90E8)),
    SUCCESS(zh(0x6210, 0x529F)),
    FAILED(zh(0x5931, 0x8D25)),
    NOT_MATCHED(zh(0x672A, 0x547D, 0x4E2D)),
    CONFIGURATION_FAILED(zh(0x914D, 0x7F6E, 0x5F02, 0x5E38));

    fun apply(records: List<SmsRecordPreview>): List<SmsRecordPreview> = when (this) {
        ALL -> records
        SUCCESS -> records.filter { it.status == "SUCCESS" }
        FAILED -> records.filter { it.status == "FAILED" }
        NOT_MATCHED -> records.filter { it.status == "NOT_MATCHED" }
        CONFIGURATION_FAILED -> records.filter { it.status == "CONFIGURATION_FAILED" }
    }
}

enum class FailedRetryFilterOption(val label: String) {
    ALL(zh(0x5168, 0x90E8)),
    SCHEDULED(zh(0x5DF2, 0x5B89, 0x6392)),
    EXHAUSTED(zh(0x5DF2, 0x8017, 0x5C3D)),
    NON_RECOVERABLE(zh(0x4E0D, 0x53EF, 0x6062, 0x590D));

    fun apply(attempts: List<RetryableAttempt>): List<RetryableAttempt> = when (this) {
        ALL -> attempts
        SCHEDULED -> attempts.filter { it.nextRetryAt != null }
        EXHAUSTED -> attempts.filter { it.nextRetryAt == null && it.recoverable }
        NON_RECOVERABLE -> attempts.filter { !it.recoverable }
    }
}

fun RetryableAttempt.completedRetryCount(): Int = (attemptNumber - 1).coerceAtLeast(0)

fun RetryableAttempt.autoRetryStatusLabel(): String = when {
    nextRetryAt != null -> zh(0x5DF2, 0x5B89, 0x6392, 0x81EA, 0x52A8, 0x91CD, 0x8BD5)
    !recoverable -> zh(0x4E0D, 0x53EF, 0x81EA, 0x52A8, 0x91CD, 0x8BD5)
    else -> zh(0x81EA, 0x52A8, 0x91CD, 0x8BD5, 0x6B21, 0x6570, 0x5DF2, 0x8017, 0x5C3D)
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
    "NOT_MATCHED" -> zh(0x5173, 0x952E, 0x5B57, 0x672A, 0x547D, 0x4E2D)
    "PENDING_FORWARD" -> zh(0x7B49, 0x5F85, 0x8F6C, 0x53D1)
    "CONFIGURATION_FAILED" -> zh(0x914D, 0x7F6E, 0x5F02, 0x5E38)
    "SUCCESS" -> zh(0x8F6C, 0x53D1, 0x6210, 0x529F)
    "FAILED" -> zh(0x8F6C, 0x53D1, 0x5931, 0x8D25)
    else -> status
}

fun SmsRecordPreview.sourceLabel(): String = when (source) {
    "REAL_SMS" -> zh(0x6536, 0x5230, 0x77ED, 0x4FE1)
    "SIMULATION" -> zh(0x6A21, 0x62DF, 0x6CE8, 0x5165)
    else -> source
}
