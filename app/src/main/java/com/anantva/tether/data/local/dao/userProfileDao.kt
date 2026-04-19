package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anantva.tether.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    // REPLACE mistake-proofs our code. If a user logs in again, it just overwrites the old row.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: UserProfileEntity)

    // Returns a Flow. The Dashboard will listen to this to instantly update the UI.
    @Query("SELECT * FROM user_profile WHERE uid = :uid LIMIT 1")
    fun getUserProfile(uid: String): Flow<UserProfileEntity?>

    // A specific query just to update the streak when they survive a day
    @Query("UPDATE user_profile SET currentStreak = :newStreak WHERE uid = :uid")
    suspend fun updateStreak(uid: String, newStreak: Int)

    // Updates the balances after a transaction
    @Query("UPDATE user_profile SET currentBalance = :newBalance, emergencyFundBalance = :newEmergencyBalance WHERE uid = :uid")
    suspend fun updateBalances(uid: String, newBalance: Double, newEmergencyBalance: Double)

    @Query("DELETE FROM user_profile")
    suspend fun deleteAllUsers()
}
