package com.example.mysmscode.domain

enum class RetryAlarmSchedulingMode {
    EXACT,
    EXACT_ALLOW_IDLE,
    INEXACT_ALLOW_IDLE,
}

fun resolveRetryAlarmSchedulingMode(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
): RetryAlarmSchedulingMode {
    return when {
        sdkInt < 23 -> RetryAlarmSchedulingMode.EXACT
        sdkInt < 31 || canScheduleExactAlarms -> RetryAlarmSchedulingMode.EXACT_ALLOW_IDLE
        else -> RetryAlarmSchedulingMode.INEXACT_ALLOW_IDLE
    }
}
