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
