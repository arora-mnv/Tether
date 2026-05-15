package com.anantva.tether.data.repository

import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

interface TransactionDataSource {
    fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?>
    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?>
    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int
    suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    suspend fun getAllConfirmedTransactions(): List<TransactionEntity>
    suspend fun getTransactionById(id: Long): TransactionEntity?
    suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend>
    suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int
    suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int
    suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int
    suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int
    suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int
    suspend fun confirmedTransactionsInRange(startOfDay: Long, endOfDay: Long): List<TransactionEntity>
    suspend fun addTransaction(transaction: TransactionEntity): Boolean
    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean
    suspend fun updateTransaction(transaction: TransactionEntity): Boolean
    suspend fun deleteTransaction(userId: String, transactionId: Long)
}
