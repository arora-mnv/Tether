package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val uid: String,
    val authProvider: String,
    val emailOrPhone: String?,
    val storagePreference: String,
    val currentBalance: Double,
    val emergencyFundBalance: Double,
    val currentStreak: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "authProvider" to authProvider,
        "emailOrPhone" to (emailOrPhone ?: ""),
        "storagePreference" to storagePreference,
        "currentBalance" to currentBalance,
        "emergencyFundBalance" to emergencyFundBalance,
        "currentStreak" to currentStreak
    )
}