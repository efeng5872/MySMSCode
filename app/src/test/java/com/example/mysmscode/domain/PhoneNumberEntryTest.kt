package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberEntryTest {

    @Test
    fun `split sender number for editing keeps china number as local part`() {
        val draft = splitSenderNumberForEditing("+8613608083211")

        assertEquals("CN", draft.countryOption.regionCode)
        assertEquals("13608083211", draft.localNumber)
    }

    @Test
    fun `build rule sender number normalizes by selected country`() {
        val senderNumber = buildRuleSenderNumber(
            countryOption = findCountryOption("GB"),
            localNumber = "07911123456",
        )

        assertEquals("+447911123456", senderNumber)
    }
}
