package com.example.mysmscode.domain

enum class ProcessExitReason {
    UNKNOWN,
    EXIT_SELF,
    SIGNALED,
    LOW_MEMORY,
    CRASH,
    CRASH_NATIVE,
    ANR,
    INITIALIZATION_FAILURE,
    PERMISSION_CHANGE,
    EXCESSIVE_RESOURCE_USAGE,
    USER_REQUESTED,
    USER_STOPPED,
    DEPENDENCY_DIED,
    OTHER,
    FREEZER,
    PACKAGE_STATE_CHANGE,
    PACKAGE_UPDATED,
}

data class ProcessExitObservation(
    val timestamp: Long,
    val reason: ProcessExitReason,
    val description: String?,
)

fun MonitoringPersistenceState.recordProcessExit(
    observation: ProcessExitObservation,
): MonitoringPersistenceState {
    if (observation.timestamp <= (lastProcessExitAt ?: Long.MIN_VALUE)) {
        return this
    }
    return copy(
        lastProcessExitAt = observation.timestamp,
        lastProcessExitReason = observation.reason.name,
        lastProcessExitDescription = observation.description,
    )
}
