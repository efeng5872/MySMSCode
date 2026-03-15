package com.example.mysmscode.data

import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryRepositoriesTest {

    @Test
    fun robotNames_mustBeUniqueIgnoringCase() {
        val repository = InMemoryRobotEndpointRepository()

        repository.save(
            RobotEndpoint(
                id = 0L,
                name = "Ops Feishu",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://example.com/feishu"
            )
        )

        val result = repository.save(
            RobotEndpoint(
                id = 0L,
                name = "ops feishu",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://example.com/other"
            )
        )

        assertTrue(result is RepositorySaveResult.DuplicateName)
    }

    @Test
    fun senderNumbers_mustBeUnique() {
        val repository = InMemorySenderRuleRepository()

        repository.save(
            SenderRule(
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(1L)
            )
        )

        val result = repository.save(
            SenderRule(
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("otp"),
                selectedRobotIds = listOf(2L)
            )
        )

        assertTrue(result is RepositorySaveResult.DuplicateSenderNumber)
    }

    @Test
    fun senderRule_canReferenceMultipleRobots() {
        val robotRepository = InMemoryRobotEndpointRepository()
        val ruleRepository = InMemorySenderRuleRepository()

        val feishu = robotRepository.save(
            RobotEndpoint(
                id = 0L,
                name = "Ops Feishu",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://example.com/feishu"
            )
        ) as RepositorySaveResult.Success<RobotEndpoint>

        val wecom = robotRepository.save(
            RobotEndpoint(
                id = 0L,
                name = "Ops WeCom",
                type = RobotType.WECOM,
                enabled = true,
                webhookUrl = "https://example.com/wecom"
            )
        ) as RepositorySaveResult.Success<RobotEndpoint>

        ruleRepository.save(
            SenderRule(
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(feishu.value.id, wecom.value.id)
            )
        )

        val storedRule = ruleRepository.findBySenderNumber("10690001")

        assertEquals(listOf(feishu.value.id, wecom.value.id), storedRule?.selectedRobotIds)
    }

    @Test
    fun updatingRobot_preservesIdentityAndChangesWebhook() {
        val repository = InMemoryRobotEndpointRepository()

        val created = repository.save(
            RobotEndpoint(
                id = 0L,
                name = "Ops Feishu",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://example.com/feishu"
            )
        ) as RepositorySaveResult.Success<RobotEndpoint>

        val updated = repository.update(
            created.value.copy(webhookUrl = "https://example.com/new-feishu")
        )

        assertTrue(updated is RepositorySaveResult.Success)
        updated as RepositorySaveResult.Success<RobotEndpoint>
        assertEquals(created.value.id, updated.value.id)
        assertEquals("https://example.com/new-feishu", updated.value.webhookUrl)
    }
}
