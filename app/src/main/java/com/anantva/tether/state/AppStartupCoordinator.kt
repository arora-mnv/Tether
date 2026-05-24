package com.anantva.tether.state

import android.util.Log
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.dao.TransactionDao
import com.anantva.tether.data.repository.SyncOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppStartup"

data class StartupReadiness(
    val isPreferencesReady: Boolean = false,
    val isLocalDbReady: Boolean = false,
    val isAuthReady: Boolean = false,
    val isSyncInitialized: Boolean = false,
    val isDashboardCacheReady: Boolean = false
) {
    val isReady: Boolean
        get() = isPreferencesReady && isLocalDbReady && isAuthReady && isDashboardCacheReady
}

@Singleton
class AppStartupCoordinator @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val transactionDao: TransactionDao,
    private val authManager: FirebaseAuthManager,
    private val syncOrchestrator: SyncOrchestrator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _readiness = MutableStateFlow(StartupReadiness())
    val readiness: StateFlow<StartupReadiness> = _readiness.asStateFlow()

    private val splashStartedAt = System.currentTimeMillis()

    fun start() {
        scope.launch {
            val preferencesReady = async { warmPreferences() }
            val localDbReady = async { warmLocalDb() }
            val authReady = async { resolveAuth() }

            preferencesReady.await()
            localDbReady.await()
            authReady.await()

            _readiness.update { it.copy(isDashboardCacheReady = true) }

            val elapsed = System.currentTimeMillis() - splashStartedAt
            val minSplash = 1200L - elapsed
            if (minSplash > 0) kotlinx.coroutines.delay(minSplash)

            _readiness.update { it.copy(isSyncInitialized = true) }

            scope.launch {
                syncOrchestrator.start()
            }
        }
    }

    private suspend fun warmPreferences() {
        try {
            preferencesRepository.hasCompletedOnboarding.first()
            preferencesRepository.hasCompletedSetup.first()
            preferencesRepository.currentBalance.first()
            _readiness.update { it.copy(isPreferencesReady = true) }
            Log.d(TAG, "Preferences ready")
        } catch (e: Exception) {
            Log.e(TAG, "Preferences failed", e)
            _readiness.update { it.copy(isPreferencesReady = true) }
        }
    }

    private suspend fun warmLocalDb() {
        try {
            transactionDao.getAllConfirmedTransactions()
            _readiness.update { it.copy(isLocalDbReady = true) }
            Log.d(TAG, "Local DB ready")
        } catch (e: Exception) {
            Log.e(TAG, "Local DB failed", e)
            _readiness.update { it.copy(isLocalDbReady = true) }
        }
    }

    private suspend fun resolveAuth() {
        try {
            authManager.getCurrentUserId()
            _readiness.update { it.copy(isAuthReady = true) }
            Log.d(TAG, "Auth ready")
        } catch (e: Exception) {
            Log.e(TAG, "Auth failed", e)
            _readiness.update { it.copy(isAuthReady = true) }
        }
    }

    fun getLastSyncInitialized(): Boolean = _readiness.value.isSyncInitialized
}
