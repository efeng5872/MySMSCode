package com.example.mysmscode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.requiresWebhookReentry

class RobotEditorState(
    editingRobotId: Long? = null,
    editingRobotCreatedAt: Long = 0L,
    showRobotDialog: Boolean = false,
    pendingDeleteRobotId: Long? = null,
    blockedRobotName: String? = null,
    robotName: String = "",
    robotWebhook: String = "",
    robotEnabled: Boolean = true,
    robotType: RobotType = RobotType.FEISHU,
    robotWebhookResetWarningVisible: Boolean = false,
) {
    var editingRobotId by mutableStateOf(editingRobotId)
    var editingRobotCreatedAt by mutableStateOf(editingRobotCreatedAt)
    var showRobotDialog by mutableStateOf(showRobotDialog)
    var pendingDeleteRobotId by mutableStateOf(pendingDeleteRobotId)
    var blockedRobotName by mutableStateOf(blockedRobotName)
    var robotName by mutableStateOf(robotName)
    var robotWebhook by mutableStateOf(robotWebhook)
    var robotEnabled by mutableStateOf(robotEnabled)
    var robotType by mutableStateOf(robotType)
    var robotWebhookResetWarningVisible by mutableStateOf(robotWebhookResetWarningVisible)

    fun reset() {
        editingRobotId = null
        editingRobotCreatedAt = 0L
        robotName = ""
        robotWebhook = ""
        robotEnabled = true
        robotType = RobotType.FEISHU
        robotWebhookResetWarningVisible = false
        showRobotDialog = false
        pendingDeleteRobotId = null
        blockedRobotName = null
    }

    fun openCreate() {
        reset()
        showRobotDialog = true
    }

    fun openEdit(robot: RobotEndpoint) {
        editingRobotId = robot.id
        editingRobotCreatedAt = robot.createdAt
        robotName = robot.name
        robotWebhook = if (robot.requiresWebhookReentry()) "" else robot.webhookUrl
        robotEnabled = robot.enabled
        robotType = robot.type
        robotWebhookResetWarningVisible = robot.requiresWebhookReentry()
        pendingDeleteRobotId = null
        blockedRobotName = null
        showRobotDialog = true
    }

    fun updateWebhook(value: String) {
        robotWebhook = value
        if (value.isNotBlank()) {
            robotWebhookResetWarningVisible = false
        }
    }

    fun buildRobot(now: Long): RobotEndpoint {
        return RobotEndpoint(
            id = editingRobotId ?: 0L,
            name = robotName.trim(),
            type = robotType,
            enabled = robotEnabled,
            webhookUrl = robotWebhook.trim(),
            createdAt = if (editingRobotId == null) now else editingRobotCreatedAt,
            updatedAt = now,
        )
    }

    companion object {
        val Saver = listSaver<RobotEditorState, Any?>(
            save = { state ->
                listOf(
                    state.editingRobotId,
                    state.editingRobotCreatedAt,
                    state.showRobotDialog,
                    state.pendingDeleteRobotId,
                    state.blockedRobotName,
                    state.robotName,
                    state.robotWebhook,
                    state.robotEnabled,
                    state.robotType.name,
                    state.robotWebhookResetWarningVisible,
                )
            },
            restore = { values ->
                RobotEditorState(
                    editingRobotId = values[0] as Long?,
                    editingRobotCreatedAt = values[1] as Long,
                    showRobotDialog = values[2] as Boolean,
                    pendingDeleteRobotId = values[3] as Long?,
                    blockedRobotName = values[4] as String?,
                    robotName = values[5] as String,
                    robotWebhook = values[6] as String,
                    robotEnabled = values[7] as Boolean,
                    robotType = RobotType.valueOf(values[8] as String),
                    robotWebhookResetWarningVisible = values[9] as Boolean,
                )
            },
        )
    }
}

@Composable
fun rememberRobotEditorState(): RobotEditorState {
    return rememberSaveable(saver = RobotEditorState.Saver) {
        RobotEditorState()
    }
}
