package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.parser.CategoryEngine
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalTx"

@Singleton
class LocalTransactionDataSource @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryEngine: CategoryEngine
) : TransactionDataSource {

    override fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> =
        transactionDao.getDailyNetSpent(startOfDay, endOfDay)

    override fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?> =
        transactionDao.observeDailyExpenseSpent(startOfDay, endOfDay)

    override suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        transactionDao.getExpenseSpentValue(startOfDay, endOfDay)

    override suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int =
        transactionDao.getConfirmedTransactionCount(startOfDay, endOfDay)

    override fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactions()

    override suspend fun getAllConfirmedTransactions(): List<TransactionEntity> =
        transactionDao.getAllConfirmedTransactions()

    override suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getTransactionById(id)

    override suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend> =
        transactionDao.getCategoryBreakdown(startOfDay, endOfDay)

    override suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        transactionDao.getNormalExpenseSpentValue(startOfDay, endOfDay)

    override suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int =
        confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { txn ->
                txn.type == "Expense" && SpendingCategories.isDiscretionarySpend(
                    category = txn.category,
                    merchant = txn.merchant,
                    txnCategory = txn.txnCategory
                )
            }
            .sumOf { it.amount }
            .toInt()

    override suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int =
        confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && it.spendNature == "WANT" }
            .sumOf { it.amount }
            .toInt()

    override suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int =
        confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && it.spendNature == "NEED" }
            .sumOf { it.amount }
            .toInt()

    override suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int =
        confirmedTransactionsInRange(startOfDay, endOfDay).sumOf { txn ->
            if (txn.status != "CONFIRMED" || txn.type != "Expense") 0.0
            else {
                val penaltyWeight = SpendingCategories.streakPenaltyWeight(
                    category = txn.category,
                    merchant = txn.merchant,
                    txnCategory = txn.txnCategory
                )
                txn.amount * penaltyWeight
            }
        }.roundToInt()

    override suspend fun confirmedTransactionsInRange(startOfDay: Long, endOfDay: Long): List<TransactionEntity> =
        transactionDao.getConfirmedTransactionsInRange(startOfDay, endOfDay)

    override suspend fun addTransaction(transaction: TransactionEntity): Boolean {
        return try {
            transactionDao.upsertTransaction(transaction)
            Log.d(TAG, "Transaction upserted locally, txnId=${transaction.transactionId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "addTransaction error", e)
            false
        }
    }

    override suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean {
        return try {
            val txn = transactionDao.getTransactionById(transactionId) ?: return false
            transactionDao.updateTransaction(txn.copy(category = newCategory))
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateTransactionCategory error", e)
            false
        }
    }

    override suspend fun updateTransaction(transaction: TransactionEntity): Boolean {
        return try {
            transactionDao.upsertTransaction(transaction)
            Log.d(TAG, "Transaction upserted locally, txnId=${transaction.transactionId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateTransaction error", e)
            false
        }
    }

    override suspend fun deleteTransaction(userId: String, transactionId: Long) {
        transactionDao.deleteTransactionById(transactionId)
    }
}
