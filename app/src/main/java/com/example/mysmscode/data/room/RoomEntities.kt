package com.example.mysmscode.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded
import com.example.mysmscode.domain.RobotEndpoint
import com.example.mysmscode.domain.RobotType
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
    @ColumnInfo(name = "sender_number")
    val senderNumber: String,
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
        senderNumber = senderNumber,
        enabled = enabled,
        keywords = KeywordListCodec.decode(keywordBlob),
        selectedRobotIds = selectedRobotIds,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(domain: SenderRule): SenderRuleEntity = SenderRuleEntity(
            id = domain.id,
            senderNumber = domain.senderNumber,
            enabled = domain.enabled,
            keywordBlob = KeywordListCodec.encode(domain.keywords),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
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