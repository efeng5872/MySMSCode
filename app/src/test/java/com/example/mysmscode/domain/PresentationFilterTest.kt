package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationFilterTest {

    private val records = listOf(
        SmsRecordPreview("10010", "a", "SUCCESS", "REAL_SMS", 1L),
        SmsRecordPreview("10011", "b", "FAILED", "REAL_SMS", 2L),
        SmsRecordPreview("10012", "c", "NOT_MATCHED", "SIMULATION", 3L),
    )

    private val attempts = listOf(
        RetryableAttempt(1L, 10L, "10010", "a", null, 1L, 101L, "Feishu A", RobotType.FEISHU, 1, "timeout", true, 2L),
        RetryableAttempt(2L, 11L, "10011", "b", null, 1L, 102L, "Feishu B", RobotType.FEISHU, 4, "timeout", true, null),
        RetryableAttempt(3L, 12L, "10012", "c", null, 1L, 103L, "WeCom A", RobotType.WECOM, 1, "bad request", false, null),
    )

    @Test
    fun historyFilter_all_returnsOriginalList() {
        assertEquals(records, HistoryFilterOption.ALL.apply(records))
    }

    @Test
    fun historyFilter_specificStatus_returnsMatchingRecords() {
        assertEquals(listOf(records[0]), HistoryFilterOption.SUCCESS.apply(records))
        assertEquals(listOf(records[2]), HistoryFilterOption.NOT_MATCHED.apply(records))
    }

    @Test
    fun failedRetryFilter_scheduled_returnsOnlyScheduledAttempts() {
        assertEquals(listOf(attempts[0]), FailedRetryFilterOption.SCHEDULED.apply(attempts))
    }

    @Test
    fun failedRetryFilter_exhausted_returnsOnlyExhaustedAttempts() {
        assertEquals(listOf(attempts[1]), FailedRetryFilterOption.EXHAUSTED.apply(attempts))
    }

    @Test
    fun failedRetryFilter_nonRecoverable_returnsOnlyDisabledAttempts() {
        assertEquals(listOf(attempts[2]), FailedRetryFilterOption.NON_RECOVERABLE.apply(attempts))
    }
}