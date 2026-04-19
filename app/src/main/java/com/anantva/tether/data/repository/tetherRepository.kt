package com.anantva.tether.data.repository

import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

class TetherRepository(
    private val userProfileDao: UserProfileDao,
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao
) {
    // ==========================================
    // USER PROFILE METHODS
    // ==========================================

    fun getUserProfile(uid: String): Flow<UserProfileEntity?> {
        return userProfileDao.getUserProfile(uid)
    }

    suspend fun saveUserProfile(user: UserProfileEntity) {
        userProfileDao.insertOrUpdateUser(user)
    }

    suspend fun updateStreak(uid: String, newStreak: Int) {
        userProfileDao.updateStreak(uid, newStreak)
    }

    suspend fun updateBalances(uid: String, currentBalance: Double, emergencyBalance: Double) {
        userProfileDao.updateBalances(uid, currentBalance, emergencyBalance)
    }

    // ==========================================
    // GOAL METHODS
    // ==========================================

    fun getActiveGoal(): Flow<GoalEntity?> {
        return goalDao.getActiveGoal()
    }

    suspend fun saveGoal(goal: GoalEntity) {
        goalDao.insertGoal(goal)
    }

    suspend fun setActiveGoal(goal: GoalEntity) {
        goalDao.deactivateAllGoals()
        goalDao.insertGoal(goal.copy(isActive = true))
    }

    suspend fun completeGoal(goalId: Int) {
        goalDao.markGoalAsCompleted(goalId)
    }

    suspend fun updateActiveGoalTarget(targetAmount: Double) {
        goalDao.updateActiveGoalTarget(targetAmount)
    }

    suspend fun clearAllData() {
        transactionDao.deleteAllTransactions()
        goalDao.deleteAllGoals()
        userProfileDao.deleteAllUsers()
    }

    // ==========================================
    // TRANSACTION METHODS
    // ==========================================

    fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return transactionDao.getDailyNetSpent(startOfDay, endOfDay)
    }

    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return transactionDao.observeDailyExpenseSpent(startOfDay, endOfDay)
    }

    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Double {
        return transactionDao.getExpenseSpentValue(startOfDay, endOfDay)
    }

    suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int {
        return transactionDao.getConfirmedTransactionCount(startOfDay, endOfDay)
    }

    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAllTransactions()
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction)
    }

    fun observePendingTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.observePendingTransactions()
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deletePendingTransaction(id: Long) {
        transactionDao.deletePendingTransaction(id)
    }

    suspend fun confirmAndUpdateTransaction(
        id:       Long,
        amount:   Double,
        merchant: String,
        type:     String
    ) {
        val existing = transactionDao.getTransactionById(id) ?: return
        transactionDao.updateTransaction(
            existing.copy(
                amount   = amount,
                merchant = merchant,
                type     = type,
                status   = "CONFIRMED"
            )
        )
    }
}
