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
