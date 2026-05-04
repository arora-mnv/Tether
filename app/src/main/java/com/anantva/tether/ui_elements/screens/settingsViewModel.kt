package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    private val authManager: FirebaseAuthManager,
    private val userRepository: UserRepository
) : ViewModel() {

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

    fun setCloudStorage(enabled: Boolean, onAuthRequired: () -> Unit = {}) {
        viewModelScope.launch {
            if (enabled) {
                if (!authManager.isLoggedIn()) {
                    onAuthRequired()
                    return@launch
                }
                // Load current user data into UserRepository on cloud sync enable
                userRepository.loadCurrentUser()
            }
            preferencesRepository.setCloudStorageEnabled(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun resetAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            tetherRepository.clearAllData()
            preferencesRepository.resetAll()
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