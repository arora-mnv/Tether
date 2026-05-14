package com.anantva.tether.data.repository

import android.util.Log
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    private suspend fun confirmedTransactionsInRange(startOfDay: Long, endOfDay: Long): List<TransactionEntity> {
        return if (isCloud() && uid().isNotEmpty()) {
            fetchDayTransactions(uid(), startOfDay, endOfDay)
                .filter { it.status == "CONFIRMED" }
        } else {
            transactionDao.getConfirmedTransactionsInRange(startOfDay, endOfDay)
        }
    }

    private suspend fun resolveCategory(
        merchant: String,
        type: String,
        selectedCategory: String
    ): String {
        if (selectedCategory.isNotBlank() && selectedCategory != SpendingCategories.OTHER) {
            return selectedCategory
        }
        return categoryEngine.categorize(merchant, type)
    }

    private suspend fun enrichTransaction(transaction: TransactionEntity): TransactionEntity {
        val resolvedCategory = resolveCategory(
            merchant = transaction.merchant,
            type = transaction.type,
            selectedCategory = transaction.category
        )
        val spendNature = if (transaction.type == "Expense") {
            SpendingCategories.spendNatureFor(
                category = resolvedCategory,
                merchant = transaction.merchant,
                txnCategory = transaction.txnCategory
            ).toDbValue()
        } else {
            transaction.spendNature
        }
        return transaction.copy(
            category = resolvedCategory,
            spendNature = spendNature
        )
    }

    private suspend fun persistCategoryLearning(transaction: TransactionEntity) {
        if (transaction.type != "Expense") return

        val corrections = categoryEngine.saveCorrection(transaction.merchant, transaction.category)
        if (corrections.isEmpty() || !isCloud() || uid().isEmpty()) return

        corrections.forEach { correction ->
            firestoreRepository.saveCategoryCorrection(uid(), correction)
        }
    }

    private fun streakPenaltyAmount(transaction: TransactionEntity): Int {
        if (transaction.status != "CONFIRMED" || transaction.type != "Expense") return 0

        val penaltyWeight = SpendingCategories.streakPenaltyWeight(
            category = transaction.category,
            merchant = transaction.merchant,
            txnCategory = transaction.txnCategory
        )
        return (transaction.amount * penaltyWeight).roundToInt()
    }

    private fun spendNatureFor(transaction: TransactionEntity) =
        SpendingCategories.spendNatureFor(
            category = transaction.category,
            merchant = transaction.merchant,
            txnCategory = transaction.txnCategory
        )

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
            // Always save locally first
            goalDao.upsertGoal(goal)
            Log.d("TetherGoal", "Goal saved locally, goalId=${goal.goalId}")

            // Then push to cloud if sync is on
            if (isCloud() && uid().isNotEmpty()) {
                saveGoalToCloud(uid(), goal)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveGoal Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveGoal error", e)
            false
        }
    }

    suspend fun setActiveGoal(goal: GoalEntity): Boolean {
        return try {
            // Always save locally first
            goalDao.deactivateAllGoals()
            goalDao.upsertGoal(goal.copy(isActive = true))
            Log.d("TetherGoal", "Active goal set locally, goalId=${goal.goalId}")

            // Then push to cloud if sync is on
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
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "setActiveGoal Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "setActiveGoal error", e)
            false
        }
    }

    suspend fun completeGoal(goalId: Int): Boolean {
        return try {
            // Always save locally first
            goalDao.markGoalAsCompleted(goalId)
            Log.d("TetherGoal", "Goal completed locally, goalId=$goalId")

            // Then push to cloud if sync is on
            if (isCloud() && uid().isNotEmpty()) {
                userDoc(uid()).collection("goals")
                    .document(goalId.toString())
                    .update("isActive", false).await()
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "completeGoal Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "completeGoal error", e)
            false
        }
    }

    suspend fun updateActiveGoalTarget(targetAmount: Double): Boolean {
        return try {
            // Always save locally first
            goalDao.updateActiveGoalTarget(targetAmount)
            Log.d("TetherGoal", "Active goal target updated locally: $targetAmount")

            // Then push to cloud if sync is on
            if (isCloud() && uid().isNotEmpty()) {
                val uid = uid()
                val snapshot = userDoc(uid).collection("goals")
                    .whereEqualTo("isActive", true).limit(1).get().await()
                snapshot.documents.firstOrNull()
                    ?.reference?.update("targetAmount", targetAmount)?.await()
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "updateActiveGoalTarget Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "updateActiveGoalTarget error", e)
            false
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
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .sumOf(::streakPenaltyAmount)
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

    suspend fun addTransaction(transaction: TransactionEntity): Boolean {
        return try {
            val txn = enrichTransaction(transaction)
            transactionDao.upsertTransaction(txn)
            Log.d("TetherTxn", "Transaction upserted locally, txnId=${txn.transactionId}")
            persistCategoryLearning(txn)
            
            if (isCloud()) {
                val uid = uid()
                Log.d(TAG, "addTransaction: isCloud=true, uid='$uid'")
                if (uid.isNotEmpty()) {
                    Log.d("TetherTxn", "Saving transaction to cloud, uid=$uid, txnId=${txn.transactionId}")
                    val cloudSuccess = saveTransactionToCloud(uid, txn)
                    if (!cloudSuccess) {
                        Log.e("TetherTxn", "Error: Cloud save returned false")
                    }
                } else {
                    Log.e(TAG, "addTransaction: uid is empty, skipping cloud save")
                }
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "addTransaction Firestore error", e)
            }
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "addTransaction error", e)
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        }
    }

    suspend fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean {
        return try {
            val txn = transactionDao.getTransactionById(transactionId) ?: return false
            val updatedTxn = enrichTransaction(txn.copy(category = newCategory))
            transactionDao.updateTransaction(updatedTxn)
            persistCategoryLearning(updatedTxn)
            if (isCloud() && uid().isNotEmpty()) {
                saveTransactionToCloud(uid(), updatedTxn)
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "updateTransactionCategory Firestore error", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "updateTransactionCategory error", e)
            false
        }
    }

    /**
     * Notification-derived PENDING rows always stay local — they are transient
     * processing state and should not pollute the cloud store.
     */
    suspend fun addPendingTransactionFromNotification(transaction: TransactionEntity) {
        // Use upsert to handle both new and existing pending transactions
        transactionDao.upsertTransaction(transaction.copy(status = "PENDING"))
        Log.d("TetherTxn", "Pending transaction upserted, txnId=${transaction.transactionId}")
    }

    /** Pending transactions are always local (notification-derived, transient). */
    fun observePendingTransactions(): Flow<List<TransactionEntity>> =
        transactionDao.observePendingTransactions()

    suspend fun suggestCategory(merchant: String, type: String): String =
        categoryEngine.categorize(merchant, type)

    suspend fun suggestTransactionDetails(merchant: String, type: String): Pair<String, Boolean> {
        val latest = transactionDao.getLatestTransactionByMerchant(merchant)
        if (latest != null) {
            val isRecurring = latest.txnCategory == com.anantva.tether.data.local.entity.TxnCategory.RECURRING.toDbValue()
            return Pair(latest.category, isRecurring)
        }
        return Pair(categoryEngine.categorize(merchant, type), false)
    }

    suspend fun updateTransaction(transaction: TransactionEntity): Boolean {
        return try {
            val enrichedTransaction = enrichTransaction(transaction)
            transactionDao.upsertTransaction(enrichedTransaction)
            Log.d("TetherTxn", "Transaction upserted locally, txnId=${enrichedTransaction.transactionId}")
            persistCategoryLearning(enrichedTransaction)
            
            if (isCloud()) {
                val uid = uid()
                Log.d(TAG, "updateTransaction: isCloud=true, uid='$uid'")
                if (uid.isNotEmpty()) {
                    Log.d("TetherTxn", "Saving transaction to cloud, uid=$uid, txnId=${enrichedTransaction.transactionId}")
                    val cloudSuccess = saveTransactionToCloud(uid, enrichedTransaction)
                    Log.d("TetherTxn", "Cloud save result: $cloudSuccess")
                    if (!cloudSuccess) {
                        Log.e("TetherTxn", "Error: Cloud save returned false")
                    }
                } else {
                    Log.e(TAG, "updateTransaction: uid is empty, skipping cloud save")
                }
            }
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "updateTransaction Firestore error", e)
            }
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "updateTransaction error", e)
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        }
    }

    /** Pending rows live locally regardless of cloud sync setting. */
    suspend fun deletePendingTransaction(id: Long) {
        transactionDao.deletePendingTransaction(id)
    }

    suspend fun getTransactionById(id: Long): TransactionEntity? {
        return try {
            if (isCloud() && uid().isNotEmpty()) {
                val doc = userDoc(uid()).collection("transactions")
                    .document(id.toString()).get().await()
                @Suppress("UNCHECKED_CAST")
                (doc.data as? Map<String, Any?>)?.toTransactionEntity()
            } else {
                transactionDao.getTransactionById(id)
            }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getTransactionById Firestore error for id=$id", e)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "getTransactionById error for id=$id", e)
            null
        }
    }

    suspend fun confirmAndUpdateTransaction(
        id:         Long,
        amount:     Double,
        merchant:   String,
        type:       String,
        category:   String = SpendingCategories.OTHER,
        txnCategory: String = com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
    ): Boolean {
        Log.d("TetherTxn", "Confirming transaction locally, txnId=$id")
        val localSuccess = updateLocalTransaction(id, amount, merchant, type, category, txnCategory)
        
        if (!localSuccess) {
            Log.e("TetherTxn", "Error: Local DB update failed for txnId=$id")
            return false
        }
        
        Log.d("TetherTxn", "Local update successful, txnId=$id")
        val localTxn = transactionDao.getTransactionById(id) ?: return true
        persistCategoryLearning(localTxn)
        
        if (!isCloud()) {
            Log.d("TetherTxn", "Cloud sync disabled, skipping cloud save")
            return true
        }
        
        val uid = uid()
        if (uid.isEmpty()) {
            Log.e("TetherTxn", "User not logged in - skipping cloud save")
            return true // Local save succeeded, that's enough
        }
        
        Log.d(TAG, "confirmAndUpdateTransaction: isCloud=true, uid='$uid'")
        Log.d("TetherTxn", "Saving transaction to cloud, uid=$uid, txnId=$id")
        
        return try {
            val cloudSuccess = saveTransactionToCloud(uid, localTxn)
            if (!cloudSuccess) {
                Log.e("TetherTxn", "Firestore write failed: saveTransactionToCloud returned false")
                Log.e(TAG, "confirmAndUpdateTransaction: cloud save returned false for txnId=$id")
                return true
            }
            
            Log.d("TetherTxn", "Transaction saved successfully to cloud, txnId=$id")
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: "Unknown Firestore error"
            Log.e(TAG, "confirmAndUpdateTransaction Firestore error for txnId=$id: $msg", e)
            
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                Log.e("TetherTxn", "Error: Firestore permission denied - check Firestore rules")
            } else if (msg.contains("offline", ignoreCase = true)) {
                Log.e("TetherTxn", "Error: Device is offline")
            } else {
                Log.e("TetherTxn", "Error: $msg")
            }
            
            Log.d("TetherTxn", "Local save succeeded, ignoring cloud error")
            true
        } catch (e: Exception) {
            Log.e(TAG, "confirmAndUpdateTransaction error for txnId=$id", e)
            Log.e("TetherTxn", "Error: ${e.message ?: "Unknown error"}")
            
            true
        }
    }
    
    private suspend fun updateLocalTransaction(
        id:         Long,
        amount:     Double,
        merchant:   String,
        type:       String,
        category:   String,
        txnCategory: String
    ): Boolean {
        return try {
            val existing = transactionDao.getTransactionById(id)

            if (existing == null) {
                Log.d("TetherTxn", "Transaction not found locally, inserting new. txnId=$id")
                val newTxn = enrichTransaction(TransactionEntity(
                    transactionId = id,
                    amount = amount,
                    merchant = merchant,
                    type = type,
                    source = "Manual",
                    date = System.currentTimeMillis(),
                    status = "CONFIRMED",
                    category = category,
                    txnCategory = txnCategory
                ))
                transactionDao.upsertTransaction(newTxn)
                Log.d("TetherTxn", "Transaction inserted successfully, txnId=$id")
                true
            } else {
                val updatedTxn = enrichTransaction(
                    existing.copy(
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        status = "CONFIRMED",
                        category = category,
                        txnCategory = txnCategory
                    )
                )
                transactionDao.upsertTransaction(updatedTxn)
                Log.d("TetherTxn", "Transaction updated successfully, txnId=$id")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update local transaction, id=$id", e)
            Log.e("TetherTxn", "Error: Failed to update local transaction: ${e.message}")
            false
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
                Log.e(TAG, "fetchDayTransactions Firestore error for uid=$uid", e)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "fetchDayTransactions error for uid=$uid", e)
            emptyList()
        }
    }

    private suspend fun saveTransactionToCloud(uid: String, transaction: TransactionEntity): Boolean {
        return firestoreRepository.saveTransaction(uid, transaction)
    }

    private suspend fun saveGoalToCloud(uid: String, goal: GoalEntity): Boolean {
        return try {
            userDoc(uid).collection("goals")
                .document(goal.goalId.toString())
                .set(goal.toMap())
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveGoalToCloud Firestore error for uid=$uid", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveGoalToCloud error for uid=$uid", e)
            false
        }
    }

    fun getTransactionsFromCloud(uid: String): Flow<List<TransactionEntity>> =
        firestoreRepository.observeTransactions(uid)

    suspend fun deleteTransaction(userId: String, transactionId: Long) {
        if (isCloud() && userId.isNotEmpty()) {
            firestoreRepository.deleteTransaction(userId, transactionId)
        }
        transactionDao.deleteTransactionById(transactionId)
    }

    suspend fun getDiscretionarySpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { txn ->
                txn.type == "Expense" && SpendingCategories.isDiscretionarySpend(
                    category = txn.category,
                    merchant = txn.merchant,
                    txnCategory = txn.txnCategory
                )
            }
            .sumOf { it.amount }
            .toInt()
    }

    suspend fun getWantSpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && spendNatureFor(it) == com.anantva.tether.data.local.entity.SpendNature.WANT }
            .sumOf { it.amount }
            .toInt()
    }

    suspend fun getNeedSpend(startOfDay: Long, endOfDay: Long): Int {
        return confirmedTransactionsInRange(startOfDay, endOfDay)
            .filter { it.type == "Expense" && spendNatureFor(it) == com.anantva.tether.data.local.entity.SpendNature.NEED }
            .sumOf { it.amount }
            .toInt()
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
