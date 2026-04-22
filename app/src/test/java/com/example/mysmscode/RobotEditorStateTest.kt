package com.example.mysmscode

import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.RobotWebhookStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotEditorStateTest {

    @Test
    fun `reset restores defaults and closes dialog`() {
        val state = RobotEditorState(
            editingRobotId = 8L,
            editingRobotCreatedAt = 30L,
            showRobotDialog = true,
            pendingDeleteRobotId = 9L,
            blockedRobotName = "old",
            robotName = "bot",
            robotWebhook = " https://hook ",
            robotEnabled = false,
            robotType = RobotType.WECOM,
            robotWebhookResetWarningVisible = true,
        )

        state.reset()

        assertNull(state.editingRobotId)
        assertEquals(0L, state.editingRobotCreatedAt)
        assertFalse(state.showRobotDialog)
        assertNull(state.pendingDeleteRobotId)
        assertNull(state.blockedRobotName)
        assertEquals("", state.robotName)
        assertEquals("", state.robotWebhook)
        assertTrue(state.robotEnabled)
        assertEquals(RobotType.FEISHU, state.robotType)
        assertFalse(state.robotWebhookResetWarningVisible)
    }

    @Test
    fun `open edit clears webhook and shows warning when reentry is required`() {
        val state = RobotEditorState()
        val robot = RobotEndpoint(
            id = 3L,
            name = "告警机器人",
            type = RobotType.WECOM,
            enabled = false,
            webhookUrl = "enc:payload",
            webhookStatus = RobotWebhookStatus.REENTRY_REQUIRED,
            createdAt = 55L,
            updatedAt = 77L,
        )

        state.openEdit(robot)

        assertEquals(3L, state.editingRobotId)
        assertEquals(55L, state.editingRobotCreatedAt)
        assertEquals("告警机器人", state.robotName)
        assertEquals("", state.robotWebhook)
        assertFalse(state.robotEnabled)
        assertEquals(RobotType.WECOM, state.robotType)
        assertTrue(state.robotWebhookResetWarningVisible)
        assertTrue(state.showRobotDialog)
    }

    @Test
    fun `update webhook clears reset warning after new value entered`() {
        val state = RobotEditorState(robotWebhookResetWarningVisible = true)

        state.updateWebhook("https://hook.example")

        assertEquals("https://hook.example", state.robotWebhook)
        assertFalse(state.robotWebhookResetWarningVisible)
    }

    @Test
    fun `build robot trims fields and preserves created time when editing`() {
        val state = RobotEditorState(
            editingRobotId = 6L,
            editingRobotCreatedAt = 88L,
            robotName = "  值班机器人 ",
            robotWebhook = " https://hook.example/path ",
            robotEnabled = false,
            robotType = RobotType.WECOM,
        )

        val result = state.buildRobot(now = 144L)

        assertEquals(6L, result.id)
        assertEquals("值班机器人", result.name)
        assertEquals("https://hook.example/path", result.webhookUrl)
        assertFalse(result.enabled)
        assertEquals(RobotType.WECOM, result.type)
        assertEquals(88L, result.createdAt)
        assertEquals(144L, result.updatedAt)
    }
}
