package com.example.mysmscode.data

import com.example.mysmscode.domain.RobotEndpoint
import java.util.Locale

class InMemoryRobotEndpointRepository {

    private val robots = linkedMapOf<Long, RobotEndpoint>()
    private var nextId = 1L

    fun save(robot: RobotEndpoint): RepositorySaveResult<RobotEndpoint> {
        if (hasDuplicateName(robot)) {
            return RepositorySaveResult.DuplicateName
        }

        val storedRobot = robot.copy(id = nextId++)
        robots[storedRobot.id] = storedRobot
        return RepositorySaveResult.Success(storedRobot)
    }

    fun update(robot: RobotEndpoint): RepositorySaveResult<RobotEndpoint> {
        if (!robots.containsKey(robot.id)) {
            return RepositorySaveResult.NotFound
        }
        if (hasDuplicateName(robot)) {
            return RepositorySaveResult.DuplicateName
        }

        robots[robot.id] = robot
        return RepositorySaveResult.Success(robot)
    }

    fun deleteById(id: Long) {
        robots.remove(id)
    }

    fun findById(id: Long): RobotEndpoint? = robots[id]

    fun getAll(): List<RobotEndpoint> = robots.values.toList()

    private fun hasDuplicateName(robot: RobotEndpoint): Boolean {
        val normalizedName = robot.name.normalized()
        return robots.values.any { existing ->
            existing.id != robot.id && existing.name.normalized() == normalizedName
        }
    }

    private fun String.normalized(): String = trim().lowercase(Locale.ROOT)
}
