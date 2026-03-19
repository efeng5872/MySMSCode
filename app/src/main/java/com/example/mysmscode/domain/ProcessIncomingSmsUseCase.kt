package com.example.mysmscode.domain

import java.util.Locale

class ProcessIncomingSmsUseCase(
    private val phoneNumberNormalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(),
) {

    fun process(
        senderNumber: String,
        messageBody: String,
        rules: List<SenderRule>,
        robots: List<RobotEndpoint>,
    ): SmsProcessingResult {
        val rule = rules.firstOrNull { phoneNumberNormalizer.matches(it.senderNumber, senderNumber) }
            ?: return SmsProcessingResult.Ignored
        if (!rule.enabled) {
            return SmsProcessingResult.Ignored
        }

        val matchedKeyword = findMatchedKeyword(messageBody, rule.keywords)
            ?: return SmsProcessingResult.NotMatched(
                record = SmsProcessingRecord(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    status = SmsProcessingStatus.NOT_MATCHED,
                )
            )

        val selectedEnabledRobots = robots.filter { robot ->
            robot.enabled && rule.selectedRobotIds.contains(robot.id)
        }

        if (selectedEnabledRobots.isEmpty()) {
            return SmsProcessingResult.ConfigurationFailed(
                record = SmsProcessingRecord(
                    senderNumber = senderNumber,
                    messageBody = messageBody,
                    status = SmsProcessingStatus.CONFIGURATION_FAILED,
                    matchedKeyword = matchedKeyword,
                    failureReason = "No enabled robot endpoint selected for matched rule.",
                )
            )
        }

        return SmsProcessingResult.PendingForward(
            record = SmsProcessingRecord(
                senderNumber = senderNumber,
                messageBody = messageBody,
                status = SmsProcessingStatus.PENDING_FORWARD,
                matchedKeyword = matchedKeyword,
            ),
            attempts = selectedEnabledRobots.map { robot ->
                ForwardPlan(
                    robotId = robot.id,
                    robotType = robot.type,
                )
            }
        )
    }

    private fun findMatchedKeyword(
        messageBody: String,
        keywords: List<String>,
    ): String? {
        val normalizedBody = messageBody.lowercase(Locale.ROOT)
        return keywords.firstOrNull { keyword ->
            normalizedBody.contains(keyword.lowercase(Locale.ROOT))
        }
    }

}
