package com.example.mysmscode.domain

fun resolveMonitoringStatusMessage(
    transition: MonitoringControlTransition,
    isServiceRunning: Boolean,
    requestMessage: String,
    completedMessage: String,
    fallbackMessage: String,
): String {
    return when {
        transition == MonitoringControlTransition.STARTING && isServiceRunning -> completedMessage
        transition == MonitoringControlTransition.STOPPING && !isServiceRunning -> completedMessage
        transition == MonitoringControlTransition.IDLE -> requestMessage
        else -> fallbackMessage
    }
}

fun calculateStatusMessageDelayMillis(
    requestStartedAtMillis: Long,
    nowMillis: Long,
    minimumVisibleMillis: Long,
): Long {
    val elapsed = (nowMillis - requestStartedAtMillis).coerceAtLeast(0L)
    return (minimumVisibleMillis - elapsed).coerceAtLeast(0L)
}
