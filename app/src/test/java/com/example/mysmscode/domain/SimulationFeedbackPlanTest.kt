package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationFeedbackPlanTest {

    @Test
    fun buildSimulationFeedback_returnsStatusAndRefreshSchedule() {
        val plan = buildSimulationFeedbackPlan("10690001")

        assertEquals("已提交模拟请求，发送号码：10690001。", plan.submittedStatusMessage)
        assertEquals(listOf(250L, 500L, 1000L, 1500L, 2000L, 3000L), plan.refreshDelaysMillis)
        assertEquals(true, plan.finalRefreshBeforeTimeout)
    }
}
