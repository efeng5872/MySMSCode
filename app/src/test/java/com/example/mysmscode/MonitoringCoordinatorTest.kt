package com.example.mysmscode

import android.Manifest
import com.example.mysmscode.domain.AppPermissionSnapshot
import com.example.mysmscode.domain.MonitoringControlTransition
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.PermissionUiState
import com.example.mysmscode.domain.RetryPolicyConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringCoordinatorTest {

    @Test
    fun `launch permission request only asks for missing permissions`() {
        val launched = mutableListOf<Array<String>>()
        var autoRequested = false
        val coordinator = createCoordinator()

        coordinator.launchPermissionRequest(
            permissionSnapshot = AppPermissionSnapshot(
                receiveSmsGranted = false,
                postNotificationsGranted = false,
                notificationPermissionRequired = true,
            ),
            onAutoRequested = { autoRequested = true },
            launchPermissions = { launched += it },
        )

        assertTrue(autoRequested)
        assertEquals(1, launched.size)
        assertEquals(
            listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.POST_NOTIFICATIONS),
            launched.single().toList(),
        )
    }

    @Test
    fun `refresh runtime state reads sources and reconciles passive started message`() = runBlocking {
        val coordinator = createCoordinator(
            retryPolicyConfig = RetryPolicyConfig(15, 45, 90),
            monitoringStates = mutableListOf(
                MonitoringPersistenceState(monitoringEnabled = true, stoppedByUser = false),
            ),
            permissionSnapshot = AppPermissionSnapshot(
                receiveSmsGranted = true,
                postNotificationsGranted = true,
                notificationPermissionRequired = true,
            ),
            serviceRunningValues = mutableListOf(true),
        )

        val result = coordinator.refreshRuntimeState(
            currentMessage = "",
            transition = MonitoringControlTransition.IDLE,
            startedMessage = "监控服务已启动",
        )

        assertEquals(15, result.retryPolicyConfig.firstRetryDelaySeconds)
        assertTrue(result.monitoringServiceRunning)
        assertTrue(result.monitoringPersistenceState.monitoringEnabled)
        assertEquals("监控服务已启动", result.statusMessage)
    }

    @Test
    fun `toggle monitoring returns permission message when permissions are missing`() = runBlocking {
        val progress = mutableListOf<Pair<MonitoringControlTransition, String>>()
        val coordinator = createCoordinator()

        val result = coordinator.toggleMonitoring(
            input = MonitoringToggleInput(
                monitoringServiceRunning = false,
                monitoringPersistenceState = MonitoringPersistenceState(),
                permissionUiState = PermissionUiState(
                    title = "需要权限",
                    message = "缺少权限",
                    actionLabel = "申请权限",
                    canStartMonitoring = false,
                ),
            ),
            messages = messages(),
            onProgress = { transition, status -> progress += transition to status },
        )

        assertTrue(progress.isEmpty())
        assertEquals("缺少权限", result.statusMessage)
        assertFalse(result.shouldRefreshRuntimeState)
    }

    @Test
    fun `toggle monitoring start path marks state started when service comes up`() = runBlocking {
        val progress = mutableListOf<Pair<MonitoringControlTransition, String>>()
        var started = false
        val coordinator = createCoordinator(
            monitoringStates = mutableListOf(
                MonitoringPersistenceState(monitoringEnabled = true, stoppedByUser = false),
            ),
            serviceRunningValues = mutableListOf(true),
            onStartMonitoring = { started = true },
        )

        val result = coordinator.toggleMonitoring(
            input = MonitoringToggleInput(
                monitoringServiceRunning = false,
                monitoringPersistenceState = MonitoringPersistenceState(),
                permissionUiState = PermissionUiState(
                    title = "权限已就绪",
                    message = "ok",
                    actionLabel = "权限已就绪",
                    canStartMonitoring = true,
                ),
            ),
            messages = messages(),
            onProgress = { transition, status -> progress += transition to status },
        )

        assertTrue(started)
        assertEquals(listOf(MonitoringControlTransition.STARTING to "正在启动监控服务..."), progress)
        assertTrue(result.monitoringServiceRunning)
        assertTrue(result.monitoringPersistenceState.monitoringEnabled)
        assertFalse(result.monitoringPersistenceState.stoppedByUser)
        assertEquals(1_000L, result.monitoringPersistenceState.lastMonitoringStartedAt)
        assertEquals("监控服务已启动", result.statusMessage)
        assertTrue(result.shouldRefreshRuntimeState)
    }

    @Test
    fun `toggle monitoring stop path marks state stopped when service goes down`() = runBlocking {
        val progress = mutableListOf<Pair<MonitoringControlTransition, String>>()
        var stopped = false
        val coordinator = createCoordinator(
            monitoringStates = mutableListOf(
                MonitoringPersistenceState(monitoringEnabled = false, stoppedByUser = false),
            ),
            serviceRunningValues = mutableListOf(false),
            onStopMonitoring = { stopped = true },
        )

        val result = coordinator.toggleMonitoring(
            input = MonitoringToggleInput(
                monitoringServiceRunning = true,
                monitoringPersistenceState = MonitoringPersistenceState(
                    monitoringEnabled = true,
                    stoppedByUser = false,
                ),
                permissionUiState = PermissionUiState(
                    title = "权限已就绪",
                    message = "ok",
                    actionLabel = "权限已就绪",
                    canStartMonitoring = true,
                ),
            ),
            messages = messages(),
            onProgress = { transition, status -> progress += transition to status },
        )

        assertTrue(stopped)
        assertEquals(listOf(MonitoringControlTransition.STOPPING to "正在停止监控服务..."), progress)
        assertFalse(result.monitoringServiceRunning)
        assertFalse(result.monitoringPersistenceState.monitoringEnabled)
        assertTrue(result.monitoringPersistenceState.stoppedByUser)
        assertEquals(1_000L, result.monitoringPersistenceState.lastMonitoringStoppedAt)
        assertEquals("监控服务已停止", result.statusMessage)
        assertTrue(result.shouldRefreshRuntimeState)
    }

    private fun createCoordinator(
        retryPolicyConfig: RetryPolicyConfig = RetryPolicyConfig.default(),
        monitoringStates: MutableList<MonitoringPersistenceState> = mutableListOf(MonitoringPersistenceState()),
        permissionSnapshot: AppPermissionSnapshot = AppPermissionSnapshot(
            receiveSmsGranted = true,
            postNotificationsGranted = true,
            notificationPermissionRequired = true,
        ),
        serviceRunningValues: MutableList<Boolean> = mutableListOf(false),
        onStartMonitoring: () -> Unit = {},
        onStopMonitoring: () -> Unit = {},
    ): MonitoringCoordinator {
        return MonitoringCoordinator(
            readRetryPolicyConfig = { retryPolicyConfig },
            readMonitoringState = { nextValue(monitoringStates) },
            readPermissionSnapshot = { permissionSnapshot },
            readServiceRunning = { nextValue(serviceRunningValues) },
            startMonitoring = onStartMonitoring,
            stopMonitoring = onStopMonitoring,
            nowProvider = { 1_000L },
            delayMillis = {},
        )
    }

    private fun messages(): MonitoringMessages {
        return MonitoringMessages(
            startedMessage = "监控服务已启动",
            startRequestedMessage = "正在启动监控服务...",
            startFailedMessage = "启动失败",
            stoppedMessage = "监控服务已停止",
            stopRequestedMessage = "正在停止监控服务...",
            stopFailedMessage = "停止失败",
        )
    }

    private fun <T> nextValue(values: MutableList<T>): T {
        if (values.size == 1) {
            return values.first()
        }
        return values.removeAt(0)
    }
}
