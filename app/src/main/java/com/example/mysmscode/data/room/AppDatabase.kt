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
interface ProcessingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: SmsRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<ForwardAttemptEntity>)

    @Query("SELECT COUNT(*) FROM sms_records")
    suspend fun countRecords(): Int

    @Query("SELECT * FROM sms_records ORDER BY received_at DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<SmsRecordEntity>
}

@Database(
    entities = [
        RobotEndpointEntity::class,
        SenderRuleEntity::class,
        SenderRuleRobotCrossRef::class,
        SmsRecordEntity::class,
        ForwardAttemptEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun robotEndpointDao(): RobotEndpointDao
    abstract fun senderRuleDao(): SenderRuleDao
    abstract fun processingDao(): ProcessingDao
}