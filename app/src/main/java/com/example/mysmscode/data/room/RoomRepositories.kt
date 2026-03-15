package com.example.mysmscode.data

import androidx.room.withTransaction
import com.example.mysmscode.DebugTraceLogger
import com.example.mysmscode.domain.ForwardAttemptDraft
import com.example.mysmscode.domain.ProcessingOutcomeDraft
import com.example.mysmscode.domain.RetryExecution
import com.example.mysmscode.domain.RetryableAttempt
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.SmsRecordDraft
import com.example.mysmscode.domain.SmsRecordPreview
import com.example.mysmscode.domain.buildPersistenceTrace
import kotlin.runCatching

class RoomRobotEndpointRepository(
    private val robotEndpointDao: RobotEndpointDao,
) {
    suspend fun save(robot: RobotEndpoint): RepositorySaveResult<RobotEndpoint> = runCatching {
        val id = robotEndpointDao.insert(RobotEndpointEntity.fromDomain(robot))
        RepositorySaveResult.Success(robot.copy(id = id))
    }.getOrElse {
        RepositorySaveResult.DuplicateName
    }

    suspend fun getAll(): List<RobotEndpoint> = robotEndpointDao.getAll().map(RobotEndpointEntity::toDomain)
}

class RoomSenderRuleRepository(
    private val database: AppDatabase,
    private val senderRuleDao: SenderRuleDao,
) {
    suspend fun save(rule: SenderRule): RepositorySaveResult<SenderRule> = runCatching {
        database.withTransaction {
            val ruleId = senderRuleDao.insert(SenderRuleEntity.fromDomain(rule))
            val crossRefs = rule.selectedRobotIds.mapIndexed { index, robotId ->
                SenderRuleRobotCrossRef(
                    senderRuleId = ruleId,
                    robotEndpointId = robotId,
                    sortOrder = index,
                )
            }
            senderRuleDao.insertCrossRefs(crossRefs)
            RepositorySaveResult.Success(rule.copy(id = ruleId))
        }
    }.getOrElse {
        RepositorySaveResult.DuplicateSenderNumber
    }

    suspend fun findBySenderNumber(senderNumber: String): SenderRule? {
        return senderRuleDao.findBySenderNumber(senderNumber)?.toDomain()
    }

    suspend fun getAll(): List<SenderRule> {
        return senderRuleDao.getAll().map(SenderRuleWithRobots::toDomain)
    }
}

class RoomProcessingRepository(
    private val database: AppDatabase,
    private val processingDao: ProcessingDao,
) {
    suspend fun saveOutcome(outcome: ProcessingOutcomeDraft) {
        database.withTransaction {
            val recordId = processingDao.insertRecord(outcome.record.toEntity())
            val attempts = outcome.attempts.map { it.toEntity(recordId) }
            if (attempts.isNotEmpty()) {
                processingDao.insertAttempts(attempts)
            }
        }
        DebugTraceLogger.d(buildPersistenceTrace(outcome))
    }

    suspend fun saveRetryExecution(failedAttempt: RetryableAttempt, execution: RetryExecution) {
        database.withTransaction {
            processingDao.insertAttempt(execution.nextAttempt.toEntity(failedAttempt.smsRecordId))
            val latestAttempts = processingDao.getLatestAttemptsForRecord(failedAttempt.smsRecordId)
            val failedLatestAttempts = latestAttempts.filter { it.status == "FAILED" }
            val finalStatus = if (failedLatestAttempts.isEmpty()) "SUCCESS" else "FAILED"
            val failureReason = failedLatestAttempts
                .mapNotNull { attempt -> attempt.responseMessage?.takeIf { message -> message.isNotBlank() }?.let { "${attempt.channel}: $it" } }
                .joinToString(separator = "; ")
                .ifBlank { null }
            processingDao.updateRecordStatus(failedAttempt.smsRecordId, finalStatus, failureReason)
        }
    }

    suspend fun countRecords(): Int = processingDao.countRecords()

    suspend fun getRecentRecords(limit: Int = 5): List<SmsRecordPreview> {
        return processingDao.getRecentRecords(limit).map { entity ->
            SmsRecordPreview(
                senderNumber = entity.senderNumber,
                messageBody = entity.messageBody,
                status = entity.processingStatus,
                source = entity.source,
                receivedAt = entity.receivedAt,
            )
        }
    }

    suspend fun getRetryableFailedAttempts(limit: Int = 20): List<RetryableAttempt> {
        return processingDao.getRetryableFailedAttempts(limit).map(RetryableAttemptRow::toDomain)
    }

    suspend fun getRetryableAttemptById(attemptId: Long): RetryableAttempt? {
        return processingDao.getRetryableAttemptById(attemptId)?.toDomain()
    }

    suspend fun getDueRetryableAttempts(now: Long, limit: Int = 20): List<RetryableAttempt> {
        return processingDao.getDueRetryableAttempts(now, limit).map(RetryableAttemptRow::toDomain)
    }
}

private fun SmsRecordDraft.toEntity(): SmsRecordEntity = SmsRecordEntity(
    senderNumber = senderNumber,
    messageBody = messageBody,
    receivedAt = receivedAt,
    matched = matchedKeyword != null,
    matchedKeyword = matchedKeyword,
    processingStatus = status.name,
    failureReason = failureReason,
    source = source.name,
)

private fun ForwardAttemptDraft.toEntity(recordId: Long): ForwardAttemptEntity = ForwardAttemptEntity(
    smsRecordId = recordId,
    robotEndpointId = robotId,
    channel = channel,
    attemptNumber = attemptNumber,
    status = status.name,
    responseCode = responseCode,
    responseMessage = responseMessage,
    attemptedAt = System.currentTimeMillis(),
    nextRetryAt = nextRetryAt,
    recoverable = recoverable,
)

private fun RetryableAttemptRow.toDomain(): RetryableAttempt = RetryableAttempt(
    attemptId = attemptId,
    smsRecordId = smsRecordId,
    senderNumber = senderNumber,
    messageBody = messageBody,
    matchedKeyword = matchedKeyword,
    receivedAt = receivedAt,
    robotId = robotId,
    robotName = robotName,
    robotType = RobotType.valueOf(robotType),
    attemptNumber = attemptNumber,
    lastErrorMessage = lastErrorMessage,
    recoverable = recoverable,
    nextRetryAt = nextRetryAt,
)
