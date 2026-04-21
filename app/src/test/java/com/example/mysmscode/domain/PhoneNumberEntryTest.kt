package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberEntryTest {

    @Test
    fun `split sender number for editing keeps china number as local part`() {
        val draft = splitSenderNumberForEditing("+8613608083211")

        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER, draft.inputMode)
        assertEquals("CN", draft.countryOption.regionCode)
        assertEquals("13608083211", draft.localNumber)
    }

    @Test
    fun `build rule sender number normalizes by selected country`() {
        val senderNumber = buildRuleSenderNumber(
            inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
            countryOption = findCountryOption("GB"),
            localNumber = "07911123456",
            displaySender = "",
        )

        assertEquals("+447911123456", senderNumber)
    }

    @Test
    fun `build rule sender number keeps display sender unchanged`() {
        val senderNumber = buildRuleSenderNumber(
            inputMode = RuleSenderInputMode.DISPLAY_VALUE,
            countryOption = defaultCountryOption(),
            localNumber = "",
            displaySender = "10654321",
        )

        assertEquals("10654321", senderNumber)
    }

    @Test
    fun `transform rule draft keeps display sender when switching to international`() {
        val draft = transformRuleNumberDraft(
            currentInputMode = RuleSenderInputMode.DISPLAY_VALUE,
            targetInputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
            currentCountryOption = defaultCountryOption(),
            currentLocalNumber = "",
            currentDisplaySender = "1065896654201",
        )

        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER, draft.inputMode)
        assertEquals("CN", draft.countryOption.regionCode)
        assertEquals("1065896654201", draft.localNumber)
    }

    @Test
    fun `transform rule draft keeps current international input when switching to display`() {
        val draft = transformRuleNumberDraft(
            currentInputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
            targetInputMode = RuleSenderInputMode.DISPLAY_VALUE,
            currentCountryOption = defaultCountryOption(),
            currentLocalNumber = "1065896654201",
            currentDisplaySender = "",
        )

        assertEquals(RuleSenderInputMode.DISPLAY_VALUE, draft.inputMode)
        assertEquals("+861065896654201", draft.displaySender)
    }
}
