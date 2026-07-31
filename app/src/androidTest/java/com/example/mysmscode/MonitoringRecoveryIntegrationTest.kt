package com.example.mysmscode

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.MonitoringRecoveryTrigger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoringRecoveryIntegrationTest {

    private lateinit var harness: AndroidTestHarness

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        harness = installTestHarness(context)
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun stopMonitoring_persistsStoppedByUserStateBeforeServiceStops() {
        runBlocking {
            harness.container.settingsRepository.saveMonitoringState(
                MonitoringPersistenceState(
                    monitoringEnabled = true,
                    stoppedByUser = false,
                )
            )
        }

        MonitoringForegroundService.stopMonitoring(harness.appContext)

        waitUntil("停止监控后应持久化 stoppedByUser=true") {
            val state = runBlocking { harness.container.settingsRepository.getMonitoringState() }
            state.stoppedByUser && !state.monitoringEnabled && state.lastMonitoringStoppedAt != null
        }
    }

    @Test
    fun monitoringLifecycle_schedulesAndCancelsWatchdog() {
        MonitoringForegroundService.startMonitoring(harness.appContext)

        waitUntil("启动监控后应创建唯一看门狗任务") {
            WorkManager.getInstance(harness.appContext)
                .getWorkInfosForUniqueWork(MonitoringWatchdogScheduler.UNIQUE_WORK_NAME)
                .get()
                .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }

        MonitoringForegroundService.stopMonitoring(harness.appContext)

        waitUntil("停止监控后应取消看门狗任务") {
            val workInfos = WorkManager.getInstance(harness.appContext)
                .getWorkInfosForUniqueWork(MonitoringWatchdogScheduler.UNIQUE_WORK_NAME)
                .get()
            workInfos.isNotEmpty() && workInfos.all { it.state == WorkInfo.State.CANCELLED }
        }
    }

    @Test
    fun bootCompleted_recoversMonitoringWhenStateIsActive() {
        runBlocking {
            harness.container.settingsRepository.saveMonitoringState(
                MonitoringPersistenceState(
                    monitoringEnabled = true,
                    stoppedByUser = false,
                )
            )
        }

        MonitoringRecoveryReceiver().handleRecoveryForTest(
            harness.appContext,
            Intent.ACTION_BOOT_COMPLETED,
        )

        waitUntil("开机广播后应恢复监控并记录恢复来源") {
            val state = runBlocking { harness.container.settingsRepository.getMonitoringState() }
            state.monitoringEnabled &&
                !state.stoppedByUser &&
                state.lastRecoveryTrigger == MonitoringRecoveryTrigger.BOOT_COMPLETED.name &&
                state.lastRecoveryStartedAt != null
        }
    }

    @Test
    fun bootCompleted_doesNotRecoverWhenUserStoppedMonitoring() {
        runBlocking {
            harness.container.settingsRepository.saveMonitoringState(
                MonitoringPersistenceState(
                    monitoringEnabled = false,
                    stoppedByUser = true,
                    lastMonitoringStoppedAt = System.currentTimeMillis(),
                )
            )
        }

        MonitoringRecoveryReceiver().handleRecoveryForTest(
            harness.appContext,
            Intent.ACTION_BOOT_COMPLETED,
        )

        assertStaysTrue("用户主动停止后不应被开机恢复重新拉起") {
            val state = runBlocking { harness.container.settingsRepository.getMonitoringState() }
            !state.monitoringEnabled &&
                state.stoppedByUser &&
                state.lastRecoveryStartedAt == null &&
                state.lastRecoveryTrigger == null
        }

        val finalState = runBlocking { harness.container.settingsRepository.getMonitoringState() }
        assertFalse(finalState.monitoringEnabled)
        assertTrue(finalState.stoppedByUser)
        assertNull(finalState.lastRecoveryTrigger)
    }

    @Test
    fun stickyServiceRestart_recordsServiceRecovery() {
        runBlocking {
            harness.container.settingsRepository.saveMonitoringState(
                MonitoringPersistenceState(
                    monitoringEnabled = true,
                    stoppedByUser = false,
                    lastMonitoringStartedAt = 100L,
                )
            )
        }

        ContextCompat.startForegroundService(
            harness.appContext,
            Intent(harness.appContext, MonitoringForegroundService::class.java),
        )

        waitUntil("粘性重建应记录 SERVICE_RECOVERY 和心跳") {
            val state = runBlocking { harness.container.settingsRepository.getMonitoringState() }
            state.lastRecoveryTrigger == MonitoringRecoveryTrigger.SERVICE_RECOVERY.name &&
                state.lastRecoveryStartedAt != null &&
                state.lastServiceHeartbeatAt != null
        }
    }
}
