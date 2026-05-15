package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CloudTx"

@Singleton
class CloudTransactionDataSource @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val authManager: FirebaseAuthManager
) : TransactionDataSource {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    private fun Map<String, Any?>.toTransactionEntity() = TransactionEntity(
        transactionId = (this["transactionId"] as? Number)?.toLong() ?: 0L,
        amount        = (this["amount"]        as? Number)?.toDouble() ?: 0.0,
        merchant      = (this["merchant"]      as? String) ?: "",
        type          = (this["type"]          as? String) ?: "",
        source        = (this["source"]        as? String) ?: "",
        date          = (this["date"]          as? Number)?.toLong() ?: 0L,
        status        = (this["status"]        as? String) ?: "CONFIRMED",
        category      = (this["category"]      as? String) ?: SpendingCategories.OTHER,
        txnCategory   = (this["txnCategory"]   as? String) ?: "NORMAL",
        spendNature   = (this["spendNature"]   as? String) ?: "UNKNOWN"
    )

    private suspend fun fetchDayTransactions(uid: String, startOfDay: Long, endOfDay: Long): List<TransactionEntity> {
        return try {
            val snapshot = userDoc(uid).collection("transactions")
                .whereGreaterThanOrEqualTo("date", startOfDay)
                .whereLessThanOrEqualTo("date", endOfDay)
                .get().await()
            @Suppress("UNCHECKED_CAST")
            snapshot.documents.mapNotNull {
                (it.data as? Map<String, Any?>)?.toTransactionEntity()
            }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "fetchDayTransactions error for uid=$uid", e)
            }
            emptyList()
        }
    }

    override fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> = flow {
        val uid = uid() ?: return@flow
        val net = fetchDayTransactions(uid, startOfDay, endOfDay)
            .filter { it.status == "CONFIRMED" }
            .sumOf {
                when (it.type) {
                    "Expense" -> it.amount
                    "Credit"  -> -it.amount
                    else      -> 0.0
                }
            }
        emit(net)
    }

    override fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?> = flow {
        val uid = uid() ?: return@flow
        val total = fetchDayTransactions(uid, startOfDay, endOfDay)
            .filter { it.status == "CONFIRMED" && it.type == "Expense" }
            .sumOf { it.amount }
            .toInt()
        emit(total)
    }

    override suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int {
        val uid = uid() ?: return 0
        return fetchDayTransactions(uid, startOfDay, endOfDay)
            .filter { it.status == "CONFIRMED" && it.type == "Expense" }
            .sumOf { it.amount }
            .toInt()
    }

    override suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int {
        val uid = uid() ?: return 0
        return fetchDayTransactions(uid, startOfDay, endOfDay).count { it.status == "CONFIRMED" }
    }

    override fun getAllTransactions(): Flow<List<TransactionEntity>> = flow {
        val uid = uid() ?: return@flow
        emit(fetchDayTransactions(uid, 0, Long.MAX_VALUE))
    }

    override suspend fun getAllConfirmedTransactions(): List<TransactionEntity> {
        val uid = uid() ?: return emptyList()
        return fetchDayTransactions(uid, 0, Long.MAX_VALUE).filter { it.status == "CONFIRMED" }
    }

    override suspend fun getTransactionById(id: Long): TransactionEntity? {
        val uid = uid() ?: return null
        return try {
            val doc = userDoc(uid).collection("transactions").document(id.toString()).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.data as? Map<String, Any?>)?.toTransactionEntity()
        } catch (e: Exception) {
            Log.e(TAG, "getTransactionById error for id=$id", e)
            null
        }
    }

    override suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend> {
        val uid = uid() ?: return emptyList()
        return fetchDayTransactions(uid, startOfDay, endOfDay)
            .filter { it.status == "CONFIRMED" && it.type == "Expense" }
            .groupBy { it.category }
            .map { (cat, txns) -> CategorySpend(cat, txns.sumOf { it.amount }.toInt()) }
            .sortedByDescending { it.total }
    }

    override suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int {
        val uid = uid() ?: return 0
        return fetchDayTransactions(uid, startOfDay, endOfDay)
            .filter { it.status == "CONFIRMED" && it.isStreakRelevant }
            .sumOf { it.amount }
            .toInt()
    }

    override suspend fun confirmedTransactionsInRange(startOfDay: Long, endOfDay: Long): List<TransactionEntity> {
        val uid = uid() ?: return emptyList()
        return fetchDayTransactions(uid, startOfDay, endOfDay).filter { it.status == "CONFIRMED" }
    }

    override suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { txn ->
                txn.type == "Expense" && SpendingCategories.isDiscretionarySpend(
                    category = txn.category, merchant = txn.merchant, txnCategory = txn.txnCategory
                )
            }
            .sumOf { it.amount }.toInt()
    }

    override suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && it.spendNature == "WANT" }
            .sumOf { it.amount }.toInt()
    }

    override suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && it.spendNature == "NEED" }
            .sumOf { it.amount }.toInt()
    }

    override suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay).sumOf { txn ->
            if (txn.status != "CONFIRMED" || txn.type != "Expense") 0.0
            else {
                val penaltyWeight = SpendingCategories.streakPenaltyWeight(
                    category = txn.category, merchant = txn.merchant, txnCategory = txn.txnCategory
                )
                txn.amount * penaltyWeight
            }
        }.roundToInt()
    }

    override suspend fun addTransaction(transaction: TransactionEntity): Boolean {
        val uid = uid() ?: return false
        return firestoreRepository.saveTransaction(uid, transaction)
    }

    override suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean {
        val uid = uid() ?: return false
        return try {
            val doc = userDoc(uid).collection("transactions").document(transactionId.toString()).get().await()
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return false
            val txn = data.toTransactionEntity().copy(category = newCategory)
            firestoreRepository.saveTransaction(uid, txn)
        } catch (e: Exception) {
            Log.e(TAG, "updateTransactionCategory error", e)
            false
        }
    }

    override suspend fun updateTransaction(transaction: TransactionEntity): Boolean {
        val uid = uid() ?: return false
        return firestoreRepository.saveTransaction(uid, transaction)
    }

    override suspend fun deleteTransaction(userId: String, transactionId: Long) {
        firestoreRepository.deleteTransaction(userId, transactionId)
    }

    private fun uid(): String? {
        val uid = authManager.getCurrentUserId()
        if (uid.isNullOrEmpty()) {
            Log.e(TAG, "Cannot resolve uid for cloud data source")
            return null
        }
        return uid
    }
}
