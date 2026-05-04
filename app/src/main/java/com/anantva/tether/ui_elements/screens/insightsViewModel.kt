package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.insights.InsightsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
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
    val isLoading: Boolean = true
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val insightsEngine: InsightsEngine
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

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

    init {
        loadInsights()
    }

    fun refresh() {
        loadInsights()
    }

    private fun loadInsights() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val today = LocalDate.now()
                val daily = insightsEngine.getDailyInsight(today)
                val weekly = insightsEngine.getWeeklyInsight(today)

                _uiState.value = InsightsUiState(
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
                    isLoading = false
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfToday(): Long =
        startOfToday() + 86_400_000 - 1
}
