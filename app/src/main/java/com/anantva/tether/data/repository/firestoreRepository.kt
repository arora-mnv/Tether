package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.entity.TransactionEntity
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
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun userTransactionsRef(userId: String) =
        firestore.collection("users").document(userId).collection("transactions")

    suspend fun saveTransaction(userId: String, transaction: TransactionEntity) {
        userTransactionsRef(userId)
            .document(transaction.transactionId.toString())
            .set(transaction.toMap())
            .await()
    }

    suspend fun deleteTransaction(userId: String, transactionId: Long) {
        userTransactionsRef(userId)
            .document(transactionId.toString())
            .delete()
            .await()
    }

    suspend fun getTransactions(userId: String): List<TransactionEntity> {
        val snapshot = userTransactionsRef(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(TransactionEntity::class.java)
        }
    }

    fun observeTransactions(userId: String): Flow<List<TransactionEntity>> = callbackFlow {
        val registration = userTransactionsRef(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeTransactions error for uid=$userId: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(TransactionEntity::class.java)
                }.orEmpty()
                trySend(transactions)
            }
        awaitClose { registration.remove() }
    }

    suspend fun getTransactionsOrNull(userId: String): List<TransactionEntity>? {
        return try {
            getTransactions(userId)
        } catch (e: Exception) {
            Log.e(TAG, "getTransactionsOrNull error for uid=$userId: ${e.message}")
            null
        }
    }

    // ── Profile methods ──

    suspend fun saveUserProfile(userId: String, name: String, phoneNumber: String) {
        try {
            Log.d(TAG, "saveUserProfile uid=$userId name=$name")
            val profileDoc = mapOf(
                "name" to name,
                "phoneNumber" to phoneNumber,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(userId)
                .collection("profile").document("main")
                .set(profileDoc)
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile error for uid=$userId: ${e.message}", e)
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
                    phoneNumber = doc.getString("phoneNumber") ?: ""
                )
                Log.d(TAG, "getUserProfile uid=$userId found name=${data.name}")
                data
            } else {
                Log.d(TAG, "getUserProfile uid=$userId document does not exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUserProfile error for uid=$userId: ${e.message}", e)
            null
        }
    }

    suspend fun hasUserProfile(userId: String): Boolean {
        return getUserProfile(userId) != null
    }
}

data class UserProfileData(
    val name: String,
    val phoneNumber: String
)
