package com.example.mysmscode.domain

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
        return SimulationInjectionValidation.Invalid("Sender number is required for simulation.")
    }

    val normalizedMessageBody = messageBody.trim()
    if (normalizedMessageBody.isEmpty()) {
        return SimulationInjectionValidation.Invalid("Message body is required for simulation.")
    }

    return SimulationInjectionValidation.Valid(
        SimulationInjectionRequest(
            senderNumber = normalizedSenderNumber,
            messageBody = normalizedMessageBody,
        )
    )
}