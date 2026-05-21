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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
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

    private val zone = ZoneId.systemDefault()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val savedCurrentMonth = tetherRepository.getActiveGoal().flatMapLatest { goal ->
        if (goal == null || goal.goalId <= 0) {
            flowOf(false)
        } else {
            val range = monthRange(LocalDate.now())
            tetherRepository.getGoalContributions(goal.goalId).map { contributions ->
                contributions.any { it.timestamp in range.first..range.second }
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.savingsGoal,
        preferencesRepository.monthlyCommitment,
        preferencesRepository.isCloudStorage,
        savedCurrentMonth,
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
            val activeGoal = tetherRepository.getActiveGoal().first()
            val amount = preferencesRepository.monthlyCommitment.first().toDoubleOrNull() ?: 0.0
            val range = monthRange(LocalDate.now())
            if (activeGoal == null || activeGoal.goalId <= 0 || amount <= 0.0) {
                preferencesRepository.setHasSavedCommitment(value)
                return@launch
            }
            if (value) {
                tetherRepository.replaceGoalContributionForMonth(
                    goalId = activeGoal.goalId,
                    amount = amount,
                    timestamp = System.currentTimeMillis(),
                    startOfMonth = range.first,
                    endOfMonth = range.second
                )
            } else {
                tetherRepository.deleteGoalContributionForMonth(activeGoal.goalId, range.first, range.second)
            }
            preferencesRepository.setHasSavedCommitment(false)
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

    private fun monthRange(date: LocalDate): Pair<Long, Long> {
        val start = YearMonth.from(date)
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = YearMonth.from(date)
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1
        return start to end
    }
}
