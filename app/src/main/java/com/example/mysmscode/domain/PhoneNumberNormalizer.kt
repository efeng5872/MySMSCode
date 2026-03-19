package com.example.mysmscode.domain

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

enum class RuleSenderInputMode {
    DISPLAY_VALUE,
    INTERNATIONAL_NUMBER,
}

class PhoneNumberNormalizer(
    private val defaultRegion: String = "CN",
    private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {

    fun normalize(rawNumber: String): String {
        val compact = compact(rawNumber)
        if (compact.isEmpty()) {
            return compact
        }

        return parseToE164OrNull(compact) ?: compact
    }

    fun matches(
        configuredSender: String,
        incomingSender: String,
    ): Boolean {
        val configuredDisplay = configuredSender.trim()
        val incomingDisplay = incomingSender.trim()
        if (configuredDisplay.isNotEmpty() && configuredDisplay == incomingDisplay) {
            return true
        }

        val configuredVariants = comparableVariants(configuredSender)
        val incomingVariants = comparableVariants(incomingSender)
        return configuredVariants.any(incomingVariants::contains)
    }

    fun comparableVariants(rawNumber: String): Set<String> {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) {
            return emptySet()
        }

        val variants = linkedSetOf<String>()
        variants += trimmed

        val compact = compact(trimmed)
        if (compact.isNotEmpty()) {
            variants += compact
        }

        parseToNumberOrNull(compact)?.let { parsedNumber ->
            if (phoneNumberUtil.isValidNumber(parsedNumber)) {
                variants += phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
                variants += "${parsedNumber.countryCode}${parsedNumber.nationalNumber}"
                variants += phoneNumberUtil.getNationalSignificantNumber(parsedNumber)
            }
        }

        return variants
    }

    private fun parseToE164OrNull(compact: String): String? {
        val parsedNumber = parseToNumberOrNull(compact) ?: return null
        if (!phoneNumberUtil.isValidNumber(parsedNumber)) {
            return null
        }
        return phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
    }

    private fun parseToNumberOrNull(compact: String) = try {
        phoneNumberUtil.parse(compact, defaultRegion)
    } catch (_: NumberParseException) {
        null
    }

    private fun compact(rawNumber: String): String {
        val trimmed = rawNumber.trim()
        return buildString(trimmed.length) {
            trimmed.forEachIndexed { index, char ->
                if (char.isDigit() || (char == '+' && index == 0)) {
                    append(char)
                }
            }
        }
    }
}

data class CountryOption(
    val regionCode: String,
    val displayName: String,
    val callingCode: String,
)

data class RuleNumberDraft(
    val inputMode: RuleSenderInputMode,
    val countryOption: CountryOption,
    val localNumber: String,
    val displaySender: String,
)

private val countryOptions: List<CountryOption> by lazy {
    val displayLocale = Locale.SIMPLIFIED_CHINESE
    PhoneNumberUtil.getInstance()
        .supportedRegions
        .map { regionCode ->
            val locale = Locale.Builder().setRegion(regionCode).build()
            val localizedName = locale.getDisplayCountry(displayLocale).trim()
            val englishName = locale.getDisplayCountry(Locale.ENGLISH).trim()
            val displayName = when {
                localizedName.isNotBlank() -> localizedName
                englishName.isNotBlank() -> englishName
                else -> regionCode
            }
            CountryOption(
                regionCode = regionCode,
                displayName = displayName,
                callingCode = "+${PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode)}",
            )
        }
        .distinctBy(CountryOption::regionCode)
        .sortedWith(compareBy<CountryOption> { if (it.regionCode == "CN") 0 else 1 }.thenBy { it.displayName })
}

fun supportedCountryOptions(): List<CountryOption> = countryOptions

fun preloadCountryOptions() {
    countryOptions.size
}

fun defaultCountryOption(): CountryOption = countryOptions.first()

fun findCountryOption(regionCode: String): CountryOption =
    countryOptions.firstOrNull { it.regionCode == regionCode } ?: defaultCountryOption()

fun defaultRuleNumberDraft(): RuleNumberDraft = RuleNumberDraft(
    inputMode = RuleSenderInputMode.DISPLAY_VALUE,
    countryOption = defaultCountryOption(),
    localNumber = "",
    displaySender = "",
)

fun splitSenderNumberForEditing(
    rawNumber: String,
    phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
): RuleNumberDraft {
    val trimmed = rawNumber.trim()
    if (trimmed.isEmpty()) {
        return defaultRuleNumberDraft()
    }

    return try {
        val parsedNumber = phoneNumberUtil.parse(trimmed, defaultCountryOption().regionCode)
        if (phoneNumberUtil.isValidNumber(parsedNumber)) {
            val regionCode = phoneNumberUtil.getRegionCodeForNumber(parsedNumber).orEmpty()
            RuleNumberDraft(
                inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                countryOption = findCountryOption(regionCode),
                localNumber = phoneNumberUtil.getNationalSignificantNumber(parsedNumber),
                displaySender = trimmed,
            )
        } else {
            RuleNumberDraft(
                inputMode = RuleSenderInputMode.DISPLAY_VALUE,
                countryOption = defaultCountryOption(),
                localNumber = "",
                displaySender = trimmed,
            )
        }
    } catch (_: NumberParseException) {
        RuleNumberDraft(
            inputMode = RuleSenderInputMode.DISPLAY_VALUE,
            countryOption = defaultCountryOption(),
            localNumber = "",
            displaySender = trimmed,
        )
    }
}

fun buildRuleSenderNumber(
    inputMode: RuleSenderInputMode,
    countryOption: CountryOption,
    localNumber: String,
    displaySender: String,
    normalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(defaultRegion = defaultCountryOption().regionCode),
): String {
    return when (inputMode) {
        RuleSenderInputMode.DISPLAY_VALUE -> displaySender.trim()
        RuleSenderInputMode.INTERNATIONAL_NUMBER -> normalizer.normalize("${countryOption.callingCode}${localNumber.trim()}")
    }
}
