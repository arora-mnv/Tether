package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.SyncManager
import com.anantva.tether.data.use_case.DeleteUserDataUseCase
import com.anantva.tether.data.repository.SyncResult
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val savingsGoal: String = "",
    val monthlyCommitment: String = "",
    val isCloudStorage: Boolean = false,
    val hasSavedCommitment: Boolean = false,
    val notificationsEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val tetherRepository: TetherRepository,
    private val syncManager: SyncManager,
    private val authManager: FirebaseAuthManager,
    private val userRepository: UserRepository,
    private val deleteUserDataUseCase: DeleteUserDataUseCase
) : ViewModel() {

    private val _syncState = MutableStateFlow<SyncResult?>(null)
    val syncState: StateFlow<SyncResult?> = _syncState

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.savingsGoal,
        preferencesRepository.monthlyCommitment,
        preferencesRepository.isCloudStorage,
        preferencesRepository.hasSavedCommitment,
        preferencesRepository.notificationsEnabled
    ) { goal, commitment, cloud, hasSavedCommitment, notifications ->
        SettingsUiState(
            savingsGoal = goal,
            monthlyCommitment = commitment,
            isCloudStorage = cloud,
            hasSavedCommitment = hasSavedCommitment,
            notificationsEnabled = notifications
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setMonthlyCommitment(value: String) {
        viewModelScope.launch {
            val sanitized = value.toDoubleOrNull()?.toInt()?.toString() ?: value
            preferencesRepository.updateMonthlyCommitment(sanitized)
        }
    }

    fun setSavingsGoal(value: String) {
        viewModelScope.launch {
            val sanitized = value.toDoubleOrNull()?.toInt()?.toString() ?: value
            preferencesRepository.updateSavingsGoal(sanitized)
            sanitized.toDoubleOrNull()?.let { tetherRepository.updateActiveGoalTarget(it) }
        }
    }

    fun setHasSavedCommitment(value: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHasSavedCommitment(value)
        }
    }

    /**
     * Called when the sync toggle is flipped.
     * If enabling sync: sign-in check → enable flag → immediately run full reconciliation.
     * Updates syncState for the UI to observe.
     */
    fun setCloudStorage(enabled: Boolean, onAuthRequired: () -> Unit = {}) {
        if (enabled) {
            if (!authManager.isLoggedIn()) {
                onAuthRequired()
                return
            }
            viewModelScope.launch {
                userRepository.loadCurrentUser()
            }
        }

        // Toggle the flag immediately
        viewModelScope.launch {
            preferencesRepository.setCloudStorageEnabled(enabled)
        }

        // If enabling, trigger immediate reconciliation
        if (enabled) {
            val uid = authManager.getCurrentUserId().orEmpty()
            viewModelScope.launch {
                _syncState.value = SyncResult.Syncing("Starting sync...")
                try {
                    syncManager.syncAll(uid).collect { result ->
                        _syncState.value = result
                    }
                } catch (e: Exception) {
                    _syncState.value = SyncResult.Error("Sync failed: ${e.message}")
                }
            }
        } else {
            _syncState.value = null
        }
    }

    fun clearSyncState() {
        _syncState.value = null
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            deleteUserDataUseCase()
            onDone()
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepository.clearUser()
            authManager.signOut()
            preferencesRepository.setCloudStorageEnabled(false)
            preferencesRepository.updateUserProfile(name = "", email = "", phone = "")
        }
    }
}