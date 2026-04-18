package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrySchedulingPlanTest {

    @Test
    fun buildRetrySchedulingPlan_returnsCancel_whenNoNextRetryAt() {
        val plan = buildRetrySchedulingPlan(
            nextRetryAt = null,
            now = 10_000L,
        )

        assertTrue(plan is RetrySchedulingPlan.Cancel)
    }

    @Test
    fun buildRetrySchedulingPlan_returnsSchedule_whenNextRetryAtExists() {
        val plan = buildRetrySchedulingPlan(
            nextRetryAt = 25_000L,
            now = 10_000L,
        )

        assertEquals(25_000L, (plan as RetrySchedulingPlan.Schedule).triggerAtMillis)
    }

    @Test
    fun buildRetrySchedulingPlan_clampsPastTimeToNow() {
        val plan = buildRetrySchedulingPlan(
            nextRetryAt = 8_000L,
            now = 10_000L,
        )

        assertEquals(10_000L, (plan as RetrySchedulingPlan.Schedule).triggerAtMillis)
    }
}
