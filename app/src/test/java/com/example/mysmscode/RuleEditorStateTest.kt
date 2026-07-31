package com.example.mysmscode

import com.example.mysmscode.domain.RuleSenderInputMode
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditorStateTest {

    @Test
    fun `reset restores defaults and closes dialog`() {
        val state = RuleEditorState(
            editingRuleId = 9L,
            editingRuleCreatedAt = 20L,
            showRuleDialog = true,
            pendingDeleteRuleId = 11L,
            ruleName = "验证码规则",
            ruleSenderInputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER.name,
            senderNumber = "13608083211",
            rawSenderDisplay = "10654321",
            selectedCountryRegion = "GB",
            keywordText = "otp",
            ruleEnabled = false,
            selectedRobotIds = listOf(1L, 2L),
        )

        state.reset()

        assertNull(state.editingRuleId)
        assertEquals(0L, state.editingRuleCreatedAt)
        assertFalse(state.showRuleDialog)
        assertNull(state.pendingDeleteRuleId)
        assertEquals("", state.ruleName)
        assertEquals(RuleSenderInputMode.DISPLAY_VALUE.name, state.ruleSenderInputMode)
        assertEquals("", state.senderNumber)
        assertEquals("", state.rawSenderDisplay)
        assertEquals("CN", state.selectedCountryRegion)
        assertEquals("", state.keywordText)
        assertTrue(state.ruleEnabled)
        assertTrue(state.selectedRobotIds.isEmpty())
    }

    @Test
    fun `open edit hydrates rule draft from saved rule`() {
        val state = RuleEditorState()
        val rule = SenderRule(
            id = 7L,
            name = "银行验证码",
            senderNumber = "+8613608083211",
            senderMatchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
            enabled = false,
            keywords = listOf("code", "otp"),
            selectedRobotIds = listOf(3L, 4L),
            createdAt = 99L,
            updatedAt = 120L,
        )

        state.openEdit(rule)

        assertEquals(7L, state.editingRuleId)
        assertEquals(99L, state.editingRuleCreatedAt)
        assertTrue(state.showRuleDialog)
        assertEquals("银行验证码", state.ruleName)
        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER.name, state.ruleSenderInputMode)
        assertEquals("CN", state.selectedCountryRegion)
        assertEquals("13608083211", state.senderNumber)
        assertEquals("+8613608083211", state.rawSenderDisplay)
        assertEquals("code, otp", state.keywordText)
        assertFalse(state.ruleEnabled)
        assertEquals(listOf(3L, 4L), state.selectedRobotIds.toList())
    }

    @Test
    fun `apply input mode change keeps sender value available for next mode`() {
        val state = RuleEditorState(
            ruleSenderInputMode = RuleSenderInputMode.DISPLAY_VALUE.name,
            rawSenderDisplay = "1065896654201",
        )

        state.applyInputModeChange(RuleSenderInputMode.INTERNATIONAL_NUMBER)

        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER.name, state.ruleSenderInputMode)
        assertEquals("CN", state.selectedCountryRegion)
        assertEquals("1065896654201", state.senderNumber)
    }

    @Test
    fun `build rule normalizes current draft`() {
        val state = RuleEditorState(
            editingRuleId = 5L,
            editingRuleCreatedAt = 42L,
            ruleName = " 英国验证码 ",
            ruleSenderInputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER.name,
            senderNumber = "07911123456",
            selectedCountryRegion = "GB",
            keywordText = " otp, code , , ",
            ruleEnabled = false,
            selectedRobotIds = listOf(8L, 9L),
        )

        val result = state.buildRule(now = 123L)

        assertEquals(5L, result.id)
        assertEquals("英国验证码", result.name)
        assertEquals("+447911123456", result.senderNumber)
        assertEquals(SenderMatchMode.INTERNATIONAL_NUMBER, result.senderMatchMode)
        assertFalse(result.enabled)
        assertEquals(listOf("otp", "code"), result.keywords)
        assertEquals(listOf(8L, 9L), result.selectedRobotIds)
        assertEquals(42L, result.createdAt)
        assertEquals(123L, result.updatedAt)
    }
}
