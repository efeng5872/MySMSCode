package com.example.mysmscode.domain

data class CountryPickerOption(
    val regionCode: String,
    val displayName: String,
    val callingCode: String,
)

fun buildCountryPickerOptions(
    options: List<CountryOption>,
    selectedCountry: CountryOption,
    query: String,
): List<CountryPickerOption> {
    val normalizedQuery = query.trim().lowercase()
    val filtered = options.filter { option ->
        normalizedQuery.isBlank() ||
            option.displayName.lowercase().contains(normalizedQuery) ||
            option.regionCode.lowercase().contains(normalizedQuery) ||
            option.callingCode.lowercase().contains(normalizedQuery)
    }

    return filtered
        .sortedWith(compareByDescending<CountryOption> { it.regionCode == selectedCountry.regionCode }.thenBy { it.displayName })
        .map { option ->
            CountryPickerOption(
                regionCode = option.regionCode,
                displayName = option.displayName.substringBeforeLast(' '),
                callingCode = option.callingCode,
            )
        }
}
