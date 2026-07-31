package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryAlarmSchedulingPolicyTest {

    @Test
    fun `android 12 without exact alarm access uses inexact idle alarm`() {
        assertEquals(
            RetryAlarmSchedulingMode.INEXACT_ALLOW_IDLE,
            resolveRetryAlarmSchedulingMode(sdkInt = 31, canScheduleExactAlarms = false),
        )
    }

    @Test
    fun `android 12 with exact alarm access uses exact idle alarm`() {
        assertEquals(
            RetryAlarmSchedulingMode.EXACT_ALLOW_IDLE,
            resolveRetryAlarmSchedulingMode(sdkInt = 31, canScheduleExactAlarms = true),
        )
    }

    @Test
    fun `android 11 uses exact idle alarm without special access`() {
        assertEquals(
            RetryAlarmSchedulingMode.EXACT_ALLOW_IDLE,
            resolveRetryAlarmSchedulingMode(sdkInt = 30, canScheduleExactAlarms = false),
        )
    }
}
