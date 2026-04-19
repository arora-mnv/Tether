package com.anantva.tether.ui_elements.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.GoalEntity
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlin.math.roundToInt
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val tetherRepository: TetherRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _currentBalance = MutableStateFlow("")
    val currentBalance: StateFlow<String> = _currentBalance.asStateFlow()

    private val _savingsGoal = MutableStateFlow("")
    val savingsGoal: StateFlow<String> = _savingsGoal.asStateFlow()

    // ✅ Replaces targetDateMillis
    private val _monthlyCommitment = MutableStateFlow(0.0)
    val monthlyCommitment: StateFlow<Double> = _monthlyCommitment.asStateFlow()

    private val _hasSavedCommitment = MutableStateFlow(false)
    val hasSavedCommitment: StateFlow<Boolean> = _hasSavedCommitment.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _isCloudStorage = MutableStateFlow(false)
    val isCloudStorage: StateFlow<Boolean> = _isCloudStorage.asStateFlow()

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete.asStateFlow()

    fun updateBalance(value: String)          { _currentBalance.value = value }
    fun updateSavingsGoal(value: String)      { _savingsGoal.value = value }
    fun updateMonthlyCommitment(value: Double){
        _monthlyCommitment.value = (value / 500.0).roundToInt() * 500.0
    }
    fun setHasSavedCommitment(value: Boolean) { _hasSavedCommitment.value = value }
    fun setAuthenticated(status: Boolean)     { _isAuthenticated.value = status }
    fun setStoragePreference(isCloud: Boolean){ _isCloudStorage.value = isCloud }

    fun nextStep() {
        when {
            _currentStep.value == 4 && !_isCloudStorage.value -> _currentStep.value = 6
            _currentStep.value < 6 -> _currentStep.value++
            else -> completeSetup()
        }
    }

    fun previousStep() {
        when {
            _currentStep.value == 6 && !_isCloudStorage.value -> _currentStep.value = 4
            _currentStep.value > 1 -> _currentStep.value--
        }
    }

    private fun completeSetup() {
        viewModelScope.launch {
            preferencesRepository.saveSetupDetails(
                balance           = _currentBalance.value,
                goal              = _savingsGoal.value,
                monthlyCommitment = _monthlyCommitment.value.roundToInt().toString(),
                hasSavedCommitment = _hasSavedCommitment.value,
                isCloud           = _isCloudStorage.value
            )

            val goalAmount = _savingsGoal.value.toDoubleOrNull() ?: 0.0
            val commitment = _monthlyCommitment.value
            if (goalAmount > 0.0 && commitment > 0.0) {
                val today = LocalDate.now()
                val zone = ZoneId.systemDefault()

                val monthsToGoal = (goalAmount / commitment).toLong().coerceAtLeast(1)
                val endDate = YearMonth.from(today).plusMonths(monthsToGoal)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

                val startDate = today
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

                tetherRepository.setActiveGoal(
                    GoalEntity(
                        targetAmount = goalAmount,
                        startDate = startDate,
                        endDate = endDate,
                        isActive = true
                    )
                )
            }

            _setupComplete.value = true
        }
    }
}
