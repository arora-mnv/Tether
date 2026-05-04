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

    companion object {
        fun fromMap(data: Map<String, Any?>): UserProfileEntity {
            return UserProfileEntity(
                uid = data["uid"] as? String ?: "",
                authProvider = data["authProvider"] as? String ?: "",
                emailOrPhone = data["emailOrPhone"] as? String,
                storagePreference = data["storagePreference"] as? String ?: "local",
                currentBalance = (data["currentBalance"] as? Number)?.toDouble() ?: 0.0,
                emergencyFundBalance = (data["emergencyFundBalance"] as? Number)?.toDouble() ?: 0.0,
                currentStreak = (data["currentStreak"] as? Number)?.toInt() ?: 0
            )
        }
    }
}