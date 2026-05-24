package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.entity.CategoryCorrectionEntity
import com.anantva.tether.data.local.entity.GoalContributionEntity
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.entity.MerchantPatternEntity
import com.anantva.tether.data.local.entity.TransactionEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TetherFirestore"

@Singleton
class FirestoreRepository @Inject constructor() {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private fun userTransactionsRef(userId: String) =
        firestore.collection("users").document(userId).collection("transactions")

    private fun userGoalsRef(userId: String) =
        firestore.collection("users").document(userId).collection("goals")

    private fun userPreferencesRef(userId: String) =
        firestore.collection("users").document(userId).collection("preferences")

    private fun userCategoryCorrectionsRef(userId: String) =
        firestore.collection("users").document(userId).collection("categoryCorrections")

    private fun userMerchantPatternsRef(userId: String) =
        firestore.collection("users").document(userId).collection("merchantPatterns")

    suspend fun saveTransaction(userId: String, transaction: TransactionEntity): Boolean {
        return try {
            Log.d("TetherTxn", "Saving transaction started")
            userTransactionsRef(userId)
                .document(transaction.transactionId.toString())
                .set(transaction.toMap())
                .await()
            Log.d("TetherTxn", "Transaction saved")
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveTransaction Firestore error for userId=$userId, txnId=${transaction.transactionId}", e)
            }
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveTransaction Firestore error for userId=$userId, txnId=${transaction.transactionId}", e)
            Log.e("TetherTxn", "Error: ${e.message}")
            false
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: Long): Boolean {
        return try {
            userTransactionsRef(userId)
                .document(transactionId.toString())
                .delete()
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "deleteTransaction Firestore error for userId=$userId, txnId=$transactionId", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "deleteTransaction Firestore error for userId=$userId, txnId=$transactionId", e)
            false
        }
    }

    suspend fun getTransactions(userId: String): List<TransactionEntity> {
        return try {
            val snapshot = userTransactionsRef(userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                runCatching { TransactionEntity.fromMap(doc.data ?: emptyMap()) }
                    .onFailure { e -> Log.e("FirestoreParse", "Failed to parse doc ${doc.id}", e) }
                    .getOrNull()
            }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getTransactions Firestore error for uid=$userId", e)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getTransactions Firestore error for uid=$userId", e)
            emptyList()
        }
    }

    fun observeTransactions(userId: String): Flow<List<TransactionEntity>> = callbackFlow {
        val registration = userTransactionsRef(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val msg = error.message ?: ""
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                        Log.e(TAG, "Firestore error: $msg")
                    } else {
                        Log.e(TAG, "observeTransactions error for uid=$userId: $msg", error)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { TransactionEntity.fromMap(doc.data ?: emptyMap()) }
                        .onFailure { e -> Log.e("FirestoreParse", "Failed to parse doc ${doc.id}", e) }
                        .getOrNull()
                }.orEmpty()
                trySend(transactions)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getTransactionsOrNull(userId: String): List<TransactionEntity>? {
        return try {
            getTransactions(userId)
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getTransactionsOrNull error for uid=$userId: ${e.message}")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "getTransactionsOrNull error for uid=$userId: ${e.message}")
            null
        }
    }

    // ── Goal methods ──

    suspend fun saveGoal(userId: String, goal: GoalEntity): Boolean {
        return try {
            Log.d("TetherGoal", "Saving goal started")
            userGoalsRef(userId)
                .document(goal.goalId.toString())
                .set(goal.toMap())
                .await()
            Log.d("TetherGoal", "Goal saved")
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveGoal Firestore error for userId=$userId, goalId=${goal.goalId}", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveGoal Firestore error for userId=$userId, goalId=${goal.goalId}", e)
            false
        }
    }

    suspend fun getGoals(userId: String): List<GoalEntity> {
        return try {
            val snapshot = userGoalsRef(userId)
                .orderBy("startDate", Query.Direction.DESCENDING)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                runCatching { GoalEntity.fromMap(doc.data ?: emptyMap()) }
                    .onFailure { e -> Log.e("FirestoreParse", "Failed to parse goal doc ${doc.id}", e) }
                    .getOrNull()
            }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getGoals Firestore error for uid=$userId", e)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getGoals Firestore error for uid=$userId", e)
            emptyList()
        }
    }

    fun observeGoals(userId: String): Flow<List<GoalEntity>> = callbackFlow {
        val registration = userGoalsRef(userId)
            .orderBy("startDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val msg = error.message ?: ""
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                        Log.e(TAG, "Firestore error: $msg")
                    } else {
                        Log.e(TAG, "observeGoals error for uid=$userId: $msg", error)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val goals = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { GoalEntity.fromMap(doc.data ?: emptyMap()) }
                        .onFailure { e -> Log.e("FirestoreParse", "Failed to parse goal doc ${doc.id}", e) }
                        .getOrNull()
                }.orEmpty()
                trySend(goals)
            }
        awaitClose { registration.remove() }
    }

    suspend fun deleteGoal(userId: String, goalId: String): Boolean {
        return try {
            userGoalsRef(userId)
                .document(goalId)
                .delete()
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "deleteGoal Firestore error for userId=$userId, goalId=$goalId", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "deleteGoal Firestore error for userId=$userId, goalId=$goalId", e)
            false
        }
    }

    suspend fun saveGoalContribution(userId: String, contribution: GoalContributionEntity): Boolean {
        return try {
            userGoalsRef(userId)
                .document(contribution.goalId.toString())
                .collection("contributions")
                .document(contribution.contributionId.toString())
                .set(contribution.toMap())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveGoalContribution error for userId=$userId goalId=${contribution.goalId}", e)
            false
        }
    }

    suspend fun getGoalContributions(userId: String, goalId: Int): List<GoalContributionEntity> {
        return try {
            userGoalsRef(userId)
                .document(goalId.toString())
                .collection("contributions")
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    runCatching { GoalContributionEntity.fromMap(doc.data ?: emptyMap()) }.getOrNull()
                }
        } catch (e: Exception) {
            Log.e(TAG, "getGoalContributions error for userId=$userId goalId=$goalId", e)
            emptyList()
        }
    }

    fun observeGoalContributions(userId: String, goalId: Int): Flow<List<GoalContributionEntity>> = callbackFlow {
        val registration = userGoalsRef(userId)
            .document(goalId.toString())
            .collection("contributions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val msg = error.message ?: ""
                    if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                        Log.e(TAG, "Firestore error: $msg")
                    } else {
                        Log.e(TAG, "observeGoalContributions error for uid=$userId goalId=$goalId: $msg", error)
                    }
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val contributions = snapshot?.documents?.mapNotNull { doc ->
                    runCatching { GoalContributionEntity.fromMap(doc.data ?: emptyMap()) }
                        .onFailure { e -> Log.e("FirestoreParse", "Failed to parse contribution doc ${doc.id}", e) }
                        .getOrNull()
                }.orEmpty()
                trySend(contributions)
            }
        awaitClose { registration.remove() }
    }

    // ── Preference methods (DataStore → Firestore map) ──

    suspend fun savePreferencesMap(userId: String, prefsMap: Map<String, Any>): Boolean {
        return try {
            userPreferencesRef(userId)
                .document("main")
                .set(prefsMap)
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "savePreferencesMap error for userId=$userId", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "savePreferencesMap error for userId=$userId", e)
            false
        }
    }

    suspend fun saveCategoryCorrection(userId: String, correction: CategoryCorrectionEntity): Boolean {
        return try {
            userCategoryCorrectionsRef(userId)
                .document(correction.merchantKey)
                .set(
                    mapOf(
                        "merchantKey" to correction.merchantKey,
                        "category" to correction.category
                    )
                )
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveCategoryCorrection error for userId=$userId, key=${correction.merchantKey}", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveCategoryCorrection error for userId=$userId, key=${correction.merchantKey}", e)
            false
        }
    }

    suspend fun getCategoryCorrections(userId: String): List<CategoryCorrectionEntity> {
        return try {
            userCategoryCorrectionsRef(userId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val merchantKey = doc.getString("merchantKey") ?: doc.id
                    val category = doc.getString("category") ?: return@mapNotNull null
                    CategoryCorrectionEntity(merchantKey = merchantKey, category = category)
                }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getCategoryCorrections error for userId=$userId", e)
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getCategoryCorrections error for userId=$userId", e)
            emptyList()
        }
    }

    suspend fun getPreferencesMap(userId: String): Map<String, Any>? {
        return try {
            val doc = userPreferencesRef(userId)
                .document("main")
                .get()
                .await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            null
        }
    }

    fun observePreferencesMap(userId: String): Flow<Map<String, Any>?> = callbackFlow {
        val registration = userPreferencesRef(userId)
            .document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observePreferencesMap error for uid=$userId", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.data)
            }
        awaitClose { registration.remove() }
    }

    suspend fun saveMerchantPattern(userId: String, pattern: MerchantPatternEntity): Boolean {
        return try {
            userMerchantPatternsRef(userId)
                .document(pattern.normalizedMerchant)
                .set(
                    mapOf(
                        "normalizedMerchant" to pattern.normalizedMerchant,
                        "category" to pattern.category,
                        "confidenceScore" to pattern.confidenceScore,
                        "usageCount" to pattern.usageCount,
                        "lastUsedTimestamp" to pattern.lastUsedTimestamp
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveMerchantPattern error for userId=$userId key=${pattern.normalizedMerchant}", e)
            false
        }
    }

    suspend fun getMerchantPatterns(userId: String): List<MerchantPatternEntity> {
        return try {
            userMerchantPatternsRef(userId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    MerchantPatternEntity(
                        normalizedMerchant = (data["normalizedMerchant"] as? String) ?: doc.id,
                        category = data["category"] as? String ?: return@mapNotNull null,
                        confidenceScore = (data["confidenceScore"] as? Number)?.toFloat() ?: 0.5f,
                        usageCount = (data["usageCount"] as? Number)?.toInt() ?: 1,
                        lastUsedTimestamp = (data["lastUsedTimestamp"] as? Number)?.toLong() ?: 0L
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "getMerchantPatterns error for userId=$userId", e)
            emptyList()
        }
    }

    suspend fun getUserProfileMap(userId: String): Map<String, Any>? {
        return try {
            val doc = firestore.collection("users").document(userId)
                .get()
                .await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfileMap(userId: String, profileMap: Map<String, Any>): Boolean {
        return try {
            firestore.collection("users").document(userId)
                .set(profileMap)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfileMap error for userId=$userId", e)
            false
        }
    }

    // ── Profile methods ──

    suspend fun saveUserProfile(
        userId: String,
        name: String,
        phoneNumber: String,
        photoUrl: String = ""
    ): Boolean {
        return try {
            Log.d(TAG, "saveUserProfile uid=$userId name=$name")
            val profileDoc = mapOf(
                "name" to name,
                "phoneNumber" to phoneNumber,
                "photoUrl" to photoUrl,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(userId)
                .collection("profile").document("main")
                .set(profileDoc)
                .await()
            true
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "saveUserProfile error for uid=$userId: ${e.message}", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile error for uid=$userId: ${e.message}", e)
            false
        }
    }

    suspend fun getUserProfile(userId: String): UserProfileData? {
        return try {
            val doc = firestore.collection("users").document(userId)
                .collection("profile").document("main")
                .get().await()
            if (doc.exists()) {
                val data = UserProfileData(
                    name = doc.getString("name") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    photoUrl = doc.getString("photoUrl") ?: ""
                )
                Log.d(TAG, "getUserProfile uid=$userId found name=${data.name}")
                data
            } else {
                Log.d(TAG, "getUserProfile uid=$userId document does not exist")
                null
            }
        } catch (e: FirebaseFirestoreException) {
            val msg = e.message ?: ""
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("offline", ignoreCase = true)) {
                Log.e(TAG, "Firestore error: $msg")
            } else {
                Log.e(TAG, "getUserProfile error for uid=$userId: ${e.message}", e)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile error for uid=$userId: ${e.message}", e)
            null
        }
    }

    suspend fun hasUserProfile(userId: String): Boolean {
        return getUserProfile(userId) != null
    }

    suspend fun deleteAllUserData(userId: String) {
        try {
            val userDoc = firestore.collection("users").document(userId)
            val txns = userTransactionsRef(userId).get().await()
            val batch1 = firestore.batch()
            txns.documents.forEach { batch1.delete(it.reference) }
            batch1.commit().await()

            val goals = userGoalsRef(userId).get().await()
            val batch2 = firestore.batch()
            goals.documents.forEach { goalDoc ->
                val contributions = goalDoc.reference.collection("contributions").get().await()
                contributions.documents.forEach { batch2.delete(it.reference) }
                batch2.delete(goalDoc.reference)
            }
            batch2.commit().await()

            val corrections = userDoc.collection("categoryCorrections").get().await()
            val batch3 = firestore.batch()
            corrections.documents.forEach { batch3.delete(it.reference) }
            batch3.commit().await()

            val merchantPatterns = userDoc.collection("merchantPatterns").get().await()
            val batch4 = firestore.batch()
            merchantPatterns.documents.forEach { batch4.delete(it.reference) }
            batch4.commit().await()

            userDoc.collection("preferences").document("main").delete().await()
            userDoc.collection("profile").document("main").delete().await()

            userDoc.delete().await()
            Log.d(TAG, "deleteAllUserData: success for userId=$userId")
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllUserData: error for userId=$userId", e)
        }
    }

    suspend fun testFirestoreWrite(): Boolean {
        return try {
            val data = mapOf(
                "message" to "hello",
                "time" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection("test").document("test_doc").set(data).await()
            Log.d("FirestoreTest", "Success")
            true
        } catch (e: Exception) {
            Log.e("FirestoreTest", "Error: ${e.message}", e)
            false
        }
    }
}

data class UserProfileData(
    val name: String,
    val phoneNumber: String,
    val photoUrl: String = ""
)
