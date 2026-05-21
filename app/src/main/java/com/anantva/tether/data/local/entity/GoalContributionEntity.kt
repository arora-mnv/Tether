package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val timestamp: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "contributionId" to contributionId,
        "goalId" to goalId,
        "amount" to amount,
        "timestamp" to timestamp
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): GoalContributionEntity {
            return GoalContributionEntity(
                contributionId = (data["contributionId"] as? Number)?.toInt() ?: 0,
                goalId = (data["goalId"] as? Number)?.toInt() ?: 0,
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
