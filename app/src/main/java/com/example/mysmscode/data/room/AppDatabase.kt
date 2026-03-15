package com.example.mysmscode.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Dao
interface RobotEndpointDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(robot: RobotEndpointEntity): Long

    @Query("SELECT * FROM robot_endpoints ORDER BY name")
    suspend fun getAll(): List<RobotEndpointEntity>
}

@Dao
interface SenderRuleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: SenderRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<SenderRuleRobotCrossRef>)

    @Transaction
    @Query("SELECT * FROM sender_rules WHERE sender_number = :senderNumber LIMIT 1")
    suspend fun findBySenderNumber(senderNumber: String): SenderRuleWithRobots?

    @Transaction
    @Query("SELECT * FROM sender_rules ORDER BY sender_number")
    suspend fun getAll(): List<SenderRuleWithRobots>
}

@Dao
interface RetryPolicyConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: RetryPolicyConfigEntity)

    @Query("SELECT * FROM retry_policy_config WHERE id = 1 LIMIT 1")
    suspend fun get(): RetryPolicyConfigEntity?
}

@Dao
interface ProcessingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SmsRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<ForwardAttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: ForwardAttemptEntity): Long

    @Query("SELECT COUNT(*) FROM sms_records")
    suspend fun countRecords(): Int

    @Query("SELECT * FROM sms_records ORDER BY received_at DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<SmsRecordEntity>

    @Query(
        """
        SELECT fa.id AS attempt_id, fa.sms_record_id, sr.sender_number, sr.message_body, sr.matched_keyword,
               sr.received_at, fa.robot_endpoint_id, re.name AS robot_name, re.type AS robot_type,
               fa.attempt_number, fa.response_message, fa.recoverable, fa.next_retry_at
        FROM forward_attempts fa
        INNER JOIN sms_records sr ON sr.id = fa.sms_record_id
        INNER JOIN robot_endpoints re ON re.id = fa.robot_endpoint_id
        WHERE fa.status = 'FAILED'
          AND fa.recoverable = 1
          AND fa.attempt_number = (
              SELECT MAX(inner_fa.attempt_number)
              FROM forward_attempts inner_fa
              WHERE inner_fa.sms_record_id = fa.sms_record_id
                AND inner_fa.robot_endpoint_id = fa.robot_endpoint_id
          )
        ORDER BY fa.attempted_at DESC
        LIMIT :limit
        """
    )
    suspend fun getRetryableFailedAttempts(limit: Int): List<RetryableAttemptRow>

    @Query(
        """
        SELECT fa.id AS attempt_id, fa.sms_record_id, sr.sender_number, sr.message_body, sr.matched_keyword,
               sr.received_at, fa.robot_endpoint_id, re.name AS robot_name, re.type AS robot_type,
               fa.attempt_number, fa.response_message, fa.recoverable, fa.next_retry_at
        FROM forward_attempts fa
        INNER JOIN sms_records sr ON sr.id = fa.sms_record_id
        INNER JOIN robot_endpoints re ON re.id = fa.robot_endpoint_id
        WHERE fa.id = :attemptId
        LIMIT 1
        """
    )
    suspend fun getRetryableAttemptById(attemptId: Long): RetryableAttemptRow?

    @Query(
        """
        SELECT fa.id AS attempt_id, fa.sms_record_id, sr.sender_number, sr.message_body, sr.matched_keyword,
               sr.received_at, fa.robot_endpoint_id, re.name AS robot_name, re.type AS robot_type,
               fa.attempt_number, fa.response_message, fa.recoverable, fa.next_retry_at
        FROM forward_attempts fa
        INNER JOIN sms_records sr ON sr.id = fa.sms_record_id
        INNER JOIN robot_endpoints re ON re.id = fa.robot_endpoint_id
        WHERE fa.status = 'FAILED'
          AND fa.recoverable = 1
          AND fa.next_retry_at IS NOT NULL
          AND fa.next_retry_at <= :now
          AND fa.attempt_number = (
              SELECT MAX(inner_fa.attempt_number)
              FROM forward_attempts inner_fa
              WHERE inner_fa.sms_record_id = fa.sms_record_id
                AND inner_fa.robot_endpoint_id = fa.robot_endpoint_id
          )
        ORDER BY fa.next_retry_at ASC
        LIMIT :limit
        """
    )
    suspend fun getDueRetryableAttempts(now: Long, limit: Int): List<RetryableAttemptRow>

    @Query(
        """
        SELECT fa.*
        FROM forward_attempts fa
        INNER JOIN (
            SELECT robot_endpoint_id, MAX(attempt_number) AS max_attempt_number
            FROM forward_attempts
            WHERE sms_record_id = :smsRecordId
            GROUP BY robot_endpoint_id
        ) latest
        ON latest.robot_endpoint_id = fa.robot_endpoint_id
        AND latest.max_attempt_number = fa.attempt_number
        WHERE fa.sms_record_id = :smsRecordId
        """
    )
    suspend fun getLatestAttemptsForRecord(smsRecordId: Long): List<ForwardAttemptEntity>

    @Query(
        """
        UPDATE sms_records
        SET processing_status = :status,
            failure_reason = :failureReason
        WHERE id = :recordId
        """
    )
    suspend fun updateRecordStatus(recordId: Long, status: String, failureReason: String?)
}

@Database(
    entities = [
        RobotEndpointEntity::class,
        SenderRuleEntity::class,
        RetryPolicyConfigEntity::class,
        SenderRuleRobotCrossRef::class,
        SmsRecordEntity::class,
        ForwardAttemptEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun robotEndpointDao(): RobotEndpointDao
    abstract fun senderRuleDao(): SenderRuleDao
    abstract fun retryPolicyConfigDao(): RetryPolicyConfigDao
    abstract fun processingDao(): ProcessingDao
}