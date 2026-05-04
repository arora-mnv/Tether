package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.calculator.use_case.CalculateDailyLimitUseCase
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.FirestoreRepository
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean          = true,
    val currentBalance: Int         = 0,
    val savingsGoal: Int            = 0,
    val monthlyCommitment: Int      = 0,
    val streakDays: Int             = 0,
    val streakLevel: String         = "BRONZE",
    val streakMilestoneReached: Int = 0,
    val dailyLimit: Int             = 0,
    val dailySpent: Int             = 0,
    val dailyLimitRemaining: Int    = 0,
    val isOverLimit: Boolean        = false,
    val monthsToGoal: Int           = 0,
    val projectedCompletionDate: String = "",
    val goalProgressPct: Float = 0f,
    val goalRemainingAmount: Int = 0,
    val isGoalCompleted: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val tetherRepository: TetherRepository,
    private val calculateDailyLimit: CalculateDailyLimitUseCase,
    private val userRepository: com.anantva.tether.data.repository.UserRepository,
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    val user = userRepository.user

    private val today = LocalDate.now()
    private val zone  = ZoneId.systemDefault()
    private var completedGoalId: Int? = null

    private val startOfToday: Long =
        today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val endOfToday: Long =
        startOfToday + 24L * 60 * 60 * 1000 - 1

    init {
        checkAndUpdateStreak()
        viewModelScope.launch {
            firestoreRepository.testFirestoreWrite()
        }
    }

    private data class BaseInputs(
        val balance: Int,
        val goal: Int,
        val monthlyCommitment: Int,
        val hasSavedCommitment: Boolean,
        val streakDays: Int,
        val dailyExpenseSpent: Int
    )

    private val preferenceInputs =
        combine(
            preferencesRepository.currentBalance,
            preferencesRepository.savingsGoal,
            preferencesRepository.monthlyCommitment,
            preferencesRepository.hasSavedCommitment,
            preferencesRepository.streakDays
        ) { balanceStr, goalStr, commitmentStr, hasSavedCommitment, streakDays ->
            Triple(
                Triple(
                    balanceStr.toIntOrNull() ?: 0,
                    goalStr.toIntOrNull() ?: 0,
                    commitmentStr.toIntOrNull() ?: 0
                ),
                hasSavedCommitment,
                streakDays
            )
        }

    private val baseInputs =
        combine(
            preferenceInputs,
            tetherRepository.observeDailyExpenseSpent(startOfToday, endOfToday)
        ) { prefs, dailyExpenseSpentNullable ->
            val (numbers, hasSavedCommitment, streakDays) = prefs
            val (balance, goal, monthlyCommitment) = numbers
            BaseInputs(
                balance = balance,
                goal = goal,
                monthlyCommitment = monthlyCommitment,
                hasSavedCommitment = hasSavedCommitment,
                streakDays = streakDays,
                dailyExpenseSpent = dailyExpenseSpentNullable ?: 0
            )
        }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            baseInputs,
            tetherRepository.getActiveGoal()
        ) { base, activeGoal ->
            val dailyLimitResult = calculateDailyLimit(
                currentBalance = base.balance,
                monthlyCommitment = base.monthlyCommitment,
                spentToday = base.dailyExpenseSpent,
                currentDate = today
            )

            val monthsToGoal = if (base.monthlyCommitment > 0 && base.goal > 0) {
                (base.goal / base.monthlyCommitment).coerceAtLeast(1)
            } else 0

            val projectedDate = if (monthsToGoal > 0) {
                val completionMonth = YearMonth.now().plusMonths(monthsToGoal.toLong())
                "${completionMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${completionMonth.year}"
            } else ""

            val (progressPct, remaining, completed) =
                if (activeGoal != null && activeGoal.targetAmount > 0 && base.monthlyCommitment > 0) {
                    val start = Instant.ofEpochMilli(activeGoal.startDate).atZone(zone).toLocalDate()
                    val monthsElapsed = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(today)).coerceAtLeast(0)
                    val savedMonths = monthsElapsed + if (base.hasSavedCommitment) 1 else 0
                    val savedSoFar = savedMonths * base.monthlyCommitment
                    val remainingAmount = (activeGoal.targetAmount - savedSoFar).coerceAtLeast(0.0).toInt()
                    val pct = (savedSoFar.toFloat() / activeGoal.targetAmount.toFloat()).coerceIn(0f, 1f)
                    Triple(pct, remainingAmount, remainingAmount <= 0)
                } else {
                    Triple(0f, 0, false)
                }

            if (completed && activeGoal != null && completedGoalId != activeGoal.goalId) {
                completedGoalId = activeGoal.goalId
                viewModelScope.launch { tetherRepository.completeGoal(activeGoal.goalId) }
            }

            val streakLevel = when (base.streakDays) {
                in 0..6 -> "BRONZE"
                in 7..13 -> "SILVER"
                in 14..29 -> "GOLD"
                else -> "PLATINUM"
            }

            val milestone = when (base.streakDays) {
                3, 7, 14, 30 -> base.streakDays
                else -> 0
            }

            DashboardUiState(
                isLoading               = false,
                currentBalance          = base.balance,
                savingsGoal             = base.goal,
                monthlyCommitment       = base.monthlyCommitment,
                streakDays              = base.streakDays,
                streakLevel             = streakLevel,
                streakMilestoneReached  = milestone,
                dailyLimit              = dailyLimitResult.dailyLimit,
                dailySpent              = base.dailyExpenseSpent,
                dailyLimitRemaining     = dailyLimitResult.remainingToday,
                isOverLimit             = dailyLimitResult.exceeded,
                monthsToGoal            = monthsToGoal,
                projectedCompletionDate = projectedDate,
                goalProgressPct         = progressPct,
                goalRemainingAmount     = remaining,
                isGoalCompleted         = completed
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )

    private fun checkAndUpdateStreak() {
        viewModelScope.launch {
            val lastCheckEpochDay = preferencesRepository.lastStreakCheckDate.first()
            val todayEpochDay = today.toEpochDay()
            if (lastCheckEpochDay >= todayEpochDay) return@launch

            val currentStreak = preferencesRepository.streakDays.first()
            val balance = preferencesRepository.currentBalance.first().toIntOrNull() ?: 0
            val monthlyCommitment = preferencesRepository.monthlyCommitment.first().toIntOrNull() ?: 0
            val spentToday = tetherRepository.getStreakRelevantSpent(startOfToday, endOfToday)

            val dailyLimitResult = calculateDailyLimit(
                currentBalance = balance,
                monthlyCommitment = monthlyCommitment,
                spentToday = spentToday,
                currentDate = today
            )

            val newStreak = if (spentToday <= dailyLimitResult.dailyLimit) {
                currentStreak + 1
            } else {
                0
            }

            preferencesRepository.updateStreakAndCheckDate(newStreak, todayEpochDay)
        }
    }
}
