package com.example.mysmscode.data

import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableRepositoriesTest {

    @Test
    fun senderRule_canBeUpdatedByIdWithoutCreatingDuplicate() {
        val repository = InMemorySenderRuleRepository()
        repository.save(
            SenderRule(
                id = 1L,
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(1L),
            )
        )

        val result = repository.update(
            SenderRule(
                id = 1L,
                senderNumber = "10690001",
                enabled = false,
                keywords = listOf("otp"),
                selectedRobotIds = listOf(2L),
            )
        )

        assertTrue(result is RepositorySaveResult.Success<*>)
        val stored = repository.findById(1L)
        assertEquals(false, stored?.enabled)
        assertEquals(listOf("otp"), stored?.keywords)
        assertEquals(listOf(2L), stored?.selectedRobotIds)
    }

    @Test
    fun senderRule_canBeDeletedById() {
        val repository = InMemorySenderRuleRepository()
        repository.save(
            SenderRule(
                id = 1L,
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(1L),
            )
        )

        repository.deleteById(1L)

        assertNull(repository.findById(1L))
    }

    @Test
    fun robot_canBeDeletedById() {
        val repository = InMemoryRobotEndpointRepository()
        val created = repository.save(
            RobotEndpoint(
                id = 0L,
                name = "Ops Feishu",
                type = RobotType.FEISHU,
                enabled = true,
                webhookUrl = "https://example.com/feishu",
            )
        ) as RepositorySaveResult.Success<RobotEndpoint>

        repository.deleteById(created.value.id)

        assertNull(repository.findById(created.value.id))
    }
}

