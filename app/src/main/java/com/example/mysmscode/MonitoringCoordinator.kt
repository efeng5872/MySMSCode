package com.example.mysmscode

import android.Manifest
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.MonitoringControlTransition
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.resolveMonitoringStatusMessage
import kotlinx.coroutines.delay

data class MonitoringRefreshResult(
    val retryPolicyConfig: RetryPolicyConfig,
    val monitoringPersistenceState: MonitoringPersistenceState,
    val permissionSnapshot: AppPermissionSnapshot,
    val monitoringServiceRunning: Boolean,
    val statusMessage: String,
)

data class MonitoringSyncResult(
    val monitoringPersistenceState: MonitoringPersistenceState,
    val monitoringServiceRunning: Boolean,
)

data class MonitoringToggleInput(
    val monitoringServiceRunning: Boolean,
    val monitoringPersistenceState: MonitoringPersistenceState,
    val permissionUiState: PermissionUiState,
)

data class MonitoringMessages(
    val startedMessage: String,
    val startRequestedMessage: String,
    val startFailedMessage: String,
    val stoppedMessage: String,
    val stopRequestedMessage: String,
    val stopFailedMessage: String,
)

data class MonitoringToggleResult(
    val monitoringPersistenceState: MonitoringPersistenceState,
    val monitoringServiceRunning: Boolean,
    val statusMessage: String,
    val shouldRefreshRuntimeState: Boolean,
)

class MonitoringCoordinator(
    private val readRetryPolicyConfig: suspend () -> RetryPolicyConfig,
    private val readMonitoringState: suspend () -> MonitoringPersistenceState,
    private val readPermissionSnapshot: () -> AppPermissionSnapshot,
    private val readServiceRunning: () -> Boolean,
    private val startMonitoring: () -> Unit,
    private val stopMonitoring: () -> Unit,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun refreshRuntimeState(
        currentMessage: String,
        transition: MonitoringControlTransition,
        startedMessage: String,
    ): MonitoringRefreshResult {
        val retryPolicyConfig = readRetryPolicyConfig()
        val monitoringPersistenceState = readMonitoringState()
        val monitoringServiceRunning = readServiceRunning()
        val permissionSnapshot = readPermissionSnapshot()
        val statusMessage = reconcilePassiveMonitoringStatusMessage(
            currentMessage = currentMessage,
            isServiceRunning = monitoringServiceRunning,
            monitoringState = monitoringPersistenceState,
            transition = transition,
            startedMessage = startedMessage,
        )
        return MonitoringRefreshResult(
            retryPolicyConfig = retryPolicyConfig,
            monitoringPersistenceState = monitoringPersistenceState,
            permissionSnapshot = permissionSnapshot,
            monitoringServiceRunning = monitoringServiceRunning,
            statusMessage = statusMessage,
        )
    }

    suspend fun syncMonitoringState(
        expectedRunning: Boolean,
        currentState: MonitoringPersistenceState,
    ): MonitoringSyncResult {
        var latestState = currentState
        repeat(8) {
            val isRunning = readServiceRunning()
            latestState = readMonitoringState()
            val persistenceMatches = if (expectedRunning) {
                latestState.monitoringEnabled && !latestState.stoppedByUser
            } else {
                !latestState.monitoringEnabled && latestState.stoppedByUser
            }
            if (isRunning == expectedRunning && persistenceMatches) {
                return MonitoringSyncResult(
                    monitoringPersistenceState = latestState,
                    monitoringServiceRunning = isRunning,
                )
            }
            delayMillis(250L)
        }
        return MonitoringSyncResult(
            monitoringPersistenceState = readMonitoringState(),
            monitoringServiceRunning = readServiceRunning(),
        )
    }

    fun launchPermissionRequest(
        permissionSnapshot: AppPermissionSnapshot,
        onAutoRequested: () -> Unit,
        launchPermissions: (Array<String>) -> Unit,
    ) {
        val missingPermissions = requiredPermissions(permissionSnapshot)
        if (missingPermissions.isNotEmpty()) {
            onAutoRequested()
            launchPermissions(missingPermissions)
        }
    }

    suspend fun toggleMonitoring(
        input: MonitoringToggleInput,
        messages: MonitoringMessages,
        onProgress: (MonitoringControlTransition, String) -> Unit,
    ): MonitoringToggleResult {
        return when {
            input.monitoringServiceRunning -> {
                val requestedAt = nowProvider()
                onProgress(MonitoringControlTransition.STOPPING, messages.stopRequestedMessage)
                stopMonitoring()
                val syncResult = syncMonitoringState(
                    expectedRunning = false,
                    currentState = input.monitoringPersistenceState,
                )
                val latestState = if (!syncResult.monitoringServiceRunning) {
                    syncResult.monitoringPersistenceState.copy(
                        monitoringEnabled = false,
                        stoppedByUser = true,
                        lastMonitoringStoppedAt = syncResult.monitoringPersistenceState.lastMonitoringStoppedAt ?: requestedAt,
                    )
                } else {
                    syncResult.monitoringPersistenceState
                }
                MonitoringToggleResult(
                    monitoringPersistenceState = latestState,
                    monitoringServiceRunning = syncResult.monitoringServiceRunning,
                    statusMessage = resolveMonitoringStatusMessage(
                        transition = MonitoringControlTransition.STOPPING,
                        isServiceRunning = syncResult.monitoringServiceRunning,
                        requestMessage = messages.stopRequestedMessage,
                        completedMessage = messages.stoppedMessage,
                        fallbackMessage = messages.stopFailedMessage,
                    ),
                    shouldRefreshRuntimeState = true,
                )
            }

            !input.permissionUiState.canStartMonitoring -> {
                MonitoringToggleResult(
                    monitoringPersistenceState = input.monitoringPersistenceState,
                    monitoringServiceRunning = input.monitoringServiceRunning,
                    statusMessage = input.permissionUiState.message,
                    shouldRefreshRuntimeState = false,
                )
            }

            else -> {
                val requestedAt = nowProvider()
                onProgress(MonitoringControlTransition.STARTING, messages.startRequestedMessage)
                startMonitoring()
                val syncResult = syncMonitoringState(
                    expectedRunning = true,
                    currentState = input.monitoringPersistenceState,
                )
                val latestState = if (syncResult.monitoringServiceRunning) {
                    syncResult.monitoringPersistenceState.copy(
                        monitoringEnabled = true,
                        stoppedByUser = false,
                        lastMonitoringStartedAt = syncResult.monitoringPersistenceState.lastMonitoringStartedAt ?: requestedAt,
                    )
                } else {
                    syncResult.monitoringPersistenceState
                }
                MonitoringToggleResult(
                    monitoringPersistenceState = latestState,
                    monitoringServiceRunning = syncResult.monitoringServiceRunning,
                    statusMessage = resolveMonitoringStatusMessage(
                        transition = MonitoringControlTransition.STARTING,
                        isServiceRunning = syncResult.monitoringServiceRunning,
                        requestMessage = messages.startRequestedMessage,
                        completedMessage = messages.startedMessage,
                        fallbackMessage = messages.startFailedMessage,
                    ),
                    shouldRefreshRuntimeState = true,
                )
            }
        }
    }
}

fun reconcilePassiveMonitoringStatusMessage(
    currentMessage: String,
    isServiceRunning: Boolean,
    monitoringState: MonitoringPersistenceState,
    transition: MonitoringControlTransition,
    startedMessage: String,
): String {
    if (transition != MonitoringControlTransition.IDLE) {
        return currentMessage
    }
    return when {
        currentMessage.isBlank() && isServiceRunning && monitoringState.monitoringEnabled -> startedMessage
        currentMessage == startedMessage && (!isServiceRunning || !monitoringState.monitoringEnabled) -> ""
        else -> currentMessage
    }
}

private fun requiredPermissions(snapshot: AppPermissionSnapshot): Array<String> = buildList {
    if (!snapshot.receiveSmsGranted) add(Manifest.permission.RECEIVE_SMS)
    if (snapshot.notificationPermissionRequired && !snapshot.postNotificationsGranted) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
