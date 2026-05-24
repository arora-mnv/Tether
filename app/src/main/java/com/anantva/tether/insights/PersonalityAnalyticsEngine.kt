package com.anantva.tether.insights

import com.anantva.tether.behavior.FinancialPersonalityEngine
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TxnCategory
import com.anantva.tether.data.repository.TetherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class PersonalityAnalytics(
    val personalityTitle: String = "",
    val personalityDescription: String = "",
    val categoryDistribution: List<CategorySpend> = emptyList(),
    val dominantTraits: List<String> = emptyList(),
    val recurringPatterns: List<String> = emptyList(),
    val strongestCategories: List<String> = emptyList(),
    val consistencyScore: Float = 0f,
    val emotionalSpendingScore: Float = 0f,
    val adaptiveScore: Float = 0f,
    val impulsiveScore: Float = 0f,
    val monthlyTrends: List<MonthlyTrend> = emptyList(),
    val isReady: Boolean = false,
    val totalTransactions: Int = 0,
    val totalSpend: Double = 0.0,
    val averageTransactionSize: Double = 0.0,
    val wantsRatio: Float = 0f,
    val needsRatio: Float = 0f,
    val peakSpendingDay: String = "",
    val lateNightTransactionRatio: Float = 0f,
    val weekendSpendRatio: Float = 0f
)

data class MonthlyTrend(
    val month: String,
    val totalSpend: Double,
    val transactionCount: Int,
    val dominantCategory: String
)

@Singleton
class PersonalityAnalyticsEngine @Inject constructor(
    private val personalityEngine: FinancialPersonalityEngine,
    private val tetherRepository: TetherRepository
) {
    private val zone = ZoneId.systemDefault()

    private val _cachedAnalytics = MutableStateFlow(PersonalityAnalytics())
    val cachedAnalytics: StateFlow<PersonalityAnalytics> = _cachedAnalytics.asStateFlow()

    private var lastTransactionCount: Int = -1
    private var lastHash: Int = 0

    suspend fun getOrCompute(transactions: List<TransactionEntity>, forceRefresh: Boolean = false): PersonalityAnalytics {
        val currentHash = transactions.hashCode()
        if (!forceRefresh && lastTransactionCount == transactions.size && lastHash == currentHash) {
            return _cachedAnalytics.value
        }

        val analytics = compute(transactions)
        lastTransactionCount = transactions.size
        lastHash = currentHash

        val mutableFlow = _cachedAnalytics as MutableStateFlow
        mutableFlow.value = analytics
        return analytics
    }

    private suspend fun compute(transactions: List<TransactionEntity>): PersonalityAnalytics {
        val confirmed = transactions.filter { it.status == "CONFIRMED" }
        val expenses = confirmed.filter { it.type == "Expense" }
        val streakDays = 0
        val isOverLimit = false

        val personality = personalityEngine.compute(confirmed, streakDays, isOverLimit)

        val allTimeRange = allTimeCategoryBreakdown(expenses)
        val dominantTraits = computeDominantTraits(expenses)
        val recurring = detectRecurringPatterns(expenses)
        val strongest = allTimeRange.take(3).map { it.category }
        val consistency = computeConsistencyScore(expenses)
        val emotionalScore = computeEmotionalSpendingScore(expenses)
        val adaptiveImpulsive = computeAdaptiveVsImpulsive(expenses)
        val trends = computeMonthlyTrends(expenses)

        val totalSpend = expenses.sumOf { it.amount }
        val avgSize = if (expenses.isNotEmpty()) totalSpend / expenses.size else 0.0
        val wantsTotal = expenses.filter { it.spendNature == "WANT" }.sumOf { it.amount }
        val needsTotal = expenses.filter { it.spendNature == "NEED" }.sumOf { it.amount }
        val wantsRatio = if (totalSpend > 0) (wantsTotal / totalSpend).toFloat() else 0f
        val needsRatio = if (totalSpend > 0) (needsTotal / totalSpend).toFloat() else 0f

        val peakDay = findPeakSpendingDay(expenses)
        val lateNightRatio = computeLateNightRatio(expenses)
        val weekendRatio = computeWeekendSpendRatio(expenses)

        return PersonalityAnalytics(
            personalityTitle = personality.title,
            personalityDescription = personality.description,
            categoryDistribution = allTimeRange,
            dominantTraits = dominantTraits,
            recurringPatterns = recurring,
            strongestCategories = strongest,
            consistencyScore = consistency,
            emotionalSpendingScore = emotionalScore,
            adaptiveScore = adaptiveImpulsive.first,
            impulsiveScore = adaptiveImpulsive.second,
            monthlyTrends = trends,
            isReady = personality.isReady,
            totalTransactions = expenses.size,
            totalSpend = totalSpend,
            averageTransactionSize = avgSize,
            wantsRatio = wantsRatio,
            needsRatio = needsRatio,
            peakSpendingDay = peakDay,
            lateNightTransactionRatio = lateNightRatio,
            weekendSpendRatio = weekendRatio
        )
    }

    private suspend fun allTimeCategoryBreakdown(expenses: List<TransactionEntity>): List<CategorySpend> {
        return expenses.groupBy { it.category.ifBlank { SpendingCategories.OTHER } }
            .map { (cat, txns) -> CategorySpend(cat, txns.sumOf { it.amount }.toInt()) }
            .sortedByDescending { it.total }
    }

    private fun computeDominantTraits(expenses: List<TransactionEntity>): List<String> {
        val traits = mutableListOf<String>()
        val totalSpend = expenses.sumOf { it.amount }
        if (totalSpend <= 0) return listOf("Insufficient data")

        val wantsTotal = expenses.filter { it.spendNature == "WANT" }.sumOf { it.amount }
        val wantsRatio = (wantsTotal / totalSpend).toFloat()

        val dayCount = expenses.map {
            Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.distinct().size
        val txPerDay = if (dayCount > 0) expenses.size.toFloat() / dayCount else 0f

        val lateNightCount = expenses.count {
            val hour = Instant.ofEpochMilli(it.date).atZone(zone).hour
            hour >= 21 || hour <= 5
        }
        val lateNightRatio = if (expenses.isNotEmpty()) lateNightCount.toFloat() / expenses.size else 0f

        if (wantsRatio > 0.6f) traits.add("Want-driven spending")
        else if (wantsRatio < 0.3f) traits.add("Need-focused spending")
        else traits.add("Balanced spender")

        if (txPerDay > 3f) traits.add("High frequency")
        else if (txPerDay < 0.5f) traits.add("Low frequency")
        else traits.add("Moderate frequency")

        if (lateNightRatio > 0.3f) traits.add("Emotional/Evening spender")
        else if (lateNightRatio < 0.1f) traits.add("Daytime spender")
        else traits.add("Mixed timing")

        val weekendCount = expenses.count {
            val day = Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }
        val weekendRatio = if (expenses.isNotEmpty()) weekendCount.toFloat() / expenses.size else 0f
        if (weekendRatio > 0.4f) traits.add("Weekend-heavy")

        return traits
    }

    private fun detectRecurringPatterns(expenses: List<TransactionEntity>): List<String> {
        val merchantGroups = expenses.groupBy { SpendingCategories.normalizeMerchant(it.merchant) }
        val patterns = mutableListOf<String>()

        merchantGroups.forEach { (merchant, txns) ->
            if (txns.size >= 3 && originalMerchant(merchant) != "") {
                val avgGap = txns.zipWithNext { a, b ->
                    kotlin.math.abs(a.date - b.date)
                }.average()
                val gapDays = avgGap / 86400000.0
                when {
                    gapDays in 25.0..32.0 -> patterns.add("Monthly: ${txns.first().merchant}")
                    gapDays in 6.0..8.0 -> patterns.add("Weekly: ${txns.first().merchant}")
                    gapDays in 13.0..16.0 -> patterns.add("Biweekly: ${txns.first().merchant}")
                }
            }
        }

        return patterns.take(5)
    }

    private fun originalMerchant(normalized: String): String = normalized

    private fun computeConsistencyScore(expenses: List<TransactionEntity>): Float {
        val dailyAmounts = expenses.groupBy {
            Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.map { (_, txns) -> txns.sumOf { it.amount }.toFloat() }

        if (dailyAmounts.size < 5) return 0.5f

        val mean = dailyAmounts.average().toFloat().coerceAtLeast(1f)
        val variance = dailyAmounts.map { (it - mean) * (it - mean) }.sum() / dailyAmounts.size
        val cv = kotlin.math.sqrt(variance.toDouble()).toFloat() / mean

        val streakLike = expenses.filter { it.typedCategory == TxnCategory.NORMAL }
            .map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()

        val gaps = streakLike.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
        val consistency = 1f - (cv.coerceIn(0f, 1f) * 0.7f + (if (gaps.isNotEmpty()) gaps.average().toFloat().coerceIn(0f, 30f) / 30f * 0.3f else 0f))
        return consistency.coerceIn(0f, 1f)
    }

    private fun computeEmotionalSpendingScore(expenses: List<TransactionEntity>): Float {
        val lateNight = expenses.count {
            val hour = Instant.ofEpochMilli(it.date).atZone(zone).hour
            hour >= 21 || hour <= 5
        }
        val weekend = expenses.count {
            val day = Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }

        val wants = expenses.filter { it.spendNature == "WANT" }.sumOf { it.amount }
        val total = expenses.sumOf { it.amount }.coerceAtLeast(1.0)

        val lateNightRatio = if (expenses.isNotEmpty()) lateNight.toFloat() / expenses.size else 0f
        val weekendRatio = if (expenses.isNotEmpty()) weekend.toFloat() / expenses.size else 0f
        val wantsRatio = (wants / total).toFloat()

        return (lateNightRatio * 0.4f + weekendRatio * 0.3f + wantsRatio * 0.3f).coerceIn(0f, 1f)
    }

    private fun computeAdaptiveVsImpulsive(expenses: List<TransactionEntity>): Pair<Float, Float> {
        val dailyGroups = expenses.groupBy {
            Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }

        val impulseDays = dailyGroups.count { (_, txns) ->
            txns.any { it.spendNature == "WANT" } && txns.size >= 3
        }

        val adaptiveDays = dailyGroups.count { (_, txns) ->
            txns.all { it.spendNature == "NEED" || it.spendNature == "UNKNOWN" }
        }

        val totalDays = dailyGroups.size.coerceAtLeast(1)
        return Pair(
            adaptiveDays.toFloat() / totalDays,
            impulseDays.toFloat() / totalDays
        )
    }

    private fun computeMonthlyTrends(expenses: List<TransactionEntity>): List<MonthlyTrend> {
        val groupedByMonth = expenses.groupBy {
            val date = Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
            "${date.year}-${date.monthValue}"
        }

        return groupedByMonth.map { (month, txns) ->
            val categoryGroups = txns.groupBy { it.category.ifBlank { SpendingCategories.OTHER } }
            val dominantCat = categoryGroups.maxByOrNull { (_, t) -> t.sumOf { it.amount } }?.key ?: ""
            MonthlyTrend(
                month = month,
                totalSpend = txns.sumOf { it.amount },
                transactionCount = txns.size,
                dominantCategory = dominantCat
            )
        }.sortedBy { it.month }.takeLast(12)
    }

    private fun findPeakSpendingDay(expenses: List<TransactionEntity>): String {
        val dayTotals = expenses.groupBy {
            Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
        }.mapValues { (_, txns) -> txns.sumOf { it.amount } }

        return dayTotals.maxByOrNull { it.value }?.key?.name?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: "N/A"
    }

    private fun computeLateNightRatio(expenses: List<TransactionEntity>): Float {
        if (expenses.isEmpty()) return 0f
        val lateNight = expenses.count {
            val hour = Instant.ofEpochMilli(it.date).atZone(zone).hour
            hour >= 21 || hour <= 5
        }
        return lateNight.toFloat() / expenses.size
    }

    private fun computeWeekendSpendRatio(expenses: List<TransactionEntity>): Float {
        if (expenses.isEmpty()) return 0f
        val weekendTotal = expenses.filter {
            val day = Instant.ofEpochMilli(it.date).atZone(zone).dayOfWeek
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }.sumOf { it.amount }
        val total = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
        return (weekendTotal / total).toFloat()
    }
}
