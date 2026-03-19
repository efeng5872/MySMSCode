package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStatusMessageTest {

    @Test
    fun resolveMonitoringStatusMessage_returnsStartCompletedWhenServiceIsRunning() {
        val message = resolveMonitoringStatusMessage(
            transition = MonitoringControlTransition.STARTING,
            isServiceRunning = true,
            requestMessage = "正在启动监控服务...",
            completedMessage = "监控服务已启动",
            fallbackMessage = "启动失败",
        )

        assertEquals("监控服务已启动", message)
    }

    @Test
    fun resolveMonitoringStatusMessage_returnsStopCompletedWhenServiceIsStopped() {
        val message = resolveMonitoringStatusMessage(
            transition = MonitoringControlTransition.STOPPING,
            isServiceRunning = false,
            requestMessage = "正在停止监控服务...",
            completedMessage = "监控服务已停止",
            fallbackMessage = "停止失败",
        )

        assertEquals("监控服务已停止", message)
    }

    @Test
    fun resolveMonitoringStatusMessage_returnsFallbackWhenExpectedStateIsNotReached() {
        val message = resolveMonitoringStatusMessage(
            transition = MonitoringControlTransition.STOPPING,
            isServiceRunning = true,
            requestMessage = "正在停止监控服务...",
            completedMessage = "监控服务已停止",
            fallbackMessage = "停止失败",
        )

        assertEquals("停止失败", message)
    }

    @Test
    fun calculateStatusMessageDelay_returnsRemainingDelayWhenRequestWasTooFast() {
        val delay = calculateStatusMessageDelayMillis(
            requestStartedAtMillis = 1_000L,
            nowMillis = 1_180L,
            minimumVisibleMillis = 600L,
        )

        assertEquals(420L, delay)
    }

    @Test
    fun calculateStatusMessageDelay_returnsZeroWhenRequestWasVisibleLongEnough() {
        val delay = calculateStatusMessageDelayMillis(
            requestStartedAtMillis = 1_000L,
            nowMillis = 1_800L,
            minimumVisibleMillis = 600L,
        )

        assertEquals(0L, delay)
    }
}
