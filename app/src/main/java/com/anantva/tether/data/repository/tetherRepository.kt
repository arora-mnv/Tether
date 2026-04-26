package com.anantva.tether.data.repository

import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.dao.UserProfileDao
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.UserProfileEntity
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
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

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
        status        = (this["status"]        as? String) ?: "CONFIRMED"
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

    suspend fun addTransaction(transaction: TransactionEntity) {
        if (isCloud() && uid().isNotEmpty()) {
            saveTransactionToCloud(uid(), transaction)
        } else {
            transactionDao.insertTransaction(transaction)
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
        if (isCloud() && uid().isNotEmpty()) {
            saveTransactionToCloud(uid(), transaction)
        } else {
            transactionDao.updateTransaction(transaction)
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
        id:       Long,
        amount:   Double,
        merchant: String,
        type:     String
    ) {
        if (isCloud() && uid().isNotEmpty()) {
            val existing = getTransactionById(id) ?: return
            saveTransactionToCloud(
                uid(),
                existing.copy(amount = amount, merchant = merchant, type = type, status = "CONFIRMED")
            )
            // Also mark the local PENDING row as CONFIRMED so it disappears from the tray
            transactionDao.getTransactionById(id)?.let { local ->
                transactionDao.updateTransaction(
                    local.copy(amount = amount, merchant = merchant, type = type, status = "CONFIRMED")
                )
            }
        } else {
            val existing = transactionDao.getTransactionById(id) ?: return
            transactionDao.updateTransaction(
                existing.copy(amount = amount, merchant = merchant, type = type, status = "CONFIRMED")
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
        userDoc(uid).collection("transactions")
            .document(transaction.transactionId.toString())
            .set(transaction.toMap())
            .await()
    }

    private suspend fun saveGoalToCloud(uid: String, goal: GoalEntity) {
        userDoc(uid).collection("goals")
            .document(goal.goalId.toString())
            .set(goal.toMap())
            .await()
    }

    private fun getTransactionsFromCloud(uid: String): Flow<List<TransactionEntity>> = flow {
        val snapshot = userDoc(uid).collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .get().await()
        @Suppress("UNCHECKED_CAST")
        emit(snapshot.documents.mapNotNull {
            (it.data as? Map<String, Any?>)?.toTransactionEntity()
        })
    }
}
