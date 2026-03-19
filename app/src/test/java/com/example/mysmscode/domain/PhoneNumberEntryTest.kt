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
}
