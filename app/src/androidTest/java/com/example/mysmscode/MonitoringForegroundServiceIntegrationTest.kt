package com.example.mysmscode

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mysmscode.data.RepositorySaveResult
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoringForegroundServiceIntegrationTest {

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
    fun enqueueSimulation_ignoresMessagesWhenMonitoringIsStopped() = runBlocking {
        harness.container.settingsRepository.saveMonitoringState(
            MonitoringPersistenceState(
                monitoringEnabled = false,
                stoppedByUser = true,
            )
        )

        MonitoringForegroundService.enqueueSimulation(
            harness.appContext,
            "10690001",
            "test verification code is 123456",
        )

        assertStaysTrue("停止监控后不应继续写入短信记录或派发 webhook") {
            runBlocking { harness.container.processingRepository.countRecords() == 0 } &&
                harness.dispatcher.dispatchCalls.isEmpty()
        }
    }

    @Test
    fun enqueueSimulation_persistsMatchedSmsAndDispatchesWebhookWhenMonitoringIsActive() = runBlocking {
        val now = System.currentTimeMillis()
        val robot = when (
            val result = harness.container.robotRepository.save(
                RobotEndpoint(
                    name = "测试飞书机器人",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://example.invalid/feishu",
                    createdAt = now,
                    updatedAt = now,
                )
            )
        ) {
            is RepositorySaveResult.Success -> result.value
            else -> throw AssertionError("机器人保存失败：$result")
        }
        when (
            harness.container.senderRuleRepository.save(
                SenderRule(
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = listOf(robot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            )
        ) {
            is RepositorySaveResult.Success -> Unit
            else -> throw AssertionError("规则保存失败")
        }
        harness.container.settingsRepository.saveMonitoringState(
            MonitoringPersistenceState(
                monitoringEnabled = true,
                stoppedByUser = false,
            )
        )

        MonitoringForegroundService.enqueueSimulation(
            harness.appContext,
            "10690001",
            "test verification code is 654321",
        )

        waitUntil("模拟短信应进入前台服务处理链并落库") {
            runBlocking { harness.container.processingRepository.countRecords() == 1 }
        }

        val record = harness.container.processingRepository.getRecentRecords(limit = 1).single()
        assertEquals("10690001", record.senderNumber)
        assertEquals("SIMULATION", record.source)
        assertEquals("SUCCESS", record.status)
        assertEquals(1, harness.dispatcher.dispatchCalls.size)
        assertTrue(harness.dispatcher.dispatchCalls.single().message.messageBody.contains("654321"))
    }
}
