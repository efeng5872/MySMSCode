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
        return SimulationInjectionValidation.Invalid("模拟发送号码不能为空。")
    }

    val normalizedMessageBody = messageBody.trim()
    if (normalizedMessageBody.isEmpty()) {
        return SimulationInjectionValidation.Invalid("模拟短信内容不能为空。")
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
    initialStatusMessage = "已加入模拟短信，发送号码：$senderNumber。",
    refreshDelaysMillis = listOf(250L, 1500L),
)
