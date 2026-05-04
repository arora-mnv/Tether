package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true)
    val goalId: Int = 0,
    val targetAmount: Double,
    val startDate: Long,
    val endDate: Long,
    val isActive: Boolean
) {
    fun toMap(): Map<String, Any> = mapOf(
        "goalId" to goalId,
        "targetAmount" to targetAmount,
        "startDate" to startDate,
        "endDate" to endDate,
        "isActive" to isActive
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): GoalEntity {
            return GoalEntity(
                goalId = (data["goalId"] as? Number)?.toInt() ?: 0,
                targetAmount = (data["targetAmount"] as? Number)?.toDouble() ?: 0.0,
                startDate = (data["startDate"] as? Number)?.toLong() ?: 0L,
                endDate = (data["endDate"] as? Number)?.toLong() ?: 0L,
                isActive = (data["isActive"] as? Boolean) ?: false
            )
        }
    }
}