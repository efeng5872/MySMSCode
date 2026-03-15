package com.example.mysmscode.domain

data class ConfigurationRuleSummary(
    val senderNumber: String,
    val enabled: Boolean,
    val keywordPreview: String,
    val robotNames: List<String>,
)

class BuildConfigurationSummaryUseCase {
    fun build(
        rules: List<SenderRule>,
        robots: List<RobotEndpoint>,
    ): List<ConfigurationRuleSummary> {
        val robotsById = robots.associateBy(RobotEndpoint::id)

        return rules.map { rule ->
            val robotNames = rule.selectedRobotIds.mapNotNull { robotId ->
                robotsById[robotId]?.let { robot ->
                    if (robot.enabled) robot.name else "${robot.name} (disabled)"
                }
            }.ifEmpty { listOf("No robot selected") }

            ConfigurationRuleSummary(
                senderNumber = rule.senderNumber,
                enabled = rule.enabled,
                keywordPreview = rule.keywords.joinToString(separator = ", "),
                robotNames = robotNames,
            )
        }
    }
}