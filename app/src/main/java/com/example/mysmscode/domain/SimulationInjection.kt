package com.example.mysmscode.domain

private fun zh(vararg codes: Int): String = codes.map(Int::toChar).joinToString("")

data class SimulationInjectionRequest(
    val senderNumber: String,
    val messageBody: String,
)

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
    val initialStatusMessage: String,
    val refreshDelaysMillis: List<Long>,
)

fun buildSimulationFeedbackPlan(senderNumber: String): SimulationFeedbackPlan = SimulationFeedbackPlan(
    initialStatusMessage = zh(0x5DF2, 0x52A0, 0x5165, 0x6A21, 0x62DF, 0x77ED, 0x4FE1, 0xFF0C, 0x53D1, 0x9001, 0x53F7, 0x7801, 0xFF1A) + senderNumber + zh(0x3002),
    refreshDelaysMillis = listOf(250L, 1500L),
)
