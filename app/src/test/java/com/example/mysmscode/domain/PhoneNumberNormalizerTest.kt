package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
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
}
