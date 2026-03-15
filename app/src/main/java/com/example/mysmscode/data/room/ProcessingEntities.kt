package com.example.mysmscode.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sms_records")
data class SmsRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "sender_number")
    val senderNumber: String,
    @ColumnInfo(name = "message_body")
    val messageBody: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    val matched: Boolean,
    @ColumnInfo(name = "matched_keyword")
    val matchedKeyword: String? = null,
    @ColumnInfo(name = "processing_status")
    val processingStatus: String,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,
    val source: String,
)

@Entity(
    tableName = "forward_attempts",
    foreignKeys = [
        ForeignKey(
            entity = SmsRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["sms_record_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RobotEndpointEntity::class,
            parentColumns = ["id"],
            childColumns = ["robot_endpoint_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["sms_record_id"]), Index(value = ["robot_endpoint_id"])]
)
data class ForwardAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "sms_record_id")
    val smsRecordId: Long,
    @ColumnInfo(name = "robot_endpoint_id")
    val robotEndpointId: Long,
    val channel: String,
    @ColumnInfo(name = "attempt_number")
    val attemptNumber: Int,
    val status: String,
    @ColumnInfo(name = "response_code")
    val responseCode: String? = null,
    @ColumnInfo(name = "response_message")
    val responseMessage: String? = null,
    @ColumnInfo(name = "attempted_at")
    val attemptedAt: Long,
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long? = null,
    val recoverable: Boolean,
)