package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.local.entity.TransactionEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncOrchestrator"

@Singleton
class SyncOrchestrator @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var listenerJob: Job? = null

    private val _syncState = MutableStateFlow<SyncResult>(SyncResult.Idle)
    val syncState: StateFlow<SyncResult> = _syncState.asStateFlow()

    fun start() {
        scope.launch {
            combine(
                preferencesRepository.isCloudStorage,
                authStateFlow()
            ) { isCloud, uid -> isCloud to uid }.collect { (isCloud, uid) ->
                syncJob?.cancel()
                listenerJob?.cancel()
                if (isCloud && !uid.isNullOrEmpty()) {
                    listenerJob = scope.launch {
                        Log.d(TAG, "Starting real-time Firestore listener for uid=$uid")
                        launch {
                            try {
                                syncManager.observeTransactionsLive(uid).collect { cloudTxns ->
                                    cloudTxns.forEach { txn ->
                                        val existing = transactionDao.getTransactionById(txn.transactionId)
                                        if (existing == null || txn.date > existing.date) {
                                            transactionDao.upsertTransaction(txn)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Transaction listener failed", e)
                            }
                        }
                        launch {
                            try {
                                syncManager.observeGoalsLive(uid).collect { goals ->
                                    goals.forEach { goal ->
                                        goalDao.upsertGoal(goal)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Goal listener failed", e)
                            }
                        }
                        launch {
                            try {
                                syncManager.observePreferencesLive(uid).collect { prefs ->
                                    if (prefs != null) {
                                        preferencesRepository.applyMap(prefs)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Preferences listener failed", e)
                            }
                        }
                    }
                    syncJob = scope.launch {
                        Log.d(TAG, "Starting sync for uid=$uid")
                        try {
                            syncManager.syncAll(uid).collect { result ->
                                _syncState.value = result
                                when (result) {
                                    is SyncResult.Syncing -> Log.d(TAG, "Syncing: ${result.message}")
                                    is SyncResult.Done -> Log.d(TAG, "Sync complete: ${result.message}")
                                    is SyncResult.Error -> Log.e(TAG, "Sync error: ${result.message}")
                                    else -> {}
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Sync failed", e)
                            _syncState.value = SyncResult.Error(e.message ?: "Unknown sync error")
                        }
                    }
                } else {
                    listenerJob?.cancel()
                    _syncState.value = SyncResult.Idle
                }
            }
        }
    }

    private fun authStateFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid)
        }
        FirebaseAuth.getInstance().addAuthStateListener(listener)
        trySend(FirebaseAuth.getInstance().currentUser?.uid)
        awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
    }
}
