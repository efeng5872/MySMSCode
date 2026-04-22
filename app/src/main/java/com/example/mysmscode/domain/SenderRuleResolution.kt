package com.example.mysmscode.domain

private data class RankedSenderRule(
    val rule: SenderRule,
    val matchStrength: Int,
)

fun prioritizeSenderMatchedRules(
    senderNumber: String,
    rules: List<SenderRule>,
    phoneNumberNormalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(),
): List<SenderRule> {
    return rules.mapNotNull { rule ->
        phoneNumberNormalizer.matchStrength(
            configuredSender = rule.senderNumber,
            incomingSender = senderNumber,
            matchMode = rule.senderMatchMode,
        )?.let { strength ->
            RankedSenderRule(
                rule = rule,
                matchStrength = strength,
            )
        }
    }.sortedWith(
        compareByDescending<RankedSenderRule> { it.matchStrength }
            .thenByDescending { senderMatchModePriority(it.rule.senderMatchMode) }
            .thenByDescending { it.rule.updatedAt }
            .thenByDescending { it.rule.id }
    ).map(RankedSenderRule::rule)
}

fun SenderRule.hasLogicalSenderConflictWith(
    other: SenderRule,
    phoneNumberNormalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(),
): Boolean {
    return phoneNumberNormalizer.hasSenderConflict(
        firstConfiguredSender = senderNumber,
        firstMatchMode = senderMatchMode,
        secondConfiguredSender = other.senderNumber,
        secondMatchMode = other.senderMatchMode,
    )
}

private fun senderMatchModePriority(matchMode: SenderMatchMode): Int = when (matchMode) {
    SenderMatchMode.DISPLAY_VALUE -> 3
    SenderMatchMode.INTERNATIONAL_NUMBER -> 2
    SenderMatchMode.LEGACY_COMPAT -> 1
}
