package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.calculator.use_case.CalculateDailyLimitUseCase
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.entity.GoalContributionEntity
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil
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
    val isGoalCompleted: Boolean = false,
    val goalSavedAmount: Int = 0,
    val hasSavedCurrentMonth: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val tetherRepository: TetherRepository,
    private val calculateDailyLimit: CalculateDailyLimitUseCase,
    private val userRepository: com.anantva.tether.data.repository.UserRepository
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
        migrateLegacySavedCommitment()
        checkAndUpdateStreak()
        watchOverLimit()
        ensureMonthlyGoalContribution()
    }

    private data class BaseInputs(
        val balance: Int,
        val goal: Int,
        val monthlyCommitment: Int,
        val streakDays: Int,
        val dailyExpenseSpent: Int
    )

    private val preferenceInputs =
        combine(
            preferencesRepository.currentBalance,
            preferencesRepository.savingsGoal,
            preferencesRepository.monthlyCommitment,
            preferencesRepository.streakDays
        ) { balanceStr, goalStr, commitmentStr, streakDays ->
            Triple(
                Triple(
                    balanceStr.toIntOrNull() ?: 0,
                    goalStr.toIntOrNull() ?: 0,
                    commitmentStr.toIntOrNull() ?: 0
                ),
                streakDays,
                Unit
            )
        }

    private val baseInputs =
        combine(
            preferenceInputs,
            tetherRepository.observeDailyExpenseSpent(startOfToday, endOfToday)
        ) { prefs, dailyExpenseSpentNullable ->
            val (numbers, streakDays, _) = prefs
            val (balance, goal, monthlyCommitment) = numbers
            BaseInputs(
                balance = balance,
                goal = goal,
                monthlyCommitment = monthlyCommitment,
                streakDays = streakDays,
                dailyExpenseSpent = dailyExpenseSpentNullable ?: 0
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeGoalWithContributions =
        tetherRepository.getActiveGoal().flatMapLatest { activeGoal ->
            if (activeGoal == null || activeGoal.goalId <= 0) {
                flowOf(activeGoal to emptyList<GoalContributionEntity>())
            } else {
                tetherRepository.getGoalContributions(activeGoal.goalId).map { contributions ->
                    activeGoal to contributions
                }
            }
        }

    val uiState: StateFlow<DashboardUiState> =
        combine(
            baseInputs,
            activeGoalWithContributions
        ) { base, goalAndContributions ->
            val (activeGoal, contributions) = goalAndContributions
            val dailyLimitResult = calculateDailyLimit(
                currentBalance = base.balance,
                monthlyCommitment = base.monthlyCommitment,
                spentToday = base.dailyExpenseSpent,
                currentDate = today
            )

            val savedSoFar = contributions.sumOf { it.amount }
            val monthRange = monthRange(today)
            val hasSavedCurrentMonth = contributions.any {
                it.timestamp in monthRange.first..monthRange.second
            }

            val targetAmount = activeGoal?.targetAmount ?: base.goal.toDouble()
            val remaining = (targetAmount - savedSoFar).coerceAtLeast(0.0).toInt()
            val completed = activeGoal != null && targetAmount > 0.0 && remaining <= 0
            val progressPct = if (targetAmount > 0.0) {
                (savedSoFar.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val monthsToGoal = if (base.monthlyCommitment > 0 && remaining > 0) {
                ceil(remaining / base.monthlyCommitment.toDouble()).toInt().coerceAtLeast(1)
            } else {
                0
            }

            val projectedDate = if (monthsToGoal > 0) {
                val completionMonth = YearMonth.now().plusMonths(monthsToGoal.toLong())
                "${completionMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${completionMonth.year}"
            } else ""

            val completedGoal = activeGoal?.takeIf { completed }
            if (completedGoal != null && completedGoalId != completedGoal.goalId) {
                completedGoalId = completedGoal.goalId
                viewModelScope.launch { tetherRepository.completeGoal(completedGoal.goalId) }
            }

            val streakLevel = when {
                base.streakDays < 7 -> "BRONZE"
                base.streakDays < 21 -> "SILVER"
                base.streakDays < 60 -> "GOLD"
                base.streakDays < 120 -> "PURPLE"
                base.streakDays < 250 -> "DEEP GOLD"
                base.streakDays < 365 -> "ORANGE"
                else -> "RED"
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
                isGoalCompleted         = completed,
                goalSavedAmount         = savedSoFar.toInt(),
                hasSavedCurrentMonth    = hasSavedCurrentMonth
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )

    /**
     * Day-transition streak check: runs once per calendar day.
     * Evaluates YESTERDAY's spending — if within limit, increments streak;
     * if exceeded, resets to 0.
     *
     * Guarded by lastStreakUpdateDate (yyyy-MM-dd): skips entirely if today
     * has already been processed, regardless of recomposition or relaunch.
     */
    private fun checkAndUpdateStreak() {
        viewModelScope.launch {
            val todayDate = LocalDate.now()
            val lastUpdate = preferencesRepository.lastStreakUpdateDate.first()
            val todayStr = todayDate.toString()

            if (lastUpdate == todayStr) return@launch

            val todayEpochDay = todayDate.toEpochDay()
            val yesterday = todayDate.minusDays(1)
            val startOfYesterday = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfYesterday = startOfYesterday + 24L * 60 * 60 * 1000 - 1

            val currentStreak = preferencesRepository.streakDays.first()
            val balance = preferencesRepository.currentBalance.first().toIntOrNull() ?: 0
            val monthlyCommitment = preferencesRepository.monthlyCommitment.first().toIntOrNull() ?: 0
            val spentYesterday = tetherRepository.getStreakRelevantSpent(startOfYesterday, endOfYesterday)

            val dailyLimitResult = calculateDailyLimit(
                currentBalance = balance,
                monthlyCommitment = monthlyCommitment,
                spentToday = spentYesterday,
                currentDate = yesterday
            )

            val newStreak = if (spentYesterday <= dailyLimitResult.dailyLimit) {
                currentStreak + 1
            } else {
                0
            }

            preferencesRepository.updateStreakAndCheckDate(newStreak, todayEpochDay, todayStr)
        }
    }

    /**
     * Real-time over-limit watcher: breaks streak immediately when today's
     * spending exceeds the daily limit. Runs independently of day transitions.
     */
    private fun watchOverLimit() {
        viewModelScope.launch {
            combine(
                preferencesRepository.streakDays,
                preferencesRepository.currentBalance,
                preferencesRepository.monthlyCommitment,
                tetherRepository.observeDailyExpenseSpent(startOfToday, endOfToday)
            ) { streakDays, balanceStr, commitmentStr, spentToday ->

                val balance = balanceStr.toIntOrNull() ?: 0
                val monthlyCommitment = commitmentStr.toIntOrNull() ?: 0
                val spent = spentToday ?: 0

                val limitResult = calculateDailyLimit(
                    currentBalance = balance,
                    monthlyCommitment = monthlyCommitment,
                    spentToday = spent,
                    currentDate = today
                )

                Triple(streakDays, limitResult, balance to spent)
            }.collect { (streakDays, limitResult, _) ->
                if (streakDays > 0 && limitResult.exceeded) {
                    val todayEpochDay = today.toEpochDay()
                    val todayStr = today.toString()
                    preferencesRepository.updateStreakAndCheckDate(0, todayEpochDay, todayStr)
                }
            }
        }
    }

    private fun ensureMonthlyGoalContribution() {
        viewModelScope.launch {
            try {
                tetherRepository.ensureMonthlyContribution()
            } catch (_: Exception) { }
        }
    }

    fun markCurrentMonthSaved() {
        viewModelScope.launch {
            val activeGoal = tetherRepository.getActiveGoal().first() ?: return@launch
            val amount = preferencesRepository.monthlyCommitment.first().toDoubleOrNull() ?: return@launch
            val range = monthRange(today)
            tetherRepository.replaceGoalContributionForMonth(
                goalId = activeGoal.goalId,
                amount = amount,
                timestamp = System.currentTimeMillis(),
                startOfMonth = range.first,
                endOfMonth = range.second
            )
        }
    }

    fun undoCurrentMonthContribution() {
        viewModelScope.launch {
            val activeGoal = tetherRepository.getActiveGoal().first() ?: return@launch
            val range = monthRange(today)
            tetherRepository.deleteGoalContributionForMonth(activeGoal.goalId, range.first, range.second)
        }
    }

    private fun migrateLegacySavedCommitment() {
        viewModelScope.launch {
            if (!preferencesRepository.hasSavedCommitment.first()) return@launch
            val activeGoal = tetherRepository.getActiveGoal().first() ?: return@launch
            val amount = preferencesRepository.monthlyCommitment.first().toDoubleOrNull() ?: return@launch
            val range = monthRange(today)
            val alreadyMigrated = tetherRepository.getGoalContributions(activeGoal.goalId)
                .first()
                .any { it.timestamp in range.first..range.second }
            if (!alreadyMigrated && amount > 0.0) {
                tetherRepository.replaceGoalContributionForMonth(
                    goalId = activeGoal.goalId,
                    amount = amount,
                    timestamp = System.currentTimeMillis(),
                    startOfMonth = range.first,
                    endOfMonth = range.second
                )
            }
            preferencesRepository.setHasSavedCommitment(false)
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

    // DEBUG ONLY — REMOVE BEFORE RELEASE
    fun debugSetStreak(streak: Int) {
        viewModelScope.launch {
            preferencesRepository.setStreakDays(streak.coerceAtLeast(0))
        }
    }
}
