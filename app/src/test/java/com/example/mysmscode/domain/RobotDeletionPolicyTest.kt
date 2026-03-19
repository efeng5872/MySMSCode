package com.example.mysmscode.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotDeletionPolicyTest {

    @Test
    fun canDeleteRobot_returnsFalseWhenRuleStillReferencesRobot() {
        val rules = listOf(
            SenderRule(
                id = 1L,
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(11L),
            )
        )

        assertFalse(canDeleteRobot(robotId = 11L, rules = rules))
    }

    @Test
    fun canDeleteRobot_returnsTrueWhenRobotIsNotReferenced() {
        val rules = listOf(
            SenderRule(
                id = 1L,
                senderNumber = "10690001",
                enabled = true,
                keywords = listOf("code"),
                selectedRobotIds = listOf(12L),
            )
        )

        assertTrue(canDeleteRobot(robotId = 11L, rules = rules))
    }
}
