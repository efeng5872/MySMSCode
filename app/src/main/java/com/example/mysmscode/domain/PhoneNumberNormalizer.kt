package com.example.mysmscode.domain

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

class PhoneNumberNormalizer(
    private val defaultRegion: String = "CN",
    private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
) {

    fun normalize(rawNumber: String): String {
        val compact = compact(rawNumber)
        if (compact.isEmpty()) {
            return compact
        }

        return try {
            val parsedNumber = phoneNumberUtil.parse(compact, defaultRegion)
            if (phoneNumberUtil.isValidNumber(parsedNumber)) {
                phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                compact
            }
        } catch (_: NumberParseException) {
            compact
        }
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
    val countryOption: CountryOption,
    val localNumber: String,
)

private val countryOptions = listOf(
    CountryOption(regionCode = "CN", displayName = "中国 +86", callingCode = "+86"),
    CountryOption(regionCode = "HK", displayName = "中国香港 +852", callingCode = "+852"),
    CountryOption(regionCode = "MO", displayName = "中国澳门 +853", callingCode = "+853"),
    CountryOption(regionCode = "TW", displayName = "中国台湾 +886", callingCode = "+886"),
    CountryOption(regionCode = "SG", displayName = "新加坡 +65", callingCode = "+65"),
    CountryOption(regionCode = "JP", displayName = "日本 +81", callingCode = "+81"),
    CountryOption(regionCode = "KR", displayName = "韩国 +82", callingCode = "+82"),
    CountryOption(regionCode = "US", displayName = "美国 +1", callingCode = "+1"),
    CountryOption(regionCode = "GB", displayName = "英国 +44", callingCode = "+44"),
)

fun supportedCountryOptions(): List<CountryOption> = countryOptions

fun defaultCountryOption(): CountryOption = countryOptions.first()

fun findCountryOption(regionCode: String): CountryOption =
    countryOptions.firstOrNull { it.regionCode == regionCode } ?: defaultCountryOption()

fun splitSenderNumberForEditing(
    rawNumber: String,
    phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance(),
): RuleNumberDraft {
    val trimmed = rawNumber.trim()
    if (trimmed.isEmpty()) {
        return RuleNumberDraft(
            countryOption = defaultCountryOption(),
            localNumber = "",
        )
    }

    return try {
        val parsedNumber = phoneNumberUtil.parse(trimmed, defaultCountryOption().regionCode)
        if (phoneNumberUtil.isValidNumber(parsedNumber)) {
            val regionCode = phoneNumberUtil.getRegionCodeForNumber(parsedNumber).orEmpty()
            RuleNumberDraft(
                countryOption = findCountryOption(regionCode),
                localNumber = phoneNumberUtil.getNationalSignificantNumber(parsedNumber),
            )
        } else {
            RuleNumberDraft(
                countryOption = defaultCountryOption(),
                localNumber = trimmed.removePrefix(defaultCountryOption().callingCode),
            )
        }
    } catch (_: NumberParseException) {
        RuleNumberDraft(
            countryOption = defaultCountryOption(),
            localNumber = trimmed,
        )
    }
}

fun buildRuleSenderNumber(
    countryOption: CountryOption,
    localNumber: String,
    normalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(defaultRegion = defaultCountryOption().regionCode),
): String {
    return normalizer.normalize("${countryOption.callingCode}${localNumber.trim()}")
}
