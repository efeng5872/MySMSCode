package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationInjectionFlowTest {

    @Test
    fun findMatchingSimulationRule_matchesEnabledRuleByNormalizedNumber() {
        val rule = SenderRule(
            id = 1L,
            senderNumber = "13608083211",
            enabled = true,
            keywords = listOf("code"),
            selectedRobotIds = listOf(1L),
        )

        val matched = findMatchingSimulationRule(
            senderNumber = "+8613608083211",
            rules = listOf(rule),
        )

        assertEquals(1L, matched?.id)
    }

    @Test
    fun findMatchingSimulationRule_ignoresDisabledRule() {
        val rule = SenderRule(
            id = 1L,
            senderNumber = "13608083211",
            enabled = false,
            keywords = listOf("code"),
            selectedRobotIds = listOf(1L),
        )

        val matched = findMatchingSimulationRule(
            senderNumber = "+8613608083211",
            rules = listOf(rule),
        )

        assertNull(matched)
    }

    @Test
    fun findMatchingSimulationRule_prefersDisplayValueRuleWhenMultipleRulesMatch() {
        val matched = findMatchingSimulationRule(
            senderNumber = "13608083211",
            rules = listOf(
                SenderRule(
                    id = 1L,
                    senderNumber = "+8613608083211",
                    senderMatchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = listOf(1L),
                    updatedAt = 200L,
                ),
                SenderRule(
                    id = 2L,
                    senderNumber = "13608083211",
                    senderMatchMode = SenderMatchMode.DISPLAY_VALUE,
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = listOf(2L),
                    updatedAt = 100L,
                )
            ),
        )

        assertEquals(2L, matched?.id)
    }

    @Test
    fun buildSimulationFeedbackPlan_returnsPhasedMessages() {
        val plan = buildSimulationFeedbackPlan("10690001")

        assertEquals("已提交模拟请求，发送号码：10690001。", plan.submittedStatusMessage)
        assertEquals("模拟短信已命中规则，正在写入最近记录。", plan.matchedRuleStatusMessage)
        assertEquals("模拟短信已写入最近记录，请查看首页最新处理结果。", plan.completedStatusMessage)
        assertEquals("模拟短信请求已提交，但暂未看到最近记录，请点击刷新后再查看。", plan.timeoutStatusMessage)
        assertEquals(250L, plan.submittedStatusVisibleDelayMillis)
        assertEquals(listOf(250L, 500L, 1000L, 1500L, 2000L, 3000L), plan.refreshDelaysMillis)
        assertTrue(plan.finalRefreshBeforeTimeout)
        assertTrue(plan.navigateToHomeRecentRecords)
    }

    @Test
    fun buildDebugSimulationQuickAction_returnsSampleRequestInDebugBuild() {
        val quickAction = buildDebugSimulationQuickAction(isDebug = true)

        assertNotNull(quickAction)
        assertEquals("一键注入样例", quickAction?.actionLabel)
        assertEquals("13608083211", quickAction?.request?.senderNumber)
        assertEquals("test verification code is 223344", quickAction?.request?.messageBody)
    }

    @Test
    fun buildDebugSimulationQuickAction_returnsNullOutsideDebugBuild() {
        val quickAction = buildDebugSimulationQuickAction(isDebug = false)

        assertNull(quickAction)
    }

    @Test
    fun findInjectedSimulationRecord_returnsOnlyNewMatchingSimulationRecord() {
        val oldRecord = SmsRecordPreview(
            senderNumber = "10690001",
            messageBody = "test verification code is 123456",
            status = "SUCCESS",
            source = "SIMULATION",
            receivedAt = 1_000L,
        )
        val newRecord = SmsRecordPreview(
            senderNumber = "10690001",
            messageBody = "test verification code is 123456",
            status = "SUCCESS",
            source = "SIMULATION",
            receivedAt = 5_000L,
        )

        val found = findInjectedSimulationRecord(
            records = listOf(oldRecord, newRecord),
            senderNumber = "10690001",
            messageBody = "test verification code is 123456",
            submittedAt = 3_000L,
        )

        assertNotNull(found)
        assertEquals(5_000L, found?.receivedAt)
    }
}
