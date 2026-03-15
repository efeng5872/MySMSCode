package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildConfigurationSummaryUseCaseTest {

    private val useCase = BuildConfigurationSummaryUseCase()

    @Test
    fun buildsRuleSummaryWithLinkedRobotNames() {
        val summaries = useCase.build(
            rules = listOf(
                SenderRule(
                    id = 1L,
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("code", "otp"),
                    selectedRobotIds = listOf(20L, 10L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 10L,
                    name = "Ops Feishu",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://example.com/feishu",
                ),
                RobotEndpoint(
                    id = 20L,
                    name = "Ops WeCom",
                    type = RobotType.WECOM,
                    enabled = true,
                    webhookUrl = "https://example.com/wecom",
                )
            )
        )

        assertEquals(1, summaries.size)
        assertEquals(listOf("Ops WeCom", "Ops Feishu"), summaries.first().robotNames)
        assertEquals("code, otp", summaries.first().keywordPreview)
    }

    @Test
    fun usesPlaceholderWhenRuleHasNoRobotSelection() {
        val summaries = useCase.build(
            rules = listOf(
                SenderRule(
                    id = 1L,
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = emptyList(),
                )
            ),
            robots = emptyList(),
        )

        assertEquals(listOf("No robot selected"), summaries.first().robotNames)
    }

    @Test
    fun marksDisabledRobotsInSummary() {
        val summaries = useCase.build(
            rules = listOf(
                SenderRule(
                    id = 1L,
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("code"),
                    selectedRobotIds = listOf(10L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 10L,
                    name = "Ops Feishu",
                    type = RobotType.FEISHU,
                    enabled = false,
                    webhookUrl = "https://example.com/feishu",
                )
            )
        )

        assertEquals(listOf("Ops Feishu (disabled)"), summaries.first().robotNames)
    }
}