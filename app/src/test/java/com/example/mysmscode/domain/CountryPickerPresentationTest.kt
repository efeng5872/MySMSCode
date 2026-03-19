package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryPickerPresentationTest {

    private val options = supportedCountryOptions()

    @Test
    fun buildCountryPickerOptions_putsSelectedOptionFirst() {
        val selected = findCountryOption("US")

        val result = buildCountryPickerOptions(
            options = options,
            selectedCountry = selected,
            query = "",
        )

        assertEquals("US", result.first().regionCode)
    }

    @Test
    fun buildCountryPickerOptions_filtersByChineseName() {
        val result = buildCountryPickerOptions(
            options = options,
            selectedCountry = findCountryOption("CN"),
            query = "美国",
        )

        assertEquals(1, result.size)
        assertEquals("US", result.first().regionCode)
    }

    @Test
    fun buildCountryPickerOptions_filtersByCallingCode() {
        val result = buildCountryPickerOptions(
            options = options,
            selectedCountry = findCountryOption("CN"),
            query = "+44",
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.regionCode == "GB" })
    }

    @Test
    fun buildCountryPickerOptions_filtersByRegionCode() {
        val result = buildCountryPickerOptions(
            options = options,
            selectedCountry = findCountryOption("CN"),
            query = "jp",
        )

        assertEquals(1, result.size)
        assertEquals("JP", result.first().regionCode)
    }

    @Test
    fun buildCountryPickerOptions_keepsSelectedFirstWithinFilteredResults() {
        val result = buildCountryPickerOptions(
            options = options,
            selectedCountry = findCountryOption("HK"),
            query = "中国",
        )

        assertTrue(result.isNotEmpty())
        assertEquals("HK", result.first().regionCode)
    }

    @Test
    fun supportedCountryOptions_containsMoreThanSeedWhitelist() {
        val result = supportedCountryOptions()

        assertTrue(result.size > 9)
    }


    @Test
    fun supportedCountryOptions_containsFranceAfterFallbackNaming() {
        val result = supportedCountryOptions()

        assertTrue(result.any { it.regionCode == "FR" })
    }

}
