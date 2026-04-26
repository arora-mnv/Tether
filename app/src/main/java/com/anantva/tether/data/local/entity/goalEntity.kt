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
}