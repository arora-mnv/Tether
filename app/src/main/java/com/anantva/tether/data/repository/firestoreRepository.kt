package com.anantva.tether.data.repository

import android.util.Log
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
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private fun userTransactionsRef(userId: String) =
        firestore.collection("users").document(userId).collection("transactions")

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

    // ── Profile methods ──

    suspend fun saveUserProfile(userId: String, name: String, phoneNumber: String): Boolean {
        return try {
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
                    phoneNumber = doc.getString("phoneNumber") ?: ""
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
    val phoneNumber: String
)
