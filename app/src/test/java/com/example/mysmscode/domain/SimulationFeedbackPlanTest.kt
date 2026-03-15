package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationFeedbackPlanTest {

    @Test
    fun buildSimulationFeedback_returnsStatusAndRefreshSchedule() {
        val plan = buildSimulationFeedbackPlan("10690001")

        assertEquals("已加入模拟短信，发送号码：10690001。", plan.initialStatusMessage)
        assertEquals(listOf(250L, 1500L), plan.refreshDelaysMillis)
    }
}
