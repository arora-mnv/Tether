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
)