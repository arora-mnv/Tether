package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ContributionSyncStatus {
    SYNCED,
    PENDING_SYNC,
    FAILED_SYNC
}

@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["goalId"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class GoalContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val contributionId: Int = 0,
    val goalId: Int,
    val amount: Double,
    val timestamp: Long,
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: ContributionSyncStatus = ContributionSyncStatus.PENDING_SYNC
) {
    fun toMap(): Map<String, Any> = mapOf(
        "contributionId" to contributionId,
        "goalId" to goalId,
        "amount" to amount,
        "timestamp" to timestamp,
        "lastUpdated" to lastUpdated,
        "syncStatus" to syncStatus.name
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): GoalContributionEntity {
            return GoalContributionEntity(
                contributionId = (data["contributionId"] as? Number)?.toInt() ?: 0,
                goalId = (data["goalId"] as? Number)?.toInt() ?: 0,
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                lastUpdated = (data["lastUpdated"] as? Number)?.toLong() ?: 0L,
                syncStatus = try {
                    (data["syncStatus"] as? String)?.let { ContributionSyncStatus.valueOf(it) }
                        ?: ContributionSyncStatus.PENDING_SYNC
                } catch (_: Exception) {
                    ContributionSyncStatus.PENDING_SYNC
                }
            )
        }
    }
}
