package com.anantva.tether.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.entity.ContributionSyncStatus
import com.anantva.tether.data.local.entity.GoalContributionEntity
import com.anantva.tether.data.local.dao.GoalDao
import com.anantva.tether.data.local.dao.TransactionDao
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncOrchestrator"

@Singleton
class SyncOrchestrator @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val tetherRepository: TetherRepository,
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lifecycleJob: Job? = null
    private var syncJob: Job? = null
    private var listenerJob: Job? = null
    private var started = false

    private val _syncState = MutableStateFlow<SyncResult>(SyncResult.Idle)
    val syncState: StateFlow<SyncResult> = _syncState.asStateFlow()

    fun start() {
        if (started) return
        started = true

        lifecycleJob = scope.launch {
            delay(5000)

            if (!isFirebaseAvailable()) return@launch
            val isCloud = safeFirst(preferencesRepository.isCloudStorage)
            if (!isCloud) return@launch

            observeSyncLifecycle()
        }
    }

    private suspend fun observeSyncLifecycle() {
        var currentUid: String? = null

        combine(
            preferencesRepository.isCloudStorage,
            authStateFlowSafe()
        ) { isCloud, uid -> isCloud to uid }
            .distinctUntilChanged()
            .collect { (isCloud, uid) ->
                if (isCloud && !uid.isNullOrEmpty()) {
                    if (uid != currentUid) {
                        currentUid = uid
                        teardown()
                        setupUserSync(uid)
                    }
                } else {
                    if (currentUid != null) {
                        currentUid = null
                        teardown()
                    }
                }
            }
    }

    private fun setupUserSync(uid: String) {
        Log.d(TAG, "Setting up sync for uid=$uid")

        listenerJob = scope.launch {
            supervisorScope {
                launch { observeTransactions(uid) }
                launch { observeGoals(uid) }
                launch { observePreferences(uid) }
                launch { observeGoalContributions(uid) }
            }
        }

        syncJob = scope.launch {
            Log.d(TAG, "Starting initial sync for uid=$uid")
            try {
                syncManager.syncAll(uid).collect { result ->
                    _syncState.value = result
                    when (result) {
                        is SyncResult.Syncing -> Log.d(TAG, "Syncing: ${result.message}")
                        is SyncResult.Done -> {
                            Log.d(TAG, "Sync complete: ${result.message}")
                            val retried = tetherRepository.retryPendingContributionSyncs()
                            if (retried > 0) {
                                Log.d(TAG, "Retried $retried pending contribution syncs after full sync")
                            }
                        }
                        is SyncResult.Error -> Log.e(TAG, "Sync error: ${result.message}")
                        else -> {}
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d(TAG, "Sync cancelled")
                _syncState.value = SyncResult.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _syncState.value = SyncResult.Error(e.message ?: "Unknown sync error")
            }
        }

        scope.launch {
            while (true) {
                delay(120_000)
                val retried = tetherRepository.retryPendingContributionSyncs()
                if (retried > 0) {
                    Log.d(TAG, "Periodic retry: synced $retried pending contributions")
                }
            }
        }
    }

    private suspend fun observeTransactions(uid: String) {
        try {
            syncManager.observeTransactionsLive(uid).collect { cloudTxns ->
                cloudTxns.forEach { txn ->
                    try {
                        val existing = transactionDao.getTransactionById(txn.transactionId)
                        if (existing == null || txn.date > existing.date) {
                            transactionDao.upsertTransaction(txn)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process transaction from cloud", e)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Transaction listener cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Transaction listener failed", e)
        }
    }

    private suspend fun observeGoals(uid: String) {
        try {
            syncManager.observeGoalsLive(uid).collect { goals ->
                goals.forEach { goal ->
                    try {
                        goalDao.upsertGoal(goal)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process goal from cloud", e)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Goal listener cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Goal listener failed", e)
        }
    }

    private suspend fun observeGoalContributions(uid: String) {
        try {
            val goals = goalDao.getAllGoals()
            for (goal in goals) {
                scope.launch {
                    try {
                        syncManager.observeGoalContributionsLive(uid, goal.goalId).collect { contributions ->
                            for (cloud in contributions) {
                                try {
                                    val range = contributionMonthRange(cloud.timestamp)
                                    val localContributions = goalDao.getGoalContributions(goal.goalId).first()
                                    val localForMonth = localContributions.filter {
                                        it.timestamp in range.first..range.second
                                    }

                                    if (localForMonth.isEmpty()) {
                                        goalDao.insertGoalContribution(
                                            cloud.copy(syncStatus = ContributionSyncStatus.SYNCED)
                                        )
                                        Log.d(TAG, "observeGoalContributions: inserted cloud contribution for goalId=${goal.goalId}")
                                    } else {
                                        val latestLocal = localForMonth.maxByOrNull { it.lastUpdated }!!
                                        if (cloud.lastUpdated > latestLocal.lastUpdated) {
                                            goalDao.deleteGoalContributionForMonth(goal.goalId, range.first, range.second)
                                            goalDao.insertGoalContribution(
                                                cloud.copy(syncStatus = ContributionSyncStatus.SYNCED)
                                            )
                                            Log.d(TAG, "observeGoalContributions: cloud newer, replaced local for goalId=${goal.goalId}")
                                        } else {
                                            Log.d(TAG, "observeGoalContributions: local newer or equal, keeping local for goalId=${goal.goalId}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to reconcile contribution for goalId=${goal.goalId}", e)
                                }
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Contribution listener cancelled for goalId=${goal.goalId}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Contribution listener failed for goalId=${goal.goalId}", e)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Goal contribution listener cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Goal contribution listener failed", e)
        }
    }

    private suspend fun observePreferences(uid: String) {
        try {
            syncManager.observePreferencesLive(uid).collect { prefs ->
                if (prefs != null) {
                    try {
                        val filtered = prefs - "isCloudStorage"
                        preferencesRepository.applyMap(filtered)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to apply preferences from cloud", e)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Preferences listener cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Preferences listener failed", e)
        }
    }

    private fun teardown() {
        syncJob?.cancel()
        syncJob = null
        listenerJob?.cancel()
        listenerJob = null
        _syncState.value = SyncResult.Idle
    }

    private suspend fun safeFirst(flow: Flow<Boolean>): Boolean = try {
        flow.first()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read initial preference", e)
        false
    }

    private fun isFirebaseAvailable(): Boolean = try {
        FirebaseApp.getInstance()
        true
    } catch (e: Exception) {
        Log.w(TAG, "Firebase not initialized: ${e.message}")
        false
    }

    private fun isNetworkAvailable(): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        Log.w(TAG, "Network check failed", e)
        true
    }

    private fun authStateFlowSafe(): Flow<String?> = callbackFlow {
        if (!isFirebaseAvailable()) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        try {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                trySend(auth.currentUser?.uid)
            }
            FirebaseAuth.getInstance().addAuthStateListener(listener)
            trySend(FirebaseAuth.getInstance().currentUser?.uid)
            awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up auth listener", e)
            trySend(null)
            awaitClose { }
        }
    }

    private fun contributionMonthRange(timestamp: Long): Pair<Long, Long> {
        val zone = java.time.ZoneId.systemDefault()
        val month = java.time.YearMonth.from(
            java.time.Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        )
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }
}
