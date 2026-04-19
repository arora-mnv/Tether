package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.calculator.use_case.CalculateDailyLimitUseCase
import com.anantva.tether.data.local.UserPreferencesRepository
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
    val currentBalance: Double      = 0.0,
    val savingsGoal: Double         = 0.0,
    val monthlyCommitment: Double   = 0.0,
    val streakDays: Int             = 0,
    // Daily limit fields
    val dailyLimit: Double          = 0.0,
    val dailySpent: Double          = 0.0,
    val dailyLimitRemaining: Double = 0.0,
    val isOverLimit: Boolean        = false,
    // Projection fields
    val monthsToGoal: Int           = 0,
    val projectedCompletionDate: String = "",
    // Goal progress
    val goalProgressPct: Float = 0f,
    val goalRemainingAmount: Double = 0.0,
    val isGoalCompleted: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val tetherRepository: TetherRepository,
    private val calculateDailyLimit: CalculateDailyLimitUseCase
) : ViewModel() {

    private val today = LocalDate.now()
    private val zone  = ZoneId.systemDefault()
    private var completedGoalId: Int? = null

    private val startOfToday: Long =
        today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val endOfToday: Long =
        startOfToday + 24L * 60 * 60 * 1000 - 1

    init { checkAndUpdateStreak() }

    private data class BaseInputs(
        val balance: Double,
        val goal: Double,
        val monthlyCommitment: Double,
        val hasSavedCommitment: Boolean,
        val streakDays: Int,
        val dailyExpenseSpent: Double
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
                    balanceStr.toDoubleOrNull() ?: 0.0,
                    goalStr.toDoubleOrNull() ?: 0.0,
                    commitmentStr.toDoubleOrNull() ?: 0.0
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
                dailyExpenseSpent = dailyExpenseSpentNullable ?: 0.0
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
            dailyNetSpent = base.dailyExpenseSpent,
            date = today
        )

        // ── Goal Projection ───────────────────────────────────────────
        // Months to goal = Goal Amount / Monthly Commitment
        val monthsToGoal = if (base.monthlyCommitment > 0 && base.goal > 0) {
            (base.goal / base.monthlyCommitment).toInt().coerceAtLeast(1)
        } else 0

        val projectedDate = if (monthsToGoal > 0) {
            val completionMonth = YearMonth.now().plusMonths(monthsToGoal.toLong())
            "${completionMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${completionMonth.year}"
        } else ""

        val (progressPct, remaining, completed) =
            if (activeGoal != null && activeGoal.targetAmount > 0.0 && base.monthlyCommitment > 0.0) {
            val start = Instant.ofEpochMilli(activeGoal.startDate).atZone(zone).toLocalDate()
            val monthsElapsed = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(today)).coerceAtLeast(0)
            val savedMonths = monthsElapsed + if (base.hasSavedCommitment) 1 else 0
            val savedSoFar = savedMonths * base.monthlyCommitment
            val remainingAmount = (activeGoal.targetAmount - savedSoFar).coerceAtLeast(0.0)
            val pct = (savedSoFar / activeGoal.targetAmount).toFloat().coerceIn(0f, 1f)
            Triple(pct, remainingAmount, remainingAmount <= 0.0)
        } else {
            Triple(0f, 0.0, false)
        }

        if (completed && activeGoal != null && completedGoalId != activeGoal.goalId) {
            completedGoalId = activeGoal.goalId
            viewModelScope.launch { tetherRepository.completeGoal(activeGoal.goalId) }
        }

        DashboardUiState(
            isLoading               = false,
            currentBalance          = base.balance,
            savingsGoal             = base.goal,
            monthlyCommitment       = base.monthlyCommitment,
            streakDays              = base.streakDays,
            dailyLimit              = dailyLimitResult.dailyLimit,
            dailySpent              = dailyLimitResult.dailyNetSpent,
            dailyLimitRemaining     = dailyLimitResult.dailyLimitRemaining,
            isOverLimit             = dailyLimitResult.isOverLimit,
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

    // ─────────────────────────────────────────────
    // Streak — Option B
    // ─────────────────────────────────────────────
    private fun checkAndUpdateStreak() {
        viewModelScope.launch {
            val lastCheckEpochDay = preferencesRepository.lastStreakCheckDate.first()
            val todayEpochDay     = today.toEpochDay()
            if (lastCheckEpochDay >= todayEpochDay) return@launch

            val yesterday        = today.minusDays(1)
            val startOfYesterday = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfYesterday   = startOfToday - 1

            val countYesterday = tetherRepository.getConfirmedTransactionCount(
                startOfDay = startOfYesterday,
                endOfDay = endOfYesterday
            )
            val spentYesterday = tetherRepository.getExpenseSpentValue(
                startOfDay = startOfYesterday,
                endOfDay = endOfYesterday
            )

            val currentStreak = preferencesRepository.streakDays.first()

            val newStreak = if (countYesterday > 0) {
                val balance           = preferencesRepository.currentBalance.first().toDoubleOrNull() ?: 0.0
                val monthlyCommitment = preferencesRepository.monthlyCommitment.first().toDoubleOrNull() ?: 0.0
                val limitYesterday = calculateDailyLimit(
                    currentBalance = balance,
                    monthlyCommitment = monthlyCommitment,
                    dailyNetSpent = 0.0,
                    date = yesterday
                ).dailyLimit

                if (spentYesterday <= limitYesterday) currentStreak + 1 else 0
            } else {
                currentStreak
            }

            preferencesRepository.updateStreakAndCheckDate(newStreak, todayEpochDay)
        }
    }
}
