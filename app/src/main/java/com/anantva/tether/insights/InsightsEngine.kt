package com.anantva.tether.insights

import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.TxnCategory
import com.anantva.tether.data.repository.TetherRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightsEngine @Inject constructor(
    private val tetherRepository: TetherRepository
) {
    private val zone = ZoneId.systemDefault()

    data class DailyInsight(
        val totalSpend: Int,
        val discretionarySpend: Int,
        val needSpend: Int,
        val wantSpend: Int,
        val needWantRatio: Float,
        val categoryBreakdown: List<CategorySpend>,
        val topCategory: String,
        val topCategoryAmount: Int,
        val transactionCount: Int,
        val normalTransactionCount: Int,
        val recurringTotal: Double,
        val healthScore: Float,
        val insightMessage: String
    )

    suspend fun getDailyInsight(date: LocalDate = LocalDate.now(), isOverLimit: Boolean = false): DailyInsight {
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = startOfDay + 86_400_000 - 1

        val totalSpend = tetherRepository.getExpenseSpentValue(startOfDay, endOfDay)
        val discretionarySpend = tetherRepository.getDiscretionarySpend(startOfDay, endOfDay)
        val needSpend = tetherRepository.getNeedSpend(startOfDay, endOfDay)
        val wantSpend = tetherRepository.getWantSpend(startOfDay, endOfDay)

        val needWantRatio = if (wantSpend > 0) {
            (needSpend.toFloat() / wantSpend.toFloat())
        } else if (needSpend > 0) {
            Float.POSITIVE_INFINITY
        } else {
            0f
        }

        val categoryBreakdown = tetherRepository.getCategoryBreakdown(startOfDay, endOfDay)
        val topCategory = categoryBreakdown.firstOrNull()?.category ?: "No spending"
        val topCategoryAmount = categoryBreakdown.firstOrNull()?.total ?: 0

        val allConfirmed = tetherRepository.getAllConfirmedTransactions()
        val dayTransactions = allConfirmed.filter { txn ->
            val txnDate = Instant.ofEpochMilli(txn.date).atZone(zone).toLocalDate()
            txnDate == date && txn.type == "Expense"
        }
        val previousWeekAverage = dailyAverageBefore(date, allConfirmed)
        val recurringTxnCount = dayTransactions.count { it.typedCategory == TxnCategory.RECURRING }

        val normalTxnCount = dayTransactions.count { it.isStreakRelevant }

        val healthScore = calculateHealthScore(
            totalSpend = totalSpend,
            discretionarySpend = discretionarySpend,
            needSpend = needSpend,
            wantSpend = wantSpend,
            needWantRatio = needWantRatio
        )

        val insightMessage = generateInsightMessage(
            totalSpend = totalSpend,
            discretionarySpend = discretionarySpend,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount,
            needWantRatio = needWantRatio,
            healthScore = healthScore,
            normalTxnCount = normalTxnCount,
            recurringTxnCount = recurringTxnCount,
            wantSpend = wantSpend,
            previousWeekAverage = previousWeekAverage,
            dayTransactions = dayTransactions,
            isOverLimit = isOverLimit
        )

        return DailyInsight(
            totalSpend = totalSpend,
            discretionarySpend = discretionarySpend,
            needSpend = needSpend,
            wantSpend = wantSpend,
            needWantRatio = needWantRatio,
            categoryBreakdown = categoryBreakdown,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount,
            transactionCount = dayTransactions.size,
            normalTransactionCount = normalTxnCount,
            recurringTotal = 0.0,
            healthScore = healthScore,
            insightMessage = insightMessage
        )
    }

    data class WeeklyInsight(
        val totalWeekSpend: Int,
        val avgDailySpend: Int,
        val peakDay: String,
        val peakDayAmount: Int,
        val categoryBreakdown: List<CategorySpend>,
        val needVsWant: Pair<Int, Int>,
        val dayByDaySpend: Map<String, Int>,
        val trendDirection: TrendDirection,
        val insightMessage: String
    )

    enum class TrendDirection { UP, DOWN, STABLE }

    suspend fun getWeeklyInsight(endDate: LocalDate = LocalDate.now()): WeeklyInsight {
        val weekStart = endDate.minusDays((endDate.dayOfWeek.value - 1).toLong())
        val weekEnd = weekStart.plusDays(6)

        val startMs = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = weekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val allConfirmed: List<TransactionEntity> = tetherRepository.getAllConfirmedTransactions()
        val weekTxns: List<TransactionEntity> = allConfirmed.filter { txn ->
            txn.type == "Expense" && txn.status == "CONFIRMED" &&
                txn.date in startMs..endMs
        }

        val totalWeekSpend = weekTxns.sumOf { it.amount }.toInt()
        val avgDailySpend = if (totalWeekSpend > 0) totalWeekSpend / 7 else 0

        val dayByDayMap: Map<String, List<TransactionEntity>> = weekTxns.groupBy { txn ->
            Instant.ofEpochMilli(txn.date).atZone(zone).dayOfWeek.name
                .lowercase().replaceFirstChar { it.uppercase() }
        }
        val dayByDay: Map<String, Int> = dayByDayMap.mapValues { (_, txns) ->
            txns.sumOf { txn -> txn.amount }.toInt()
        }

        val peakDay = dayByDay.maxByOrNull { it.value }?.key ?: "N/A"
        val peakDayAmount = dayByDay.maxByOrNull { it.value }?.value ?: 0

        val categoryGrouped: Map<String, List<TransactionEntity>> = weekTxns.groupBy { it.category }
        val categoryBreakdown: List<CategorySpend> = categoryGrouped
            .map { (cat, txns) -> CategorySpend(cat, txns.sumOf { txn -> txn.amount }.toInt()) }
            .sortedByDescending { it.total }

        val needTotal = weekTxns.filter { it.spendNature == "NEED" }.sumOf { txn -> txn.amount }.toInt()
        val wantTotal = weekTxns.filter { it.spendNature == "WANT" }.sumOf { txn -> txn.amount }.toInt()

        val dailyTotals = (0..6).map { dayOffset ->
            val day = weekStart.plusDays(dayOffset.toLong())
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000 - 1
            weekTxns.filter { it.date in dayStart..dayEnd }.sumOf { txn -> txn.amount }.toInt()
        }

        val trendDirection = when {
            dailyTotals.size < 3 -> TrendDirection.STABLE
            dailyTotals.takeLast(3).average() > dailyTotals.take(3).average() * 1.2 -> TrendDirection.UP
            dailyTotals.takeLast(3).average() < dailyTotals.take(3).average() * 0.8 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }

        val insightMessage = generateWeeklyInsight(
            totalWeekSpend = totalWeekSpend,
            avgDailySpend = avgDailySpend,
            peakDay = peakDay,
            trendDirection = trendDirection,
            topCategory = categoryBreakdown.firstOrNull()?.category
        )

        return WeeklyInsight(
            totalWeekSpend = totalWeekSpend,
            avgDailySpend = avgDailySpend,
            peakDay = peakDay,
            peakDayAmount = peakDayAmount,
            categoryBreakdown = categoryBreakdown,
            needVsWant = needTotal to wantTotal,
            dayByDaySpend = dayByDay,
            trendDirection = trendDirection,
            insightMessage = insightMessage
        )
    }

    private fun calculateHealthScore(
        totalSpend: Int,
        discretionarySpend: Int,
        needSpend: Int,
        wantSpend: Int,
        needWantRatio: Float
    ): Float {
        var score = 0.5f

        if (needWantRatio >= 2f) score += 0.2f
        else if (needWantRatio >= 1f) score += 0.1f
        else if (needWantRatio < 0.5f) score -= 0.15f

        if (discretionarySpend == 0) score += 0.1f
        else if (discretionarySpend < totalSpend * 0.3) score += 0.05f
        else score -= 0.1f

        if (totalSpend == 0) score = 1f

        return score.coerceIn(0f, 1f)
    }

    private fun generateInsightMessage(
        totalSpend: Int,
        discretionarySpend: Int,
        topCategory: String,
        topCategoryAmount: Int,
        needWantRatio: Float,
        healthScore: Float,
        normalTxnCount: Int,
        recurringTxnCount: Int,
        wantSpend: Int,
        previousWeekAverage: Int,
        dayTransactions: List<TransactionEntity>,
        isOverLimit: Boolean
    ): String {
        return when {
            isOverLimit -> listOf(
                "The momentum collapsed.",
                "Today broke the rhythm.",
                "Everything went quiet."
            ).random()
            totalSpend == 0 -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding.",
                "Today stayed under control."
            ).random()
            dayTransactions.isNotEmpty() && dayTransactions.all { txn ->
                SpendingCategories.streakPenaltyWeight(txn.category, txn.merchant, txn.txnCategory) == 0.0
            } -> listOf(
                "Today stayed under control.",
                "The rhythm is holding."
            ).random()
            previousWeekAverage > 0 && totalSpend < (previousWeekAverage * 0.6f) -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding."
            ).random()
            healthScore >= 0.8f || needWantRatio >= 3f -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding.",
                "Today stayed under control."
            ).random()
            discretionarySpend > totalSpend * 0.6f -> listOf(
                "Impulse is taking over.",
                "The pace is getting dangerous.",
                "The buffer is disappearing."
            ).random()
            normalTxnCount >= 5 -> listOf(
                "The pace is getting dangerous.",
                "The buffer is disappearing."
            ).random()
            wantSpend > 0 && needWantRatio < 1f -> listOf(
                "Impulse is taking over.",
                "The pace is getting dangerous."
            ).random()
            else -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding.",
                "Today stayed under control."
            ).random()
        }
    }

    private fun dailyAverageBefore(date: LocalDate, transactions: List<TransactionEntity>): Int {
        val startDate = date.minusDays(7)
        val trailingTransactions = transactions.filter { txn ->
            if (txn.type != "Expense" || txn.status != "CONFIRMED") return@filter false
            val txnDate = Instant.ofEpochMilli(txn.date).atZone(zone).toLocalDate()
            txnDate >= startDate && txnDate < date
        }

        if (trailingTransactions.isEmpty()) return 0

        return trailingTransactions
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .values
            .sumOf { day -> day.sumOf { it.amount }.toInt() } / 7
    }

    private fun generateWeeklyInsight(
        totalWeekSpend: Int,
        avgDailySpend: Int,
        peakDay: String,
        trendDirection: TrendDirection,
        topCategory: String?
    ): String {
        return when {
            totalWeekSpend == 0 -> "Clean week. Zero spending."
            trendDirection == TrendDirection.UP -> "Spending is climbing. $peakDay was the peak day."
            trendDirection == TrendDirection.DOWN -> "Good trend — spending is going down."
            avgDailySpend > 2000 -> "₹$avgDailySpend/day average. That's steep."
            avgDailySpend < 500 -> "Under ₹500/day. Impressive."
            topCategory != null -> "Most spent on $topCategory this week."
            else -> "Standard week. Nothing abnormal."
        }
    }
}
