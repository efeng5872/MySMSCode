package com.example.mysmscode.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessIncomingSmsUseCaseTest {

    private val useCase = ProcessIncomingSmsUseCase()

    @Test
    fun unconfiguredSender_isIgnored() {
        val result = useCase.process(
            senderNumber = "10690001",
            messageBody = "验证码 1234",
            rules = emptyList(),
            robots = emptyList(),
        )

        assertTrue(result is SmsProcessingResult.Ignored)
    }

    @Test
    fun disabledSenderRule_isIgnored() {
        val result = useCase.process(
            senderNumber = "10690001",
            messageBody = "验证码 1234",
            rules = listOf(
                SenderRule(
                    senderNumber = "10690001",
                    enabled = false,
                    keywords = listOf("验证码"),
                    selectedRobotIds = listOf(1L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 1L,
                    name = "Feishu Main",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://example.com/feishu",
                )
            ),
        )

        assertTrue(result is SmsProcessingResult.Ignored)
    }

    @Test
    fun configuredSenderWithoutKeywordHit_isMarkedNotMatched() {
        val result = useCase.process(
            senderNumber = "10690001",
            messageBody = "余额通知",
            rules = listOf(
                SenderRule(
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("验证码", "动态码"),
                    selectedRobotIds = listOf(1L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 1L,
                    name = "Feishu Main",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://example.com/feishu",
                )
            ),
        )

        assertTrue(result is SmsProcessingResult.NotMatched)
        result as SmsProcessingResult.NotMatched
        assertEquals(SmsProcessingStatus.NOT_MATCHED, result.record.status)
    }

    @Test
    fun keywordMatching_isCaseInsensitiveAndUsesOrSemantics() {
        val result = useCase.process(
            senderNumber = "Bank-01",
            messageBody = "Your CoDe is 9988",
            rules = listOf(
                SenderRule(
                    senderNumber = "Bank-01",
                    enabled = true,
                    keywords = listOf("verify", "code"),
                    selectedRobotIds = listOf(1L, 2L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 1L,
                    name = "Feishu Main",
                    type = RobotType.FEISHU,
                    enabled = true,
                    webhookUrl = "https://example.com/feishu",
                ),
                RobotEndpoint(
                    id = 2L,
                    name = "WeCom Main",
                    type = RobotType.WECOM,
                    enabled = true,
                    webhookUrl = "https://example.com/wecom",
                )
            ),
        )

        assertTrue(result is SmsProcessingResult.PendingForward)
        result as SmsProcessingResult.PendingForward
        assertEquals("code", result.record.matchedKeyword)
        assertEquals(2, result.attempts.size)
    }

    @Test
    fun matchedRuleWithoutEnabledRobot_isConfigurationFailure() {
        val result = useCase.process(
            senderNumber = "10690001",
            messageBody = "验证码 1234",
            rules = listOf(
                SenderRule(
                    senderNumber = "10690001",
                    enabled = true,
                    keywords = listOf("验证码"),
                    selectedRobotIds = listOf(1L),
                )
            ),
            robots = listOf(
                RobotEndpoint(
                    id = 1L,
                    name = "Feishu Main",
                    type = RobotType.FEISHU,
                    enabled = false,
                    webhookUrl = "https://example.com/feishu",
                )
            ),
        )

        assertTrue(result is SmsProcessingResult.ConfigurationFailed)
        result as SmsProcessingResult.ConfigurationFailed
        assertEquals(SmsProcessingStatus.CONFIGURATION_FAILED, result.record.status)
        assertFalse(result.record.failureReason.isNullOrBlank())
    }
}
