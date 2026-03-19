package com.example.mysmscode.domain

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

data class SimulationInjectionRequest(
    val senderNumber: String,
    val messageBody: String,
)

data class DebugSimulationQuickAction(
    val actionLabel: String,
    val request: SimulationInjectionRequest,
)

fun buildDebugSimulationQuickAction(isDebug: Boolean): DebugSimulationQuickAction? {
    if (!isDebug) {
        return null
    }

    return DebugSimulationQuickAction(
        actionLabel = zh(0x4E00, 0x952E, 0x6CE8, 0x5165, 0x6837, 0x4F8B),
        request = SimulationInjectionRequest(
            senderNumber = "13608083211",
            messageBody = "test verification code is 223344",
        ),
    )
}

sealed interface SimulationInjectionValidation {
    data class Valid(val request: SimulationInjectionRequest) : SimulationInjectionValidation
    data class Invalid(val reason: String) : SimulationInjectionValidation
}

fun validateSimulationInjection(
    senderNumber: String,
    messageBody: String,
): SimulationInjectionValidation {
    val normalizedSenderNumber = senderNumber.trim()
    if (normalizedSenderNumber.isEmpty()) {
        return SimulationInjectionValidation.Invalid(zh(0x6A21, 0x62DF, 0x53D1, 0x9001, 0x53F7, 0x7801, 0x4E0D, 0x80FD, 0x4E3A, 0x7A7A, 0x3002))
    }

    val normalizedMessageBody = messageBody.trim()
    if (normalizedMessageBody.isEmpty()) {
        return SimulationInjectionValidation.Invalid(zh(0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0x5185, 0x5BB9, 0x4E0D, 0x80FD, 0x4E3A, 0x7A7A, 0x3002))
    }

    return SimulationInjectionValidation.Valid(
        SimulationInjectionRequest(
            senderNumber = normalizedSenderNumber,
            messageBody = normalizedMessageBody,
        )
    )
}

data class SimulationFeedbackPlan(
    val submittedStatusMessage: String,
    val matchedRuleStatusMessage: String,
    val completedStatusMessage: String,
    val timeoutStatusMessage: String,
    val submittedStatusVisibleDelayMillis: Long,
    val refreshDelaysMillis: List<Long>,
    val navigateToHomeRecentRecords: Boolean,
    val finalRefreshBeforeTimeout: Boolean,
)

fun buildSimulationFeedbackPlan(senderNumber: String): SimulationFeedbackPlan = SimulationFeedbackPlan(
    submittedStatusMessage = zh(0x5DF2, 0x63D0, 0x4EA4, 0x6A21, 0x62DF, 0x8BF7, 0x6C42, 0xFF0C, 0x53D1, 0x9001, 0x53F7, 0x7801, 0xFF1A) + senderNumber + zh(0x3002),
    matchedRuleStatusMessage = zh(0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0x5DF2, 0x547D, 0x4E2D, 0x89C4, 0x5219, 0xFF0C, 0x6B63, 0x5728, 0x5199, 0x5165, 0x6700, 0x8FD1, 0x8BB0, 0x5F55, 0x3002),
    completedStatusMessage = zh(0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0x5DF2, 0x5199, 0x5165, 0x6700, 0x8FD1, 0x8BB0, 0x5F55, 0xFF0C, 0x8BF7, 0x67E5, 0x770B, 0x9996, 0x9875, 0x6700, 0x65B0, 0x5904, 0x7406, 0x7ED3, 0x679C, 0x3002),
    timeoutStatusMessage = zh(0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0x8BF7, 0x6C42, 0x5DF2, 0x63D0, 0x4EA4, 0xFF0C, 0x4F46, 0x6682, 0x672A, 0x770B, 0x5230, 0x6700, 0x8FD1, 0x8BB0, 0x5F55, 0xFF0C, 0x8BF7, 0x70B9, 0x51FB, 0x5237, 0x65B0, 0x540E, 0x518D, 0x67E5, 0x770B, 0x3002),
    submittedStatusVisibleDelayMillis = 250L,
    refreshDelaysMillis = listOf(250L, 500L, 1000L, 1500L, 2000L, 3000L),
    navigateToHomeRecentRecords = true,
    finalRefreshBeforeTimeout = true,
)

fun buildSimulationRuleMismatchMessage(): String =
    zh(0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0x672A, 0x547D, 0x4E2D, 0x4EFB, 0x4F55, 0x5DF2, 0x542F, 0x7528, 0x53F7, 0x7801, 0x89C4, 0x5219, 0xFF0C, 0x672A, 0x5199, 0x5165, 0x6700, 0x8FD1, 0x8BB0, 0x5F55, 0x3002)

fun findMatchingSimulationRule(
    senderNumber: String,
    rules: List<SenderRule>,
    phoneNumberNormalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(),
): SenderRule? {
    return rules.firstOrNull { rule ->
        rule.enabled && phoneNumberNormalizer.matches(rule.senderNumber, senderNumber)
    }
}

fun findInjectedSimulationRecord(
    records: List<SmsRecordPreview>,
    senderNumber: String,
    messageBody: String,
    submittedAt: Long,
): SmsRecordPreview? {
    return records.firstOrNull { record ->
        record.source == SmsSource.SIMULATION.name &&
            record.senderNumber == senderNumber &&
            record.messageBody == messageBody &&
            record.receivedAt >= submittedAt
    }
}
