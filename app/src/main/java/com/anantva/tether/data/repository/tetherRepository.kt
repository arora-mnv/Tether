package com.anantva.tether.data.repository

import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.parser.CategoryEngine
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class TetherRepository(
    private val userProfileDao: UserProfileDao,
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val categoryCorrectionDao: CategoryCorrectionDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager,
    private val firestoreRepository: FirestoreRepository,
    private val categoryEngine: CategoryEngine
) {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    // ==========================================
    // ROUTING HELPERS
    // ==========================================

    private suspend fun isCloud(): Boolean =
        preferencesRepository.isCloudStorage.first()

    /** Returns the authenticated uid, or empty string if not signed in. */
    private fun uid(): String = authManager.getCurrentUserId().orEmpty()

    private fun userDoc(uid: String) =
        firestore.collection("users").document(uid)

    /** Safely parse a Firestore document map into a TransactionEntity. */
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

    /** Safely parse a Firestore document map into a GoalEntity. */
    private fun Map<String, Any?>.toGoalEntity() = GoalEntity(
        goalId       = (this["goalId"]       as? Number)?.toInt() ?: 0,
        targetAmount = (this["targetAmount"] as? Number)?.toDouble() ?: 0.0,
        startDate    = (this["startDate"]    as? Number)?.toLong() ?: 0L,
        endDate      = (this["endDate"]      as? Number)?.toLong() ?: 0L,
        isActive     = (this["isActive"]     as? Boolean) ?: false
    )

    /** Safely parse a Firestore document map into a UserProfileEntity. */
    private fun Map<String, Any?>.toUserProfileEntity() = UserProfileEntity(
        uid                  = (this["uid"]                  as? String) ?: "",
        authProvider         = (this["authProvider"]         as? String) ?: "",
        emailOrPhone         = (this["emailOrPhone"]         as? String),
        storagePreference    = (this["storagePreference"]    as? String) ?: "local",
        currentBalance       = (this["currentBalance"]       as? Number)?.toDouble() ?: 0.0,
        emergencyFundBalance = (this["emergencyFundBalance"] as? Number)?.toDouble() ?: 0.0,
        currentStreak        = (this["currentStreak"]        as? Number)?.toInt() ?: 0
    )

    // ==========================================
    // USER PROFILE METHODS
    // ==========================================

    fun getUserProfile(uid: String): Flow<UserProfileEntity?> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid.isNotEmpty()) {
                flow {
                    val doc = userDoc(uid).get().await()
                    @Suppress("UNCHECKED_CAST")
                    emit((doc.data as? Map<String, Any?>)?.toUserProfileEntity())
                }
            } else {
                userProfileDao.getUserProfile(uid)
            }
        }
    }

    suspend fun saveUserProfile(user: UserProfileEntity) {
        if (isCloud() && uid().isNotEmpty()) {
            userDoc(uid()).set(user.toMap()).await()
        } else {
            userProfileDao.insertOrUpdateUser(user)
        }
    }

    suspend fun updateStreak(uid: String, newStreak: Int) {
        if (isCloud() && uid.isNotEmpty()) {
            userDoc(uid).update("currentStreak", newStreak).await()
        } else {
            userProfileDao.updateStreak(uid, newStreak)
        }
    }

    suspend fun updateBalances(uid: String, currentBalance: Double, emergencyBalance: Double) {
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
    }

    // ==========================================
    // GOAL METHODS
    // ==========================================

    fun getActiveGoal(): Flow<GoalEntity?> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid().isNotEmpty()) {
                flow {
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
                }
            } else {
                goalDao.getActiveGoal()
            }
        }
    }

    suspend fun saveGoal(goal: GoalEntity) {
        if (isCloud() && uid().isNotEmpty()) {
            saveGoalToCloud(uid(), goal)
        } else {
            goalDao.insertGoal(goal)
        }
    }

    suspend fun setActiveGoal(goal: GoalEntity) {
        if (isCloud() && uid().isNotEmpty()) {
            val uid = uid()
            // Deactivate all existing goals in Firestore then write the new active one
            val existing = userDoc(uid).collection("goals")
                .whereEqualTo("isActive", true).get().await()
            val batch = firestore.batch()
            existing.documents.forEach { batch.update(it.reference, "isActive", false) }
            val newRef = userDoc(uid).collection("goals")
                .document(goal.goalId.toString())
            batch.set(newRef, goal.copy(isActive = true).toMap())
            batch.commit().await()
        } else {
            goalDao.deactivateAllGoals()
            goalDao.insertGoal(goal.copy(isActive = true))
        }
    }

    suspend fun completeGoal(goalId: Int) {
        if (isCloud() && uid().isNotEmpty()) {
            userDoc(uid()).collection("goals")
                .document(goalId.toString())
                .update("isActive", false).await()
        } else {
            goalDao.markGoalAsCompleted(goalId)
        }
    }

    suspend fun updateActiveGoalTarget(targetAmount: Double) {
        if (isCloud() && uid().isNotEmpty()) {
            val uid = uid()
            val snapshot = userDoc(uid).collection("goals")
                .whereEqualTo("isActive", true).limit(1).get().await()
            snapshot.documents.firstOrNull()
                ?.reference?.update("targetAmount", targetAmount)?.await()
        } else {
            goalDao.updateActiveGoalTarget(targetAmount)
        }
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
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid().isNotEmpty()) {
                flow {
                    val net = fetchDayTransactions(uid(), startOfDay, endOfDay)
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
            } else {
                transactionDao.getDailyNetSpent(startOfDay, endOfDay)
            }
        }
    }

    fun observeDailyExpenseSpent(startOfDay: Long, endOfDay: Long): Flow<Int?> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid().isNotEmpty()) {
                flow {
                    val total = fetchDayTransactions(uid(), startOfDay, endOfDay)
                        .filter { it.status == "CONFIRMED" && it.type == "Expense" }
                        .sumOf { it.amount }
                        .toInt()
                    emit(total)
                }
            } else {
                transactionDao.observeDailyExpenseSpent(startOfDay, endOfDay)
            }
        }
    }

    suspend fun getExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getExpenseSpentValue(startOfDay, endOfDay)
    }

    suspend fun getStreakRelevantSpent(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.isStreakRelevant }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getStreakRelevantSpent(startOfDay, endOfDay)
    }

    suspend fun getConfirmedTransactionCount(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .count { it.status == "CONFIRMED" }
        }
        return transactionDao.getConfirmedTransactionCount(startOfDay, endOfDay)
    }

    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return preferencesRepository.isCloudStorage.flatMapLatest { isCloudOn ->
            if (isCloudOn && uid().isNotEmpty()) {
                getTransactionsFromCloud(uid())
            } else {
                transactionDao.getAllTransactions()
            }
        }
    }

    suspend fun getAllConfirmedTransactions(): List<TransactionEntity> {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), 0, Long.MAX_VALUE)
                .filter { it.status == "CONFIRMED" }
        }
        return transactionDao.getAllConfirmedTransactions()
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        val category = categoryEngine.categorize(transaction.merchant)
        val txn = transaction.copy(category = category)
        transactionDao.insertTransaction(txn)
        if (isCloud() && uid().isNotEmpty()) {
            saveTransactionToCloud(uid(), txn)
        }
    }

    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String) {
        val txn = transactionDao.getTransactionById(transactionId) ?: return
        transactionDao.updateTransaction(txn.copy(category = newCategory))
        categoryEngine.saveCorrection(txn.merchant, newCategory)
        if (isCloud() && uid().isNotEmpty()) {
            saveTransactionToCloud(uid(), txn.copy(category = newCategory))
        }
    }

    /**
     * Notification-derived PENDING rows always stay local — they are transient
     * processing state and should not pollute the cloud store.
     */
    suspend fun addPendingTransactionFromNotification(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction.copy(status = "PENDING"))
    }

    /** Pending transactions are always local (notification-derived, transient). */
    fun observePendingTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observePendingTransactions()

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
        if (isCloud() && uid().isNotEmpty()) {
            saveTransactionToCloud(uid(), transaction)
        }
    }

    /** Pending rows live locally regardless of cloud sync setting. */
    suspend fun deletePendingTransaction(id: Long) {
        transactionDao.deletePendingTransaction(id)
    }

    suspend fun getTransactionById(id: Long): TransactionEntity? {
        if (isCloud() && uid().isNotEmpty()) {
            val doc = userDoc(uid()).collection("transactions")
                .document(id.toString()).get().await()
            @Suppress("UNCHECKED_CAST")
            return (doc.data as? Map<String, Any?>)?.toTransactionEntity()
        }
        return transactionDao.getTransactionById(id)
    }

    suspend fun confirmAndUpdateTransaction(
        id:         Long,
        amount:     Double,
        merchant:   String,
        type:       String,
        category:   String = SpendingCategories.OTHER,
        txnCategory: String = com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
    ) {
        if (isCloud() && uid().isNotEmpty()) {
            val existing = getTransactionById(id) ?: return
            saveTransactionToCloud(
                uid(),
                existing.copy(
                    amount = amount,
                    merchant = merchant,
                    type = type,
                    status = "CONFIRMED",
                    category = category,
                    txnCategory = txnCategory
                )
            )
            transactionDao.getTransactionById(id)?.let { local ->
                transactionDao.updateTransaction(
                    local.copy(
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        status = "CONFIRMED",
                        category = category,
                        txnCategory = txnCategory
                    )
                )
            }
        } else {
            val existing = transactionDao.getTransactionById(id) ?: return
            transactionDao.updateTransaction(
                existing.copy(
                    amount = amount,
                    merchant = merchant,
                    type = type,
                    status = "CONFIRMED",
                    category = category,
                    txnCategory = txnCategory
                )
            )
        }
    }

    // ==========================================
    // PRIVATE FIREBASE HELPERS
    // ==========================================

    private suspend fun fetchDayTransactions(
        uid: String,
        startOfDay: Long,
        endOfDay: Long
    ): List<TransactionEntity> {
        val snapshot = userDoc(uid).collection("transactions")
            .whereGreaterThanOrEqualTo("date", startOfDay)
            .whereLessThanOrEqualTo("date", endOfDay)
            .get().await()
        @Suppress("UNCHECKED_CAST")
        return snapshot.documents.mapNotNull {
            (it.data as? Map<String, Any?>)?.toTransactionEntity()
        }
    }

    private suspend fun saveTransactionToCloud(uid: String, transaction: TransactionEntity) {
        firestoreRepository.saveTransaction(uid, transaction)
    }

    private suspend fun saveGoalToCloud(uid: String, goal: GoalEntity) {
        userDoc(uid).collection("goals")
            .document(goal.goalId.toString())
            .set(goal.toMap())
            .await()
    }

    fun getTransactionsFromCloud(uid: String): Flow<List<TransactionEntity>> =
        firestoreRepository.observeTransactions(uid)

    /**
     * Sync local DB with Firestore on login.
     * Basic version: overwrite local with cloud data.
     * Returns true if cloud had data, false if local was pushed to cloud.
     */
    suspend fun syncLocalWithCloud(userId: String): Boolean {
        val cloudTransactions = firestoreRepository.getTransactionsOrNull(userId) ?: return false
        transactionDao.deleteAllConfirmedTransactions()
        if (cloudTransactions.isNotEmpty()) {
            cloudTransactions.forEach { transactionDao.insertTransaction(it) }
            return true
        } else {
            // Cloud empty → push local to cloud
            transactionDao.getAllConfirmedTransactions().forEach {
                firestoreRepository.saveTransaction(userId, it)
            }
            return false
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: Long) {
        if (isCloud() && userId.isNotEmpty()) {
            firestoreRepository.deleteTransaction(userId, transactionId)
        }
        transactionDao.deleteTransactionById(transactionId)
    }

    suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.isStreakRelevant }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getDiscretionarySpend(startOfDay, endOfDay)
    }

    suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.spendNature == "WANT" }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getWantSpend(startOfDay, endOfDay)
    }

    suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.spendNature == "NEED" }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getNeedSpend(startOfDay, endOfDay)
    }

    suspend fun getCategoryBreakdown(startOfDay: Long, endOfDay: Long): List<CategorySpend> {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.type == "Expense" }
                .groupBy { it.category }
                .map { (cat, txns) ->
                    CategorySpend(cat, txns.sumOf { it.amount }.toInt())
                }
                .sortedByDescending { it.total }
        }
        return transactionDao.getCategoryBreakdown(startOfDay, endOfDay)
    }

    suspend fun getNormalExpenseSpentValue(startOfDay: Long, endOfDay: Long): Int {
        if (isCloud() && uid().isNotEmpty()) {
            return fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" && it.isStreakRelevant }
                .sumOf { it.amount }
                .toInt()
        }
        return transactionDao.getNormalExpenseSpentValue(startOfDay, endOfDay)
    }
}
