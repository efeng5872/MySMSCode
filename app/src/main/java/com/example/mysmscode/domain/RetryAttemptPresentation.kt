package com.example.mysmscode.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val retryTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

enum class HistoryFilterOption(val label: String) {
    ALL("全部"),
    SUCCESS("成功"),
    FAILED("失败"),
    NOT_MATCHED("未命中"),
    CONFIGURATION_FAILED("配置异常");

    fun apply(records: List<SmsRecordPreview>): List<SmsRecordPreview> = when (this) {
        ALL -> records
        SUCCESS -> records.filter { it.status == "SUCCESS" }
        FAILED -> records.filter { it.status == "FAILED" }
        NOT_MATCHED -> records.filter { it.status == "NOT_MATCHED" }
        CONFIGURATION_FAILED -> records.filter { it.status == "CONFIGURATION_FAILED" }
    }
}

enum class FailedRetryFilterOption(val label: String) {
    ALL("全部"),
    SCHEDULED("已安排"),
    EXHAUSTED("已耗尽"),
    NON_RECOVERABLE("不可重试");

    fun apply(attempts: List<RetryableAttempt>): List<RetryableAttempt> = when (this) {
        ALL -> attempts
        SCHEDULED -> attempts.filter { it.nextRetryAt != null }
        EXHAUSTED -> attempts.filter { it.nextRetryAt == null && it.recoverable }
        NON_RECOVERABLE -> attempts.filter { !it.recoverable }
    }
}

fun RetryableAttempt.completedRetryCount(): Int = (attemptNumber - 1).coerceAtLeast(0)

fun RetryableAttempt.autoRetryStatusLabel(): String = when {
    nextRetryAt != null -> "已安排自动重试"
    !recoverable -> "不可自动重试"
    else -> "自动重试次数已耗尽"
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
    "NOT_MATCHED" -> "关键字未命中"
    "PENDING_FORWARD" -> "等待转发"
    "CONFIGURATION_FAILED" -> "配置异常"
    "SUCCESS" -> "转发成功"
    "FAILED" -> "转发失败"
    else -> status
}

fun SmsRecordPreview.sourceLabel(): String = when (source) {
    "REAL_SMS" -> "收到短信"
    "SIMULATION" -> "模拟注入"
    else -> source
}
