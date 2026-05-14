package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.insights.InsightsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.anantva.tether.calculator.use_case.CalculateDailyLimitUseCase
import com.anantva.tether.data.local.UserPreferencesRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class InsightsUiState(
    val dailyTotalSpend: Int = 0,
    val dailyDiscretionary: Int = 0,
    val dailyNeedSpend: Int = 0,
    val dailyWantSpend: Int = 0,
    val dailyNeedWantRatio: Float = 0f,
    val dailyCategoryBreakdown: List<CategorySpend> = emptyList(),
    val dailyTopCategory: String = "No spending",
    val dailyTopCategoryAmount: Int = 0,
    val dailyTransactionCount: Int = 0,
    val dailyNormalTransactionCount: Int = 0,
    val dailyHealthScore: Float = 0f,
    val dailyInsightMessage: String = "",
    val weeklyTotalSpend: Int = 0,
    val weeklyAvgDailySpend: Int = 0,
    val weeklyPeakDay: String = "N/A",
    val weeklyPeakDayAmount: Int = 0,
    val weeklyCategoryBreakdown: List<CategorySpend> = emptyList(),
    val weeklyNeedVsWant: Pair<Int, Int> = 0 to 0,
    val weeklyDayByDaySpend: Map<String, Int> = emptyMap(),
    val weeklyTrendDirection: InsightsEngine.TrendDirection = InsightsEngine.TrendDirection.STABLE,
    val weeklyInsightMessage: String = "",
    val spendingPersonality: String = "",
    val personalityEmoji: String = "",
    val personalitySupporting: String = "",
    val observations: List<String> = emptyList(),
    val dailyMood: String = "",
    val isOverLimit: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val insightsEngine: InsightsEngine,
    private val preferencesRepository: UserPreferencesRepository,
    private val calculateDailyLimit: CalculateDailyLimitUseCase
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val dailyLimitFlow = tetherRepository.observeDailyExpenseSpent(
        startOfToday(),
        endOfToday()
    )

    val spendTrendValues: StateFlow<List<Int>> = combine(
        dailyLimitFlow,
        tetherRepository.getAllTransactions()
    ) { dailyExpenseNullable, allTransactions ->
        val dailyLimit = (dailyExpenseNullable ?: 0).coerceAtLeast(1)
        val today = LocalDate.now()
        val weekDays = (0..6).map { today.minusDays((today.dayOfWeek.value - 1 - it).toLong()) }.reversed()

        weekDays.map { date ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 86_400_000 - 1
            allTransactions
                .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.date in start..end }
                .sumOf { it.amount }
                .toInt()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf(0, 0, 0, 0, 0, 0, 0)
    )

    val trendLabels: List<String> = run {
        val today = LocalDate.now()
        (0..6).map { offset ->
            today.minusDays((today.dayOfWeek.value - 1 - (6 - offset)).toLong())
                .dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        tetherRepository.getAllTransactions(),
        preferencesRepository.currentBalance,
        preferencesRepository.monthlyCommitment
    ) { transactions, balanceStr, commitmentStr ->
        val balance = balanceStr.toIntOrNull() ?: 0
        val monthlyCommitment = commitmentStr.toIntOrNull() ?: 0

        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = startOfToday + 86_400_000 - 1

        val spentToday = transactions
            .filter { it.status == "CONFIRMED" && it.type == "Expense" && it.date in startOfToday..endOfToday && it.isStreakRelevant }
            .sumOf { it.amount }
            .toInt()

        val dailyLimitResult = calculateDailyLimit(
            currentBalance = balance,
            monthlyCommitment = monthlyCommitment,
            spentToday = spentToday,
            currentDate = today
        )

        val daily = insightsEngine.getDailyInsight(today, dailyLimitResult.exceeded)
        val weekly = insightsEngine.getWeeklyInsight(today)

        val (personality, emoji, supporting) = computePersonality(daily, weekly, dailyLimitResult.exceeded)
        val mood = computeDailyMood(daily, dailyLimitResult.exceeded)
        val obs = computeObservations(daily, weekly)

        InsightsUiState(
            dailyTotalSpend = daily.totalSpend,
            dailyDiscretionary = daily.discretionarySpend,
            dailyNeedSpend = daily.needSpend,
            dailyWantSpend = daily.wantSpend,
            dailyNeedWantRatio = daily.needWantRatio,
            dailyCategoryBreakdown = daily.categoryBreakdown,
            dailyTopCategory = daily.topCategory,
            dailyTopCategoryAmount = daily.topCategoryAmount,
            dailyTransactionCount = daily.transactionCount,
            dailyNormalTransactionCount = daily.normalTransactionCount,
            dailyHealthScore = daily.healthScore,
            dailyInsightMessage = daily.insightMessage,
            weeklyTotalSpend = weekly.totalWeekSpend,
            weeklyAvgDailySpend = weekly.avgDailySpend,
            weeklyPeakDay = weekly.peakDay,
            weeklyPeakDayAmount = weekly.peakDayAmount,
            weeklyCategoryBreakdown = weekly.categoryBreakdown,
            weeklyNeedVsWant = weekly.needVsWant,
            weeklyDayByDaySpend = weekly.dayByDaySpend,
            weeklyTrendDirection = weekly.trendDirection,
            weeklyInsightMessage = weekly.insightMessage,
            spendingPersonality = personality,
            personalityEmoji = emoji,
            personalitySupporting = supporting,
            observations = obs,
            dailyMood = mood,
            isOverLimit = dailyLimitResult.exceeded,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(isLoading = true)
    )

    fun refresh() {}

    private fun computePersonality(
        daily: InsightsEngine.DailyInsight,
        weekly: InsightsEngine.WeeklyInsight,
        isOverLimit: Boolean
    ): Triple<String, String, String> = when {
        daily.totalSpend == 0 -> Triple("Clean", "🧘", "Zero spend. Wallet is resting.")
        isOverLimit && daily.wantSpend > daily.needSpend -> Triple("Chaotic", "🌪️", "Overshot and mostly on wants.")
        isOverLimit -> Triple("Rebounding", "🔄", "Went over. Tomorrow is a reset.")
        daily.healthScore >= 0.85f -> Triple("Elite", "👑", "High discipline. Low noise.")
        daily.healthScore >= 0.7f -> Triple("Consistent", "⚡", "Steady spending. Streak-friendly.")
        daily.wantSpend == 0 && daily.needSpend > 0 -> Triple("Survival mode", "💪", "All essentials today.")
        daily.needWantRatio >= 3f -> Triple("Controlled", "🎯", "Needs far outweighed wants.")
        daily.needWantRatio >= 1.5f -> Triple("Balanced", "⚖️", "Healthy mix of needs and wants.")
        daily.wantSpend > daily.needSpend * 2 -> Triple("Impulsive", "🔥", "Wants doubled the needs today.")
        daily.discretionarySpend > daily.totalSpend * 0.6f -> Triple("Reactive", "💨", "Most of today was optional spend.")
        daily.healthScore >= 0.5f -> Triple("Steady", "🌱", "Nothing extreme. Clean enough.")
        else -> Triple("Aware", "👀", "Spending happened. First step is noticing.")
    }

    private fun computeDailyMood(
        daily: InsightsEngine.DailyInsight,
        isOverLimit: Boolean
    ): String = when {
        daily.totalSpend == 0 -> "clean"
        isOverLimit -> "bad"
        daily.healthScore >= 0.8f -> "great"
        daily.healthScore >= 0.6f -> "good"
        daily.healthScore >= 0.4f -> "mixed"
        else -> "bad"
    }

    private fun computeObservations(
        daily: InsightsEngine.DailyInsight,
        weekly: InsightsEngine.WeeklyInsight
    ): List<String> {
        val result = mutableListOf<String>()

        if (daily.topCategory != "No spending" && daily.topCategoryAmount > 0) {
            result.add("${daily.topCategory} led today's spending.")
        }
        when (weekly.trendDirection) {
            InsightsEngine.TrendDirection.UP -> result.add("Spending is trending upward this week.")
            InsightsEngine.TrendDirection.DOWN -> result.add("Spending has been declining this week.")
            InsightsEngine.TrendDirection.STABLE -> {}
        }
        if (daily.transactionCount >= 5) {
            result.add("${daily.transactionCount} transactions today.")
        }
        if (daily.discretionarySpend > 0 && daily.totalSpend > 0) {
            val pct = (daily.discretionarySpend.toFloat() / daily.totalSpend * 100).toInt()
            if (pct >= 50) {
                result.add("$pct% of today was optional spending.")
            }
        }
        if (weekly.dayByDaySpend.isNotEmpty()) {
            val weekendKeys = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            val weekdayTotal = weekly.dayByDaySpend
                .filterKeys { day ->
                    val dow = day.lowercase().replaceFirstChar { it.uppercase() }
                    try {
                        val dayRef = DayOfWeek.valueOf(dow.uppercase())
                        dayRef !in weekendKeys
                    } catch (_: Exception) { true }
                }
                .values
            val weekendTotal = weekly.dayByDaySpend
                .filterKeys { day ->
                    val dow = day.lowercase().replaceFirstChar { it.uppercase() }
                    try {
                        val dayRef = DayOfWeek.valueOf(dow.uppercase())
                        dayRef in weekendKeys
                    } catch (_: Exception) { false }
                }
                .values
            if (weekendTotal.isNotEmpty() && weekdayTotal.isNotEmpty()) {
                val wkAvg = weekendTotal.average().toInt()
                val wdAvg = weekdayTotal.average().toInt()
                if (wdAvg > 0 && wkAvg > wdAvg * 1.5) {
                    result.add("Weekend spending outpaces weekdays.")
                }
            }
        }
        if (daily.recurringTotal > 0) {
            result.add("Recurring payments contributed to today's spending.")
        }

        return result.take(3)
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfToday(): Long =
        startOfToday() + 86_400_000 - 1
}
