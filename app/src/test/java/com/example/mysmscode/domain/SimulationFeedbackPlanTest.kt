package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SimulationFeedbackPlanTest {

    @Test
    fun buildSimulationFeedback_returnsStatusAndRefreshSchedule() {
        val plan = buildSimulationFeedbackPlan("10690001")

        assertEquals("Simulation enqueued for 10690001.", plan.initialStatusMessage)
        assertEquals(listOf(250L, 1500L), plan.refreshDelaysMillis)
    }
}