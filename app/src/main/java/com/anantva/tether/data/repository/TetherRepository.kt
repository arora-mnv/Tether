package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.TransactionPagingSource
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.GoalContributionEntity
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.RecurringType
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.SpendingCategories
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.anantva.tether.data.parser.CategoryEngine
import com.anantva.tether.data.parser.MerchantLearningEngine
import com.anantva.tether.data.parser.RecurringDetectionEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

private const val TAG = "TetherFirestore"

class TetherRepository(
    private val userProfileDao: UserProfileDao,
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val categoryCorrectionDao: CategoryCorrectionDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager,
    private val firestoreRepository: FirestoreRepository,
    private val categoryEngine: CategoryEngine,
    private val transactionDataSource: TransactionDataSourceRouter,
    private val merchantLearningEngine: MerchantLearningEngine,
    private val recurringDetectionEngine: RecurringDetectionEngine
) {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private suspend fun isCloud(): Boolean =
        preferencesRepository.isCloudStorage.first()

    private fun uid(): String = authManager.getCurrentUserId().orEmpty()

    private fun userDoc(uid: String) =
        firestore.collection("users").document(uid)

    // ==========================================
    // USER PROFILE METHODS
    // ==========================================

    fun getUserProfile(uid: String): Flow<UserProfileEntity?> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid.isNotEmpty()) {
                flow {
                    try {
                        val doc = userDoc(uid).get().await()
                        @Suppress("UNCHECKED_CAST")
                        emit((doc.data as? Map<String, Any?>)?.toUserProfileEntity())
                    } catch (e: FirebaseFirestoreException) {
                        val msg = e.message ?: ""
                        if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                            Log.e(TAG, "Firestore error: $msg")
                        } else {
                            Log.e(TAG, "getUserProfile Firestore error for uid=$uid", e)
                        }
                        emit(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "getUserProfile Firestore error for uid=$uid", e)
                        emit(null)
                    }
                }
            } else {
                userProfileDao.getUserProfile(uid)
            }
        }
    }

    suspend fun saveUserProfile(user: UserProfileEntity): Boolean {
        return try {
            if (isCloud() && uid().isNotEmpty()) {
                userDoc(uid()).set(user.toMap()).await()
            } else {
                userProfileDao.insertOrUpdateUser(user)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveUserProfile Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile Firestore error", e)
            false
        }
    }

    suspend fun updateStreak(uid: String, newStreak: Int): Boolean {
        return try {
            if (isCloud() && uid.isNotEmpty()) {
                userDoc(uid).update("currentStreak", newStreak).await()
            } else {
                userProfileDao.updateStreak(uid, newStreak)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "updateStreak Firestore error for uid=$uid", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "updateStreak Firestore error for uid=$uid", e)
            false
        }
    }

    suspend fun updateBalances(uid: String, currentBalance: Double, emergencyBalance: Double): Boolean {
        return try {
            if (isCloud() && uid.isNotEmpty()) {
                userDoc(uid).update(
                    mapOf(
                        "currentBalance"       to currentBalance,
                        "emergencyFundBalance" to emergencyBalance
                    )
                ).await()
            } else {
                userProfileDao.updateBalances(uid, currentBalance, emergencyBalance)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "updateBalances Firestore error for uid=$uid", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "updateBalances Firestore error for uid=$uid", e)
            false
        }
    }

    // ==========================================
    // GOAL METHODS
    // ==========================================

    fun getActiveGoal(): Flow<GoalEntity?> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid().isNotEmpty()) {
                flow {
                    try {
                        val snapshot = userDoc(uid())
                            .collection("goals")
                            .whereEqualTo("isActive", true)
                            .limit(1)
                            .get().await()
                        @Suppress("UNCHECKED_CAST")
                        emit(
                            (snapshot.documents.firstOrNull()?.data as? Map<String, Any?>)
                                ?.toGoalEntity()
                        )
                    } catch (e: FirebaseFirestoreException) {
                        val msg = e.message ?: ""
                        if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                            Log.e(TAG, "Firestore error: $msg")
                        } else {
                            Log.e(TAG, "getActiveGoal Firestore error", e)
                        }
                        emit(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "getActiveGoal Firestore error", e)
                        emit(null)
                    }
                }
            } else {
                goalDao.getActiveGoal()
            }
        }
    }

    suspend fun saveGoal(goal: GoalEntity): Boolean {
        return try {
            goalDao.upsertGoal(goal)
            if (isCloud() && uid().isNotEmpty()) {
                saveGoalToCloud(uid(), goal)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setActiveGoal(goal: GoalEntity): Boolean {
        return try {
            goalDao.deactivateAllGoals()
            goalDao.upsertGoal(goal.copy(isActive = true))
            if (isCloud() && uid().isNotEmpty()) {
                val uid = uid()
                val existing = userDoc(uid).collection("goals")
                    .whereEqualTo("isActive", true).get().await()
                val batch = firestore.batch()
                existing.documents.forEach { batch.update(it.reference, "isActive", false) }
                val newRef = userDoc(uid).collection("goals")
                    .document(goal.goalId.toString())
                batch.set(newRef, goal.copy(isActive = true).toMap())
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun completeGoal(goalId: Int): Boolean {
        return try {
            goalDao.markGoalAsCompleted(goalId)
            if (isCloud() && uid().isNotEmpty()) {
                userDoc(uid()).collection("goals")
                    .document(goalId.toString())
                    .update("isActive", false).await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateActiveGoalTarget(targetAmount: Double): Boolean {
        return try {
            goalDao.updateActiveGoalTarget(targetAmount)
            if (isCloud() && uid().isNotEmpty()) {
                val uid = uid()
                val snapshot = userDoc(uid).collection("goals")
                    .whereEqualTo("isActive", true).limit(1).get().await()
                snapshot.documents.firstOrNull()
                    ?.reference?.update("targetAmount", targetAmount)?.await()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==========================================
    // DELEGATED TO DATA SOURCE ROUTER
    // ==========================================

    fun getDailyNetSpent(startOfDay: Long, endOfDay: Long): Flow<Double?> =
        transactionDataSource.getDailyNetSpent(startOfDay, endOfDay)

    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?> =
        transactionDataSource.observeDailyExpenseSpent(startOfDay, endOfDay)

    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getExpenseSpentValue(startOfDay, endOfDay)

    suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getStreakRelevantSpent(startOfDay, endOfDay)

    suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getConfirmedTransactionCount(startOfDay, endOfDay)

    fun getAllTransactions(): Flow<List<TransactionEntity>> =
        transactionDataSource.getAllTransactions()

    fun getTransactionsPaged(): Flow<PagingData<TransactionEntity>> = Pager(
        config = PagingConfig(pageSize = 30, prefetchDistance = 10, enablePlaceholders = false),
        pagingSourceFactory = { TransactionPagingSource(transactionDao) }
    ).flow

    suspend fun getAllConfirmedTransactions(): List<TransactionEntity> =
        transactionDataSource.getAllConfirmedTransactions()

    suspend fun addTransaction(transaction: TransactionEntity): Boolean =
        transactionDataSource.addTransaction(transaction)

    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean =
        transactionDataSource.updateTransactionCategory(transactionId, newCategory)

    suspend fun updateTransaction(transaction: TransactionEntity): Boolean =
        transactionDataSource.updateTransaction(transaction)

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDataSource.getTransactionById(id)

    suspend fun deleteTransaction(userId: String, transactionId: Long) =
        transactionDataSource.deleteTransaction(userId, transactionId)

    suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getDiscretionarySpend(startOfDay, endOfDay)

    suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getWantSpend(startOfDay, endOfDay)

    suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getNeedSpend(startOfDay, endOfDay)

    suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend> =
        transactionDataSource.getCategoryBreakdown(startOfDay, endOfDay)

    suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int =
        transactionDataSource.getNormalExpenseSpentValue(startOfDay, endOfDay)

    fun getTransactionsFromCloud(uid: String): Flow<List<TransactionEntity>> =
        firestoreRepository.observeTransactions(uid)

    // ==========================================
    // ALWAYS-LOCAL METHODS
    // ==========================================

    suspend fun addPendingTransactionFromNotification(transaction: TransactionEntity) {
        transactionDao.upsertTransaction(transaction.copy(status = "PENDING"))
    }

    fun observePendingTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observePendingTransactions()

    suspend fun deletePendingTransaction(id: Long) {
        transactionDao.deletePendingTransaction(id)
    }

    suspend fun clearAllData() {
        transactionDao.deleteAllTransactions()
        goalDao.deleteAllGoals()
        userProfileDao.deleteAllUsers()
    }

    suspend fun suggestCategory(merchant: String, type: String): String {
        val prediction = merchantLearningEngine.predict(merchant)
        if (prediction != null) return prediction.category
        return categoryEngine.categorize(merchant, type)
    }

    suspend fun suggestTransactionDetails(merchant: String, type: String): Pair<String, Boolean> {
        val prediction = merchantLearningEngine.predict(merchant)
        val category = prediction?.category ?: categoryEngine.categorize(merchant, type)
        val history = transactionDao.getAllConfirmedTransactions()
        val recurring = recurringDetectionEngine.detect(merchant, 0.0, category, history)
        return Pair(category, recurring.showSuggestion)
    }

    suspend fun confirmAndUpdateTransaction(
        id:         Long,
        amount:     Double,
        merchant:   String,
        type:       String,
        category:   String = SpendingCategories.OTHER,
        txnCategory: String = com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
    ): Boolean {
        val existing = transactionDao.getTransactionById(id)
        val txn = if (existing == null) {
            TransactionEntity(
                transactionId = id, amount = amount, merchant = merchant, type = type,
                source = "Manual", date = System.currentTimeMillis(), status = "CONFIRMED",
                category = category, txnCategory = txnCategory
            )
        } else {
            existing.copy(amount = amount, merchant = merchant, type = type,
                status = "CONFIRMED", category = category, txnCategory = txnCategory)
        }
        return transactionDataSource.updateTransaction(txn)
    }

    // ==========================================
    // GOAL CONTRIBUTIONS
    // ==========================================

    fun getGoalContributions(goalId: Int): Flow<List<GoalContributionEntity>> =
        goalDao.getGoalContributions(goalId)

    suspend fun replaceGoalContributionForMonth(
        goalId: Int,
        amount: Double,
        timestamp: Long,
        startOfMonth: Long,
        endOfMonth: Long
    ): Boolean {
        goalDao.deleteGoalContributionForMonth(goalId, startOfMonth, endOfMonth)
        goalDao.insertGoalContribution(
            GoalContributionEntity(
                goalId = goalId,
                amount = amount,
                timestamp = System.currentTimeMillis()
            )
        )
        if (isCloud() && uid().isNotEmpty()) {
            val uid = uid()
            try {
                val goalDoc = userDoc(uid).collection("goals")
                    .document(goalId.toString())
                val contributionsRef = goalDoc.collection("contributions")
                val existing = contributionsRef
                    .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                    .whereLessThanOrEqualTo("timestamp", endOfMonth)
                    .get().await()
                existing.documents.forEach { it.reference.delete().await() }
                contributionsRef.add(
                    mapOf(
                        "goalId" to goalId,
                        "amount" to amount,
                        "timestamp" to System.currentTimeMillis()
                    )
                ).await()
            } catch (_: Exception) { }
        }
        return true
    }

    suspend fun deleteGoalContributionForMonth(
        goalId: Int,
        startOfMonth: Long,
        endOfMonth: Long
    ) {
        goalDao.deleteGoalContributionForMonth(goalId, startOfMonth, endOfMonth)
        if (isCloud() && uid().isNotEmpty()) {
            try {
                val contributionsRef = userDoc(uid()).collection("goals")
                    .document(goalId.toString()).collection("contributions")
                val existing = contributionsRef
                    .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                    .whereLessThanOrEqualTo("timestamp", endOfMonth)
                    .get().await()
                existing.documents.forEach { it.reference.delete().await() }
            } catch (_: Exception) { }
        }
    }

    // ==========================================
    // RECURRING DETECTION
    // ==========================================

    suspend fun detectRecurring(
        merchant: String,
        amount: Double,
        category: String
    ): RecurringDetectionEngine.DetectionResult {
        val history = transactionDao.getAllConfirmedTransactions()
        return recurringDetectionEngine.detect(merchant, amount, category, history)
    }

    suspend fun isMerchantRecurring(merchant: String): Boolean {
        val history = transactionDao.getAllConfirmedTransactions()
        val normalized = SpendingCategories.normalizeMerchant(merchant)
        val type = RecurringType.infer("", merchant)
        if (type != RecurringType.OTHER) return true
        val matches = history.filter {
            it.type == "Expense" && it.status == "CONFIRMED" &&
                SpendingCategories.normalizeMerchant(it.merchant).contains(normalized)
        }
        return matches.size >= 2
    }

    suspend fun getRecurringMerchants(): List<String> {
        return merchantLearningEngine.getRecurringMerchants()
    }

    suspend fun ensureMonthlyContribution(): Boolean {
        val activeGoal = goalDao.getActiveGoal().first() ?: return false
        val amount = preferencesRepository.monthlyCommitment.first().toDoubleOrNull() ?: return false
        if (amount <= 0.0) return false

        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.now()
        val startOfMonth = java.time.YearMonth.from(now)
            .atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfMonth = java.time.YearMonth.from(now)
            .plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val existing = goalDao.getGoalContributions(activeGoal.goalId).first()
        val alreadyContributed = existing.any { it.timestamp in startOfMonth..endOfMonth }
        if (alreadyContributed) return false

        return replaceGoalContributionForMonth(
            goalId = activeGoal.goalId,
            amount = amount,
            timestamp = System.currentTimeMillis(),
            startOfMonth = startOfMonth,
            endOfMonth = endOfMonth
        )
    }

    // ==========================================
    // PRIVATE HELPERS
    // ==========================================

    private fun Map<String, Any?>.toGoalEntity() = GoalEntity(
        goalId       = (this["goalId"]       as? Number)?.toInt() ?: 0,
        targetAmount = (this["targetAmount"] as? Number)?.toDouble() ?: 0.0,
        startDate    = (this["startDate"]    as? Number)?.toLong() ?: 0L,
        endDate      = (this["endDate"]      as? Number)?.toLong() ?: 0L,
        isActive     = (this["isActive"]     as? Boolean) ?: false
    )

    private fun Map<String, Any?>.toUserProfileEntity() = UserProfileEntity(
        uid                  = (this["uid"]                  as? String) ?: "",
        authProvider         = (this["authProvider"]         as? String) ?: "",
        emailOrPhone         = (this["emailOrPhone"]         as? String),
        storagePreference    = (this["storagePreference"]    as? String) ?: "local",
        currentBalance       = (this["currentBalance"]       as? Number)?.toDouble() ?: 0.0,
        emergencyFundBalance = (this["emergencyFundBalance"] as? Number)?.toDouble() ?: 0.0,
        currentStreak        = (this["currentStreak"]        as? Number)?.toInt() ?: 0
    )

    private suspend fun saveGoalToCloud(uid: String, goal: GoalEntity): Boolean {
        return try {
            userDoc(uid).collection("goals")
                .document(goal.goalId.toString())
                .set(goal.toMap())
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
