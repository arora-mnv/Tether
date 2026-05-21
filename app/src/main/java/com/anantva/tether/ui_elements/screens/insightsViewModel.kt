package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.behavior.BehaviorLearningEngine
import com.anantva.tether.insights.InsightsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.anantva.tether.calculator.use_case.CalculateDailyLimitUseCase
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.TxnCategory
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
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
    val isLoading: Boolean = true,
    val personalityTitle: String = "Reading your rhythm\u2026",
    val personalityDescription: String = "Learning your financial patterns over time.",
    val personalityWaveformSharpness: Float = 0f,
    val personalityWaveformSpeed: Float = 0.8f,
    val projected30DaySpend: Int = 0,
    val projected30DayDailyAverage: Int = 0,
    val projected30DayTopCategory: String = "No pattern",
    val projected30DayConfidence: Float = 0f
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val insightsEngine: InsightsEngine,
    private val preferencesRepository: UserPreferencesRepository,
    private val calculateDailyLimit: CalculateDailyLimitUseCase,
    private val behaviorEngine: BehaviorLearningEngine
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
        val weekDays = (0..6).map { today.with(DayOfWeek.MONDAY).plusDays(it.toLong()) }

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
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        (0..6).map { offset ->
            monday.plusDays(offset.toLong())
                .dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    val uiState: StateFlow<InsightsUiState> = combine(
        tetherRepository.getAllTransactions(),
        preferencesRepository.currentBalance,
        preferencesRepository.monthlyCommitment,
        preferencesRepository.streakDays
    ) { transactions, balanceStr, commitmentStr, streakDays ->
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

        val behaviorSnapshot = behaviorEngine.computeSnapshot(
            transactions = transactions,
            streakDays = streakDays,
            isOverLimit = dailyLimitResult.exceeded,
            spentToday = spentToday
        )
        val (personality, emoji, supporting) = personalityWithBehavior(
            daily, weekly, dailyLimitResult.exceeded, streakDays, behaviorSnapshot
        )
        val mood = computeDailyMood(daily, dailyLimitResult.exceeded, behaviorSnapshot)
        val obs = computeObservations(daily, weekly, dailyLimitResult.exceeded, streakDays)
        val prediction = computeThirtyDayPrediction(transactions)

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
            isLoading = false,
            personalityTitle = behaviorSnapshot.personalityProfile.title,
            personalityDescription = behaviorSnapshot.personalityProfile.description,
            personalityWaveformSharpness = behaviorSnapshot.personalityProfile.waveformSharpness,
            personalityWaveformSpeed = behaviorSnapshot.personalityProfile.waveformSpeed,
            projected30DaySpend = prediction.projectedSpend,
            projected30DayDailyAverage = prediction.dailyAverage,
            projected30DayTopCategory = prediction.topCategory,
            projected30DayConfidence = prediction.confidence
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(isLoading = true)
    )

    fun refresh() {}

    private fun personalityWithBehavior(
        daily: InsightsEngine.DailyInsight,
        weekly: InsightsEngine.WeeklyInsight,
        isOverLimit: Boolean,
        streakDays: Int,
        behavior: com.anantva.tether.behavior.BehaviorSnapshot
    ): Triple<String, String, String> {
        if (daily.totalSpend == 0) return Triple("Clean", "🧘", "Zero spend. Wallet is resting.")

        val behaviorPersonality = behavior.currentPersonality
        val mapping = behaviorPersonalityToDisplay(behaviorPersonality)
        if (mapping != null) return mapping

        return when {
            streakDays == 0 && isOverLimit && daily.totalSpend > 0 -> Triple("Slipped", "📉", "The streak reset today.")
            isOverLimit && daily.wantSpend > daily.needSpend -> Triple("Chaotic", "🌪️", "Overshot and mostly on wants.")
            isOverLimit -> Triple("Rebounding", "🔄", "Went over. Tomorrow is a reset.")
            else -> {
                val trend = behavior.behavioralTrend
                val msg = when (trend) {
                    "IMPROVING" -> "Spending patterns are trending better."
                    "WORSENING" -> "Keep an eye on the trend."
                    else -> "Patterns are stable."
                }
                Triple(behaviorPersonality, behaviorEmoji(behaviorPersonality), msg)
            }
        }
    }

    private fun behaviorPersonalityToDisplay(p: String): Triple<String, String, String>? = when (p) {
        "Disciplined" -> Triple("Disciplined", "👑", "Controlled spending. Consistent choices.")
        "Stable" -> Triple("Stable", "🌊", "Smooth spending rhythm. No spikes.")
        "Controlled" -> Triple("Controlled", "🎯", "Intentional spending. Needs prioritized.")
        "Balanced" -> Triple("Balanced", "⚖️", "Healthy mix of needs and wants.")
        "Coasting" -> Triple("Coasting", "💨", "Mostly optional spend today.")
        "Impulsive" -> Triple("Impulsive", "🔥", "Wants outpaced needs.")
        "Spiraling" -> Triple("Spiraling", "🌪️", "Spending patterns are scattering.")
        "Reactive" -> Triple("Reactive", "⚡", "Reacting rather than planning.")
        "Aware" -> Triple("Aware", "👀", "Noticing patterns. First step.")
        else -> null
    }

    private fun behaviorEmoji(p: String): String = when (p) {
        "Disciplined" -> "👑"
        "Stable" -> "🌊"
        "Controlled" -> "🎯"
        "Balanced" -> "⚖️"
        "Coasting" -> "💨"
        "Impulsive" -> "🔥"
        "Spiraling" -> "🌪️"
        "Reactive" -> "⚡"
        "Aware" -> "👀"
        else -> "🧠"
    }

    private fun computeDailyMood(
        daily: InsightsEngine.DailyInsight,
        isOverLimit: Boolean,
        behavior: com.anantva.tether.behavior.BehaviorSnapshot
    ): String = when {
        daily.totalSpend == 0 -> "clean"
        isOverLimit -> "bad"
        daily.healthScore >= 0.8f -> "great"
        daily.healthScore >= 0.6f -> "good"
        behavior.currentPersonality == "Spiraling" || behavior.currentPersonality == "Reactive" -> "bad"
        behavior.currentPersonality == "Impulsive" -> "mixed"
        behavior.impulseScore >= 0.6f -> "mixed"
        daily.healthScore >= 0.4f -> "mixed"
        else -> "bad"
    }

    private fun computeObservations(
        daily: InsightsEngine.DailyInsight,
        weekly: InsightsEngine.WeeklyInsight,
        isOverLimit: Boolean,
        streakDays: Int
    ): List<String> {
        val result = mutableListOf<String>()

        if (streakDays == 0 && isOverLimit) {
            val topCat = daily.topCategory
            if (topCat != "No spending" && topCat != "Other") {
                result.add("${topCat} pushed spending over the edge today.")
            } else {
                result.add("The streak slipped today.")
            }
        }

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

    private data class ThirtyDayPrediction(
        val projectedSpend: Int,
        val dailyAverage: Int,
        val topCategory: String,
        val confidence: Float
    )

    private fun computeThirtyDayPrediction(transactions: List<TransactionEntity>): ThirtyDayPrediction {
        val today = LocalDate.now()
        val start = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val recentExpenses = transactions.filter { txn ->
            txn.status == "CONFIRMED" &&
                txn.type == "Expense" &&
                txn.typedCategory == TxnCategory.NORMAL &&
                txn.date in start..end
        }
        if (recentExpenses.isEmpty()) {
            return ThirtyDayPrediction(0, 0, "No pattern", 0f)
        }

        val activeDays = recentExpenses
            .map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .distinct()
            .size
            .coerceAtLeast(1)
        val observedWindowDays = minOf(30, java.time.temporal.ChronoUnit.DAYS.between(
            recentExpenses.minOf { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() },
            today
        ).toInt() + 1).coerceAtLeast(activeDays)

        val dailyAverage = (recentExpenses.sumOf { it.amount } / observedWindowDays).roundToInt()
        val category = recentExpenses
            .groupBy { it.category.ifBlank { SpendingCategories.OTHER } }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
            ?.key ?: "No pattern"
        val confidence = when {
            activeDays >= 15 -> 0.9f
            activeDays >= 8 -> 0.7f
            activeDays >= 4 -> 0.45f
            else -> 0.25f
        }
        return ThirtyDayPrediction(
            projectedSpend = dailyAverage * 30,
            dailyAverage = dailyAverage,
            topCategory = category,
            confidence = confidence
        )
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfToday(): Long =
        startOfToday() + 86_400_000 - 1
}
