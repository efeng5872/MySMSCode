package com.example.mysmscode.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.mysmscode.domain.RetryPolicyConfig
import com.example.mysmscode.domain.MonitoringPersistenceState
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
import com.example.mysmscode.domain.SenderMatchMode
import com.example.mysmscode.domain.SenderRule

@Entity(
    tableName = "robot_endpoints",
    indices = [Index(value = ["name"], unique = true)]
)
data class RobotEndpointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: RobotType,
    val enabled: Boolean,
    @ColumnInfo(name = "webhook_url")
    val webhookUrl: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): RobotEndpoint = RobotEndpoint(
        id = id,
        name = name,
        type = type,
        enabled = enabled,
        webhookUrl = webhookUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(domain: RobotEndpoint): RobotEndpointEntity = RobotEndpointEntity(
            id = domain.id,
            name = domain.name,
            type = domain.type,
            enabled = domain.enabled,
            webhookUrl = domain.webhookUrl,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
    }
}

@Entity(
    tableName = "sender_rules",
    indices = [Index(value = ["sender_number"], unique = true)]
)
data class SenderRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "sender_number")
    val senderNumber: String,
    @ColumnInfo(name = "sender_match_mode")
    val senderMatchMode: SenderMatchMode,
    val enabled: Boolean,
    @ColumnInfo(name = "keyword_blob")
    val keywordBlob: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(selectedRobotIds: List<Long>): SenderRule = SenderRule(
        id = id,
        name = name,
        senderNumber = senderNumber,
        senderMatchMode = senderMatchMode,
        enabled = enabled,
        keywords = KeywordListCodec.decode(keywordBlob),
        selectedRobotIds = selectedRobotIds,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(domain: SenderRule): SenderRuleEntity = SenderRuleEntity(
            id = domain.id,
            name = domain.name,
            senderNumber = domain.senderNumber,
            senderMatchMode = domain.senderMatchMode,
            enabled = domain.enabled,
            keywordBlob = KeywordListCodec.encode(domain.keywords),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
        )
    }
}

@Entity(tableName = "retry_policy_config")
data class RetryPolicyConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "first_retry_delay_seconds")
    val firstRetryDelaySeconds: Int,
    @ColumnInfo(name = "second_retry_delay_seconds")
    val secondRetryDelaySeconds: Int,
    @ColumnInfo(name = "third_retry_delay_seconds")
    val thirdRetryDelaySeconds: Int,
) {
    fun toDomain(): RetryPolicyConfig = RetryPolicyConfig(
        firstRetryDelaySeconds = firstRetryDelaySeconds,
        secondRetryDelaySeconds = secondRetryDelaySeconds,
        thirdRetryDelaySeconds = thirdRetryDelaySeconds,
    )

    companion object {
        fun fromDomain(domain: RetryPolicyConfig): RetryPolicyConfigEntity = RetryPolicyConfigEntity(
            firstRetryDelaySeconds = domain.firstRetryDelaySeconds,
            secondRetryDelaySeconds = domain.secondRetryDelaySeconds,
            thirdRetryDelaySeconds = domain.thirdRetryDelaySeconds,
        )
    }
}

@Entity(tableName = "monitoring_state")
data class MonitoringStateEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "monitoring_enabled")
    val monitoringEnabled: Boolean,
    @ColumnInfo(name = "stopped_by_user")
    val stoppedByUser: Boolean,
    @ColumnInfo(name = "last_monitoring_started_at")
    val lastMonitoringStartedAt: Long?,
    @ColumnInfo(name = "last_monitoring_stopped_at")
    val lastMonitoringStoppedAt: Long?,
    @ColumnInfo(name = "last_recovery_started_at")
    val lastRecoveryStartedAt: Long?,
    @ColumnInfo(name = "last_recovery_trigger")
    val lastRecoveryTrigger: String?,
    @ColumnInfo(name = "last_service_heartbeat_at")
    val lastServiceHeartbeatAt: Long? = null,
    @ColumnInfo(name = "last_runtime_event_at")
    val lastRuntimeEventAt: Long? = null,
    @ColumnInfo(name = "last_runtime_event")
    val lastRuntimeEvent: String? = null,
    @ColumnInfo(name = "last_watchdog_check_at")
    val lastWatchdogCheckAt: Long? = null,
    @ColumnInfo(name = "last_process_exit_at")
    val lastProcessExitAt: Long? = null,
    @ColumnInfo(name = "last_process_exit_reason")
    val lastProcessExitReason: String? = null,
    @ColumnInfo(name = "last_process_exit_description")
    val lastProcessExitDescription: String? = null,
) {
    fun toDomain(): MonitoringPersistenceState = MonitoringPersistenceState(
        monitoringEnabled = monitoringEnabled,
        stoppedByUser = stoppedByUser,
        lastMonitoringStartedAt = lastMonitoringStartedAt,
        lastMonitoringStoppedAt = lastMonitoringStoppedAt,
        lastRecoveryStartedAt = lastRecoveryStartedAt,
        lastRecoveryTrigger = lastRecoveryTrigger,
        lastServiceHeartbeatAt = lastServiceHeartbeatAt,
        lastRuntimeEventAt = lastRuntimeEventAt,
        lastRuntimeEvent = lastRuntimeEvent,
        lastWatchdogCheckAt = lastWatchdogCheckAt,
        lastProcessExitAt = lastProcessExitAt,
        lastProcessExitReason = lastProcessExitReason,
        lastProcessExitDescription = lastProcessExitDescription,
    )

    companion object {
        fun fromDomain(domain: MonitoringPersistenceState): MonitoringStateEntity = MonitoringStateEntity(
            monitoringEnabled = domain.monitoringEnabled,
            stoppedByUser = domain.stoppedByUser,
            lastMonitoringStartedAt = domain.lastMonitoringStartedAt,
            lastMonitoringStoppedAt = domain.lastMonitoringStoppedAt,
            lastRecoveryStartedAt = domain.lastRecoveryStartedAt,
            lastRecoveryTrigger = domain.lastRecoveryTrigger,
            lastServiceHeartbeatAt = domain.lastServiceHeartbeatAt,
            lastRuntimeEventAt = domain.lastRuntimeEventAt,
            lastRuntimeEvent = domain.lastRuntimeEvent,
            lastWatchdogCheckAt = domain.lastWatchdogCheckAt,
            lastProcessExitAt = domain.lastProcessExitAt,
            lastProcessExitReason = domain.lastProcessExitReason,
            lastProcessExitDescription = domain.lastProcessExitDescription,
        )
    }
}

@Entity(
    tableName = "sender_rule_robot_cross_ref",
    primaryKeys = ["sender_rule_id", "robot_endpoint_id"],
    foreignKeys = [
        ForeignKey(
            entity = SenderRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sender_rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RobotEndpointEntity::class,
            parentColumns = ["id"],
            childColumns = ["robot_endpoint_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["robot_endpoint_id"])]
)
data class SenderRuleRobotCrossRef(
    @ColumnInfo(name = "sender_rule_id")
    val senderRuleId: Long,
    @ColumnInfo(name = "robot_endpoint_id")
    val robotEndpointId: Long,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

data class SenderRuleWithRobots(
    @Embedded
    val rule: SenderRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sender_rule_id",
    )
    val selectedRobotIds: List<SenderRuleRobotCrossRef>,
) {
    fun toDomain(): SenderRule = rule.toDomain(
        selectedRobotIds = selectedRobotIds.sortedBy(SenderRuleRobotCrossRef::sortOrder).map(SenderRuleRobotCrossRef::robotEndpointId)
    )
}
