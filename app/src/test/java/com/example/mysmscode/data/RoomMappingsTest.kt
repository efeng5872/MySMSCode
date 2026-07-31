package com.example.mysmscode.data

import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomMappingsTest {

    @Test
    fun robotEndpointEntity_roundTripsDomainModel() {
        val domain = RobotEndpoint(
            id = 7L,
            name = "Ops Feishu",
            type = RobotType.FEISHU,
            enabled = true,
            webhookUrl = "https://example.com/feishu",
            createdAt = 111L,
            updatedAt = 222L,
        )

        val entity = RobotEndpointEntity.fromDomain(domain)
        val restored = entity.toDomain()

        assertEquals(domain, restored)
    }

    @Test
    fun senderRuleEntity_roundTripsKeywords() {
        val domain = SenderRule(
            id = 3L,
            name = "银行短信",
            senderNumber = "Bank-01",
            senderMatchMode = SenderMatchMode.DISPLAY_VALUE,
            enabled = true,
            keywords = listOf("code", "OTP", "dynamic password"),
            selectedRobotIds = listOf(11L, 12L),
            createdAt = 333L,
            updatedAt = 444L,
        )

        val entity = SenderRuleEntity.fromDomain(domain)
        val restored = entity.toDomain(selectedRobotIds = domain.selectedRobotIds)

        assertEquals(domain, restored)
    }

    @Test
    fun senderRuleWithRobots_toDomainPreservesRobotSelectionOrder() {
        val aggregate = SenderRuleWithRobots(
            rule = SenderRuleEntity(
                id = 5L,
                name = "登录验证码",
                senderNumber = "10690001",
                senderMatchMode = SenderMatchMode.DISPLAY_VALUE,
                enabled = true,
                keywordBlob = "code${KeywordListCodec.SEPARATOR}otp",
                createdAt = 10L,
                updatedAt = 20L,
            ),
            selectedRobotIds = listOf(
                SenderRuleRobotCrossRef(
                    senderRuleId = 5L,
                    robotEndpointId = 100L,
                    sortOrder = 0,
                ),
                SenderRuleRobotCrossRef(
                    senderRuleId = 5L,
                    robotEndpointId = 200L,
                    sortOrder = 1,
                )
            )
        )

        val domain = aggregate.toDomain()

        assertEquals(listOf(100L, 200L), domain.selectedRobotIds)
        assertEquals("登录验证码", domain.name)
        assertEquals(listOf("code", "otp"), domain.keywords)
    }

    @Test
    fun retryPolicyEntity_roundTripsDomainModel() {
        val domain = RetryPolicyConfig(
            firstRetryDelaySeconds = 10,
            secondRetryDelaySeconds = 30,
            thirdRetryDelaySeconds = 60,
        )

        val entity = RetryPolicyConfigEntity.fromDomain(domain)
        val restored = entity.toDomain()

        assertEquals(domain, restored)
    }
}
