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

    fun matchesLegacy(
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

    fun matches(
        configuredSender: String,
        incomingSender: String,
        matchMode: SenderMatchMode,
    ): Boolean {
        return when (matchMode) {
            SenderMatchMode.DISPLAY_VALUE -> matchesDisplayValue(configuredSender, incomingSender)
            SenderMatchMode.INTERNATIONAL_NUMBER -> matchesInternationalNumber(configuredSender, incomingSender)
            SenderMatchMode.LEGACY_COMPAT -> matchesLegacy(configuredSender, incomingSender)
        }
    }

    fun matchStrength(
        configuredSender: String,
        incomingSender: String,
        matchMode: SenderMatchMode,
    ): Int? {
        return when (matchMode) {
            SenderMatchMode.DISPLAY_VALUE -> displayMatchStrength(configuredSender, incomingSender)
            SenderMatchMode.INTERNATIONAL_NUMBER -> internationalMatchStrength(configuredSender, incomingSender)
            SenderMatchMode.LEGACY_COMPAT -> listOfNotNull(
                displayMatchStrength(configuredSender, incomingSender),
                internationalMatchStrength(configuredSender, incomingSender),
            ).maxOrNull()
        }
    }

    fun hasSenderConflict(
        firstConfiguredSender: String,
        firstMatchMode: SenderMatchMode,
        secondConfiguredSender: String,
        secondMatchMode: SenderMatchMode,
    ): Boolean {
        val firstVariants = conflictComparableVariants(firstConfiguredSender, firstMatchMode)
        val secondVariants = conflictComparableVariants(secondConfiguredSender, secondMatchMode)
        return firstVariants.isNotEmpty() && firstVariants.any(secondVariants::contains)
    }

    private fun matchesDisplayValue(
        configuredSender: String,
        incomingSender: String,
    ): Boolean {
        val configuredDisplay = configuredSender.trim()
        val incomingDisplay = incomingSender.trim()
        if (configuredDisplay.isEmpty() || incomingDisplay.isEmpty()) {
            return false
        }
        if (configuredDisplay == incomingDisplay) {
            return true
        }

        val configuredCompact = compact(configuredDisplay)
        val incomingCompact = compact(incomingDisplay)
        return configuredCompact.isNotEmpty() &&
            incomingCompact.isNotEmpty() &&
            configuredCompact == incomingCompact
    }

    private fun displayMatchStrength(
        configuredSender: String,
        incomingSender: String,
    ): Int? {
        val configuredDisplay = configuredSender.trim()
        val incomingDisplay = incomingSender.trim()
        if (configuredDisplay.isEmpty() || incomingDisplay.isEmpty()) {
            return null
        }
        if (configuredDisplay == incomingDisplay) {
            return 400
        }

        val configuredCompact = compact(configuredDisplay)
        val incomingCompact = compact(incomingDisplay)
        if (configuredCompact.isNotEmpty() && configuredCompact == incomingCompact) {
            return 300
        }
        return null
    }

    private fun matchesInternationalNumber(
        configuredSender: String,
        incomingSender: String,
    ): Boolean {
        val configuredVariants = comparableVariants(configuredSender)
        val incomingVariants = comparableVariants(incomingSender)
        return configuredVariants.isNotEmpty() && configuredVariants.any(incomingVariants::contains)
    }

    private fun internationalMatchStrength(
        configuredSender: String,
        incomingSender: String,
    ): Int? {
        val configuredTrimmed = configuredSender.trim()
        val incomingTrimmed = incomingSender.trim()
        if (configuredTrimmed.isEmpty() || incomingTrimmed.isEmpty()) {
            return null
        }

        val configuredCompact = compact(configuredTrimmed)
        val incomingCompact = compact(incomingTrimmed)
        val configuredVariants = comparableVariants(configuredTrimmed)
        val incomingVariants = comparableVariants(incomingTrimmed)
        if (configuredVariants.isEmpty() || configuredVariants.none(incomingVariants::contains)) {
            return null
        }

        val configuredE164 = parseToE164OrNull(configuredCompact)
        val incomingE164 = parseToE164OrNull(incomingCompact)
        if (configuredE164 != null && configuredE164 == incomingE164) {
            return 400
        }
        if (configuredTrimmed == incomingTrimmed) {
            return 350
        }
        if (configuredCompact.isNotEmpty() && configuredCompact == incomingCompact) {
            return 300
        }

        val configuredNational = nationalSignificantNumber(configuredCompact)
        val incomingNational = nationalSignificantNumber(incomingCompact)
        if (configuredNational != null && configuredNational == incomingNational) {
            return 200
        }

        return 100
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

    private fun conflictComparableVariants(
        rawNumber: String,
        matchMode: SenderMatchMode,
    ): Set<String> {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) {
            return emptySet()
        }

        return when (matchMode) {
            SenderMatchMode.DISPLAY_VALUE -> buildSet {
                add(trimmed)
                compact(trimmed).takeIf { it.isNotEmpty() }?.let(::add)
            }

            SenderMatchMode.INTERNATIONAL_NUMBER,
            SenderMatchMode.LEGACY_COMPAT,
            -> comparableVariants(trimmed)
        }
    }

    private fun parseToE164OrNull(compact: String): String? {
        val parsedNumber = parseToNumberOrNull(compact) ?: return null
        if (!phoneNumberUtil.isValidNumber(parsedNumber)) {
            return null
        }
        return phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
    }

    private fun nationalSignificantNumber(compact: String): String? {
        val parsedNumber = parseToNumberOrNull(compact) ?: return null
        if (!phoneNumberUtil.isValidNumber(parsedNumber)) {
            return null
        }
        return phoneNumberUtil.getNationalSignificantNumber(parsedNumber)
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
    matchMode: SenderMatchMode = SenderMatchMode.LEGACY_COMPAT,
    phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
): RuleNumberDraft {
    val trimmed = rawNumber.trim()
    if (trimmed.isEmpty()) {
        return defaultRuleNumberDraft()
    }

    if (matchMode == SenderMatchMode.DISPLAY_VALUE) {
        return RuleNumberDraft(
            inputMode = RuleSenderInputMode.DISPLAY_VALUE,
            countryOption = defaultCountryOption(),
            localNumber = "",
            displaySender = trimmed,
        )
    }

    if (matchMode == SenderMatchMode.INTERNATIONAL_NUMBER) {
        return parseInternationalDraftOrFallback(trimmed, phoneNumberUtil)
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

private fun parseInternationalDraftOrFallback(
    trimmed: String,
    phoneNumberUtil: PhoneNumberUtil,
): RuleNumberDraft {
    try {
        val parsedNumber = phoneNumberUtil.parse(trimmed, defaultCountryOption().regionCode)
        if (phoneNumberUtil.isValidNumber(parsedNumber)) {
            val regionCode = phoneNumberUtil.getRegionCodeForNumber(parsedNumber).orEmpty()
            return RuleNumberDraft(
                inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                countryOption = findCountryOption(regionCode),
                localNumber = phoneNumberUtil.getNationalSignificantNumber(parsedNumber),
                displaySender = trimmed,
            )
        }
    } catch (_: NumberParseException) {
        // 已明确为国际号码模式时，解析失败也不回退为显示值模式。
    }

    val compact = trimmed.filterIndexed { index, char ->
        char.isDigit() || (char == '+' && index == 0)
    }
    val matchedCountry = supportedCountryOptions()
        .sortedByDescending { it.callingCode.length }
        .firstOrNull { option ->
            compact.startsWith(option.callingCode) ||
                compact.startsWith(option.callingCode.removePrefix("+"))
        }
        ?: defaultCountryOption()
    val localNumber = compact
        .removePrefix(matchedCountry.callingCode)
        .removePrefix(matchedCountry.callingCode.removePrefix("+"))
        .ifBlank { compact.removePrefix("+") }

    return RuleNumberDraft(
        inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
        countryOption = matchedCountry,
        localNumber = localNumber,
        displaySender = trimmed,
    )
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

fun transformRuleNumberDraft(
    currentInputMode: RuleSenderInputMode,
    targetInputMode: RuleSenderInputMode,
    currentCountryOption: CountryOption,
    currentLocalNumber: String,
    currentDisplaySender: String,
    phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
): RuleNumberDraft {
    if (currentInputMode == targetInputMode) {
        return RuleNumberDraft(
            inputMode = currentInputMode,
            countryOption = currentCountryOption,
            localNumber = currentLocalNumber,
            displaySender = currentDisplaySender,
        )
    }

    return when (targetInputMode) {
        RuleSenderInputMode.DISPLAY_VALUE -> RuleNumberDraft(
            inputMode = RuleSenderInputMode.DISPLAY_VALUE,
            countryOption = currentCountryOption,
            localNumber = "",
            displaySender = when (currentInputMode) {
                RuleSenderInputMode.DISPLAY_VALUE -> currentDisplaySender.trim()
                RuleSenderInputMode.INTERNATIONAL_NUMBER -> buildRuleSenderNumber(
                    inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                    countryOption = currentCountryOption,
                    localNumber = currentLocalNumber,
                    displaySender = currentDisplaySender,
                )
            },
        )

        RuleSenderInputMode.INTERNATIONAL_NUMBER -> {
            val sourceNumber = when (currentInputMode) {
                RuleSenderInputMode.DISPLAY_VALUE -> currentDisplaySender.trim()
                RuleSenderInputMode.INTERNATIONAL_NUMBER -> buildRuleSenderNumber(
                    inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                    countryOption = currentCountryOption,
                    localNumber = currentLocalNumber,
                    displaySender = currentDisplaySender,
                )
            }
            if (sourceNumber.isBlank()) {
                RuleNumberDraft(
                    inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                    countryOption = currentCountryOption,
                    localNumber = "",
                    displaySender = "",
                )
            } else {
                if (currentInputMode == RuleSenderInputMode.DISPLAY_VALUE && !sourceNumber.startsWith("+") && !sourceNumber.startsWith("00")) {
                    RuleNumberDraft(
                        inputMode = RuleSenderInputMode.INTERNATIONAL_NUMBER,
                        countryOption = currentCountryOption,
                        localNumber = sourceNumber
                            .trim()
                            .removePrefix(currentCountryOption.callingCode)
                            .removePrefix(currentCountryOption.callingCode.removePrefix("+"))
                            .ifBlank { sourceNumber.trim() },
                        displaySender = sourceNumber,
                    )
                } else {
                    splitSenderNumberForEditing(
                        rawNumber = sourceNumber,
                        matchMode = SenderMatchMode.INTERNATIONAL_NUMBER,
                        phoneNumberUtil = phoneNumberUtil,
                    )
                }
            }
        }
    }
}
