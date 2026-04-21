package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberNormalizerTest {

    private val normalizer = PhoneNumberNormalizer(defaultRegion = "CN")

    @Test
    fun normalize_convertsChinaLocalNumberToE164() {
        val normalized = normalizer.normalize("13608083211")

        assertEquals("+8613608083211", normalized)
    }

    @Test
    fun normalize_keepsEquivalentChinaInternationalFormatsAligned() {
        val local = normalizer.normalize("13608083211")
        val international = normalizer.normalize("+8613608083211")
        val internationalPrefix = normalizer.normalize("008613608083211")

        assertEquals(local, international)
        assertEquals(local, internationalPrefix)
    }

    @Test
    fun normalize_convertsUsNumberToE164() {
        val normalized = normalizer.normalize("+1 415-555-2671")

        assertEquals("+14155552671", normalized)
    }

    @Test
    fun normalize_convertsUkNumberToE164() {
        val normalized = normalizer.normalize("+44 20 7946 0958")

        assertEquals("+442079460958", normalized)
    }

    @Test
    fun normalize_fallsBackToDigitPlusCompactionWhenParsingFails() {
        val normalized = normalizer.normalize("sender-ALPHA-001")

        assertEquals("001", normalized)
    }

    @Test
    fun matches_returnsTrueForEquivalentChinaNumberRepresentations() {
        val matched = normalizer.matches(
            configuredSender = "13608083211",
            incomingSender = "+8613608083211",
            matchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
        )

        assertTrue(matched)
    }

    @Test
    fun matches_returnsTrueForRawDisplayShortCode() {
        val matched = normalizer.matches(
            configuredSender = "10654321",
            incomingSender = "10654321",
            matchMode = SenderMatchMode.DISPLAY_VALUE,
        )

        assertTrue(matched)
    }

    @Test
    fun matches_returnsFalseForDifferentRawDisplayValues() {
        val matched = normalizer.matches(
            configuredSender = "10654321",
            incomingSender = "10654322",
            matchMode = SenderMatchMode.DISPLAY_VALUE,
        )

        assertFalse(matched)
    }

    @Test
    fun matches_displayValueModeDoesNotTreatCountryCodeVariantAsSameNumber() {
        val matched = normalizer.matches(
            configuredSender = "13608083211",
            incomingSender = "+8613608083211",
            matchMode = SenderMatchMode.DISPLAY_VALUE,
        )

        assertFalse(matched)
    }


    @Test
    fun splitSenderNumberForEditing_returnsDisplayModeForShortCode() {
        val draft = splitSenderNumberForEditing("10654321")

        assertEquals(RuleSenderInputMode.DISPLAY_VALUE, draft.inputMode)
        assertEquals("10654321", draft.displaySender)
        assertEquals("", draft.localNumber)
    }

    @Test
    fun splitSenderNumberForEditing_returnsInternationalModeForPhoneNumber() {
        val draft = splitSenderNumberForEditing("+8613608083211")

        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER, draft.inputMode)
        assertEquals("CN", draft.countryOption.regionCode)
        assertEquals("13608083211", draft.localNumber)
    }

    @Test
    fun splitSenderNumberForEditing_preservesDisplayModeWhenRuleWasSavedAsDisplayValue() {
        val draft = splitSenderNumberForEditing(
            rawNumber = "13608083211",
            matchMode = SenderMatchMode.DISPLAY_VALUE,
        )

        assertEquals(RuleSenderInputMode.DISPLAY_VALUE, draft.inputMode)
        assertEquals("13608083211", draft.displaySender)
        assertEquals("", draft.localNumber)
    }

    @Test
    fun splitSenderNumberForEditing_preservesInternationalModeForChinaShortCodeStyleNumber() {
        val draft = splitSenderNumberForEditing(
            rawNumber = "+861065896654201",
            matchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
        )

        assertEquals(RuleSenderInputMode.INTERNATIONAL_NUMBER, draft.inputMode)
        assertEquals("CN", draft.countryOption.regionCode)
        assertEquals("1065896654201", draft.localNumber)
    }

}
