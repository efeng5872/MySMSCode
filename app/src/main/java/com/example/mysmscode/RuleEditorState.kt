package com.example.mysmscode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.mysmscode.domain.RuleSenderInputMode
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.buildRuleSenderNumber
import com.example.mysmscode.domain.defaultRuleNumberDraft
import com.example.mysmscode.domain.findCountryOption
import com.example.mysmscode.domain.splitSenderNumberForEditing
import com.example.mysmscode.domain.transformRuleNumberDraft

class RuleEditorState(
    editingRuleId: Long? = null,
    editingRuleCreatedAt: Long = 0L,
    showRuleDialog: Boolean = false,
    pendingDeleteRuleId: Long? = null,
    ruleName: String = "",
    ruleSenderInputMode: String = defaultRuleNumberDraft().inputMode.name,
    senderNumber: String = "",
    rawSenderDisplay: String = "",
    selectedCountryRegion: String = defaultRuleNumberDraft().countryOption.regionCode,
    keywordText: String = "",
    ruleEnabled: Boolean = true,
    selectedRobotIds: List<Long> = emptyList(),
) {
    var editingRuleId by mutableStateOf(editingRuleId)
    var editingRuleCreatedAt by mutableStateOf(editingRuleCreatedAt)
    var showRuleDialog by mutableStateOf(showRuleDialog)
    var pendingDeleteRuleId by mutableStateOf(pendingDeleteRuleId)
    var ruleName by mutableStateOf(ruleName)
    var ruleSenderInputMode by mutableStateOf(ruleSenderInputMode)
    var senderNumber by mutableStateOf(senderNumber)
    var rawSenderDisplay by mutableStateOf(rawSenderDisplay)
    var selectedCountryRegion by mutableStateOf(selectedCountryRegion)
    var keywordText by mutableStateOf(keywordText)
    var ruleEnabled by mutableStateOf(ruleEnabled)
    val selectedRobotIds = mutableStateListOf<Long>().also { it.addAll(selectedRobotIds) }

    val selectedInputMode: RuleSenderInputMode
        get() = RuleSenderInputMode.valueOf(ruleSenderInputMode)

    fun reset() {
        val defaultDraft = defaultRuleNumberDraft()
        editingRuleId = null
        editingRuleCreatedAt = 0L
        ruleName = ""
        ruleSenderInputMode = defaultDraft.inputMode.name
        senderNumber = defaultDraft.localNumber
        rawSenderDisplay = defaultDraft.displaySender
        selectedCountryRegion = defaultDraft.countryOption.regionCode
        keywordText = ""
        ruleEnabled = true
        selectedRobotIds.clear()
        showRuleDialog = false
        pendingDeleteRuleId = null
    }

    fun openCreate() {
        reset()
        showRuleDialog = true
    }

    fun openEdit(rule: SenderRule) {
        val numberDraft = splitSenderNumberForEditing(rule.senderNumber, rule.senderMatchMode)
        editingRuleId = rule.id
        editingRuleCreatedAt = rule.createdAt
        ruleName = rule.name
        ruleSenderInputMode = numberDraft.inputMode.name
        selectedCountryRegion = numberDraft.countryOption.regionCode
        senderNumber = numberDraft.localNumber
        rawSenderDisplay = numberDraft.displaySender
        keywordText = rule.keywords.joinToString()
        ruleEnabled = rule.enabled
        selectedRobotIds.clear()
        selectedRobotIds.addAll(rule.selectedRobotIds)
        pendingDeleteRuleId = null
        showRuleDialog = true
    }

    fun applyInputModeChange(targetMode: RuleSenderInputMode) {
        val transformedDraft = transformRuleNumberDraft(
            currentInputMode = selectedInputMode,
            targetInputMode = targetMode,
            currentCountryOption = findCountryOption(selectedCountryRegion),
            currentLocalNumber = senderNumber,
            currentDisplaySender = rawSenderDisplay,
        )
        ruleSenderInputMode = transformedDraft.inputMode.name
        selectedCountryRegion = transformedDraft.countryOption.regionCode
        senderNumber = transformedDraft.localNumber
        rawSenderDisplay = transformedDraft.displaySender
    }

    fun toggleRobot(robotId: Long, checked: Boolean) {
        if (checked) {
            if (!selectedRobotIds.contains(robotId)) {
                selectedRobotIds.add(robotId)
            }
        } else {
            selectedRobotIds.remove(robotId)
        }
    }

    fun buildRule(now: Long): SenderRule {
        return SenderRule(
            id = editingRuleId ?: 0L,
            name = ruleName.trim(),
            senderNumber = buildRuleSenderNumber(
                inputMode = selectedInputMode,
                countryOption = findCountryOption(selectedCountryRegion),
                localNumber = senderNumber,
                displaySender = rawSenderDisplay,
            ),
            senderMatchMode = when (selectedInputMode) {
                RuleSenderInputMode.DISPLAY_VALUE -> SenderMatchMode.DISPLAY_VALUE
                RuleSenderInputMode.INTERNATIONAL_NUMBER -> SenderMatchMode.INTERNATIONAL_NUMBER
            },
            enabled = ruleEnabled,
            keywords = keywordText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            selectedRobotIds = selectedRobotIds.toList(),
            createdAt = if (editingRuleId == null) now else editingRuleCreatedAt,
            updatedAt = now,
        )
    }

    companion object {
        val Saver = listSaver<RuleEditorState, Any?>(
            save = { state ->
                listOf(
                    state.editingRuleId,
                    state.editingRuleCreatedAt,
                    state.showRuleDialog,
                    state.pendingDeleteRuleId,
                    state.ruleName,
                    state.ruleSenderInputMode,
                    state.senderNumber,
                    state.rawSenderDisplay,
                    state.selectedCountryRegion,
                    state.keywordText,
                    state.ruleEnabled,
                    ArrayList(state.selectedRobotIds),
                )
            },
            restore = { values ->
                RuleEditorState(
                    editingRuleId = values[0] as Long?,
                    editingRuleCreatedAt = values[1] as Long,
                    showRuleDialog = values[2] as Boolean,
                    pendingDeleteRuleId = values[3] as Long?,
                    ruleName = values[4] as String,
                    ruleSenderInputMode = values[5] as String,
                    senderNumber = values[6] as String,
                    rawSenderDisplay = values[7] as String,
                    selectedCountryRegion = values[8] as String,
                    keywordText = values[9] as String,
                    ruleEnabled = values[10] as Boolean,
                    selectedRobotIds = (values[11] as List<*>).filterIsInstance<Long>(),
                )
            },
        )
    }
}

@Composable
fun rememberRuleEditorState(): RuleEditorState {
    return rememberSaveable(saver = RuleEditorState.Saver) {
        RuleEditorState()
    }
}
