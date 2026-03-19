package com.example.mysmscode.domain

fun canDeleteRobot(robotId: Long, rules: List<SenderRule>): Boolean {
    return rules.none { rule -> rule.selectedRobotIds.contains(robotId) }
}
