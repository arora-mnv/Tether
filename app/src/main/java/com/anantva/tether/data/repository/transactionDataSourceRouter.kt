package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.parser.CategoryEngine
import com.anantva.tether.data.parser.MerchantLearningEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TxRouter"

@Singleton
class TransactionDataSourceRouter @Inject constructor(
    private val local: LocalTransactionDataSource,
    private val cloud: CloudTransactionDataSource,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager,
    private val categoryEngine: CategoryEngine,
    private val firestoreRepository: FirestoreRepository,
    private val merchantLearningEngine: MerchantLearningEngine
) : TransactionDataSource {

    // Always read from local (Room) which is reactive via Room DAO flows.
    // Room is always up-to-date: all writes go to Room first, and SyncOrchestrator
    // pulls cloud transactions into Room. Cloud data source is write-only.
    override fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> =
        local.getDailyNetSpent(startOfDay, endOfDay)

    override fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?> =
        local.observeDailyExpenseSpent(startOfDay, endOfDay)

    override fun getAllTransactions(): Flow<List<TransactionEntity>> =
        local.getAllTransactions()

    override suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        local.getExpenseSpentValue(startOfDay, endOfDay)

    override suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int =
        local.getConfirmedTransactionCount(startOfDay, endOfDay)

    override suspend fun getAllConfirmedTransactions(): List<TransactionEntity> =
        local.getAllConfirmedTransactions()

    override suspend fun getTransactionById(id: Long): TransactionEntity? =
        local.getTransactionById(id)

    override suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend> =
        local.getCategoryBreakdown(startOfDay, endOfDay)

    override suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        local.getNormalExpenseSpentValue(startOfDay, endOfDay)

    override suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int =
        local.getDiscretionarySpend(startOfDay, endOfDay)

    override suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int =
        local.getWantSpend(startOfDay, endOfDay)

    override suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int =
        local.getNeedSpend(startOfDay, endOfDay)

    override suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int =
        local.getStreakRelevantSpent(startOfDay, endOfDay)

    override suspend fun confirmedTransactionsInRange(startOfDay: Long, endOfDay: Long): List<TransactionEntity> =
        local.confirmedTransactionsInRange(startOfDay, endOfDay)

    private suspend fun shouldSyncToCloud(): Boolean =
        preferencesRepository.isCloudStorage.first() && authManager.getCurrentUserId().orEmpty().isNotEmpty()

    override suspend fun addTransaction(transaction: TransactionEntity): Boolean {
        val enriched = enrichTransaction(transaction)
        if (!local.addTransaction(enriched)) return false
        persistCategoryLearning(enriched)
        if (shouldSyncToCloud()) {
            try { cloud.addTransaction(enriched) }
            catch (e: Exception) { Log.e(TAG, "Cloud save failed", e) }
        }
        return true
    }

    override suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean {
        val txn = local.getTransactionById(transactionId) ?: return false
        if (!local.updateTransactionCategory(transactionId, newCategory)) return false
        val updated = enrichTransaction(txn.copy(category = newCategory))
        persistCategoryLearning(updated)
        if (shouldSyncToCloud()) cloud.updateTransactionCategory(transactionId, newCategory)
        return true
    }

    override suspend fun updateTransaction(transaction: TransactionEntity): Boolean {
        val enriched = enrichTransaction(transaction)
        if (!local.updateTransaction(enriched)) return false
        persistCategoryLearning(enriched)
        if (shouldSyncToCloud()) cloud.updateTransaction(enriched)
        return true
    }

    override suspend fun deleteTransaction(userId: String, transactionId: Long) {
        local.deleteTransaction(userId, transactionId)
        if (shouldSyncToCloud()) cloud.deleteTransaction(userId, transactionId)
    }

    private suspend fun enrichTransaction(transaction: TransactionEntity): TransactionEntity {
        val category = if (transaction.category.isNotBlank() && transaction.category != "Other") {
            transaction.category
        } else {
            categoryEngine.categorize(transaction.merchant, transaction.type)
        }
        val spendNature = if (transaction.type == "Expense") {
            com.anantva.tether.data.local.entity.SpendingCategories.spendNatureFor(
                category = category, merchant = transaction.merchant,
                txnCategory = transaction.txnCategory
            ).toDbValue()
        } else {
            transaction.spendNature
        }
        return transaction.copy(category = category, spendNature = spendNature)
    }

    private suspend fun persistCategoryLearning(transaction: TransactionEntity) {
        if (transaction.type != "Expense") return
        merchantLearningEngine.learn(transaction.merchant, transaction.category)
        val corrections = categoryEngine.saveCorrection(transaction.merchant, transaction.category)
        if (corrections.isEmpty() || !shouldSyncToCloud()) return
        corrections.forEach { correction ->
            firestoreRepository.saveCategoryCorrection(
                authManager.getCurrentUserId().orEmpty(), correction
            )
        }
    }
}
