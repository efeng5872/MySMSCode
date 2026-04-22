package com.example.mysmscode

import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SimulationInjectionRequest
import com.example.mysmscode.domain.SmsRecordPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCoordinatorTest {

    @Test
    fun `execute shows mismatch and skips enqueue when no rule matches`() {
        val statuses = mutableListOf<String>()
        var enqueueCount = 0
        var navigateCount = 0
        val coordinator = SimulationCoordinator(
            enqueueSimulation = { enqueueCount += 1 },
            currentRecordsProvider = { emptyList() },
            updateSimulationStatus = { statuses += it },
            navigateToHomeRecentRecords = { navigateCount += 1 },
            delayMillis = {},
        )

        runBlocking {
            coordinator.execute(
                request = SimulationInjectionRequest(
                    senderNumber = "10690001",
                    messageBody = "test verification code is 123456",
                ),
                rules = emptyList(),
            )
        }

        assertEquals(listOf("模拟短信未命中任何已启用号码规则，未写入最近记录。"), statuses)
        assertEquals(0, enqueueCount)
        assertEquals(0, navigateCount)
    }

    @Test
    fun `execute updates status and completes when recent record appears during refresh`() {
        val statuses = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val enqueuedRequests = mutableListOf<SimulationInjectionRequest>()
        var navigateCount = 0
        var currentRecords = emptyList<SmsRecordPreview>()
        val coordinator = SimulationCoordinator(
            enqueueSimulation = { request -> enqueuedRequests += request },
            currentRecordsProvider = { currentRecords },
            updateSimulationStatus = { status -> statuses += status },
            navigateToHomeRecentRecords = { navigateCount += 1 },
            nowProvider = { 1_000L },
            delayMillis = { delayValue ->
                delays += delayValue
                if (delayValue == 500L) {
                    currentRecords = listOf(
                        SmsRecordPreview(
                            senderNumber = "10690001",
                            messageBody = "test verification code is 123456",
                            status = "SUCCESS",
                            source = "SIMULATION",
                            receivedAt = 2_000L,
                        )
                    )
                }
            },
        )

        runBlocking {
            coordinator.execute(
                request = SimulationInjectionRequest(
                    senderNumber = "10690001",
                    messageBody = "test verification code is 123456",
                ),
                rules = listOf(
                    SenderRule(
                        id = 1L,
                        senderNumber = "10690001",
                        enabled = true,
                        keywords = listOf("code"),
                        selectedRobotIds = listOf(1L),
                    )
                ),
            )
        }

        assertEquals(1, enqueuedRequests.size)
        assertEquals(1, navigateCount)
        assertEquals(listOf(250L, 250L, 500L), delays)
        assertEquals(
            listOf(
                "已提交模拟请求，发送号码：10690001。",
                "模拟短信已命中规则，正在写入最近记录。",
                "模拟短信已写入最近记录，请查看首页最新处理结果。",
            ),
            statuses,
        )
    }

    @Test
    fun `execute reports timeout when no matching recent record is written`() {
        val statuses = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        var navigateCount = 0
        val coordinator = SimulationCoordinator(
            enqueueSimulation = {},
            currentRecordsProvider = { emptyList() },
            updateSimulationStatus = { status -> statuses += status },
            navigateToHomeRecentRecords = { navigateCount += 1 },
            nowProvider = { 1_000L },
            delayMillis = { delayValue -> delays += delayValue },
        )

        runBlocking {
            coordinator.execute(
                request = SimulationInjectionRequest(
                    senderNumber = "10690001",
                    messageBody = "test verification code is 123456",
                ),
                rules = listOf(
                    SenderRule(
                        id = 1L,
                        senderNumber = "10690001",
                        enabled = true,
                        keywords = listOf("code"),
                        selectedRobotIds = listOf(1L),
                    )
                ),
            )
        }

        assertEquals(1, navigateCount)
        assertEquals(listOf(250L, 250L, 500L, 1000L, 1500L, 2000L, 3000L), delays)
        assertTrue(statuses.last().contains("暂未看到最近记录"))
    }
}
