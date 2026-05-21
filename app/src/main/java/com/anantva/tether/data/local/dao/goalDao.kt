package com.anantva.tether.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.GoalContributionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity): Long

    // The Dashboard needs to know the active goal to calculate the Daily Limit
    @Query("SELECT * FROM goals WHERE isActive = 1 LIMIT 1")
    fun getActiveGoal(): Flow<GoalEntity?>

    // When a goal is finished (balloon pops), we deactivate it
    @Query("UPDATE goals SET isActive = 0 WHERE goalId = :goalId")
    suspend fun markGoalAsCompleted(goalId: Int)

    @Query("UPDATE goals SET isActive = 0")
    suspend fun deactivateAllGoals()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("UPDATE goals SET targetAmount = :targetAmount WHERE isActive = 1")
    suspend fun updateActiveGoalTarget(targetAmount: Double)

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalContribution(contribution: GoalContributionEntity): Long

    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId ORDER BY timestamp ASC")
    fun getGoalContributions(goalId: Int): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY timestamp ASC")
    suspend fun getAllGoalContributions(): List<GoalContributionEntity>

    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId AND timestamp >= :startOfMonth AND timestamp <= :endOfMonth")
    suspend fun deleteGoalContributionForMonth(goalId: Int, startOfMonth: Long, endOfMonth: Long)

    @Query("SELECT SUM(amount) FROM goal_contributions WHERE goalId = :goalId")
    fun getTotalContributions(goalId: Int): Flow<Double?>
}
