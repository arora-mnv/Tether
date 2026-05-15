package com.anantva.tether.behavior

import com.anantva.tether.data.local.entity.TransactionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatternAnalyzer @Inject constructor() {

    private val zone = ZoneId.systemDefault()

    fun analyze(
        transactions: List<TransactionEntity>,
        streakDays: Int,
        isOverLimit: Boolean,
        spentToday: Int
    ): BehaviorSnapshot {
        val now = LocalDate.now(zone)
        val todayStart = now.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86_400_000 - 1
        val weekAgo = now.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthAgo = now.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

        val monthTxns = transactions.filter { it.date in monthAgo..todayEnd && it.type == "Expense" }
        val weekTxns = transactions.filter { it.date in weekAgo..todayEnd && it.type == "Expense" }
        val todayTxns = transactions.filter { it.date in todayStart..todayEnd && it.type == "Expense" }

        val wantsRatio = computeWantsRatio(weekTxns)
        val impulseScore = computeImpulseScore(weekTxns, todayTxns)
        val disciplineScore = computeDisciplineScore(monthTxns, streakDays, isOverLimit, spentToday)
        val stabilityScore = computeStabilityScore(monthTxns)
        val streakResilience = computeStreakResilience(streakDays, isOverLimit, monthTxns)
        val streakRisk = computeStreakRisk(streakDays, todayTxns, monthTxns)

        return BehaviorSnapshot(
            impulseScore = impulseScore,
            disciplineScore = disciplineScore,
            stabilityScore = stabilityScore,
            streakResilience = streakResilience,
            wantsRatio = wantsRatio,
            streakRisk = streakRisk,
            behavioralTrend = computeTrend(monthTxns, weekTxns)
        )
    }

    private fun computeWantsRatio(weekTxns: List<TransactionEntity>): Float {
        val wants = weekTxns.sumOf { if (it.spendNature == "WANT") it.amount else 0.0 }.toFloat()
        val total = weekTxns.sumOf { it.amount }.toFloat()
        return if (total > 0f) (wants / total).coerceIn(0f, 1f) else 0.5f
    }

    private fun computeImpulseScore(
        weekTxns: List<TransactionEntity>,
        todayTxns: List<TransactionEntity>
    ): Float {
        val weekWants = weekTxns.sumOf { if (it.spendNature == "WANT") it.amount else 0.0 }.toFloat()
        val weekTotal = weekTxns.sumOf { it.amount }.toFloat()
        val weekRatio = if (weekTotal > 0f) weekWants / weekTotal else 0.5f

        val todayCount = todayTxns.size
        val todayWants = todayTxns.count { it.spendNature == "WANT" }
        val todayRatio = if (todayCount > 0) todayWants.toFloat() / todayCount else 0.5f

        val hasLateNight = todayTxns.any {
            val hour = java.time.Instant.ofEpochMilli(it.date).atZone(zone).hour
            hour >= 21 || hour <= 5
        }

        val raw = (weekRatio * 0.6f + todayRatio * 0.4f)
        return (raw + if (hasLateNight) 0.1f else 0f).coerceIn(0f, 1f)
    }

    private fun computeDisciplineScore(
        monthTxns: List<TransactionEntity>,
        streakDays: Int,
        isOverLimit: Boolean,
        spentToday: Int
    ): Float {
        val dailySpending = monthTxns.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }
        val dayCount = dailySpending.size.coerceAtLeast(1)
        val largeDays = dailySpending.count { (_, txns) ->
            txns.sumOf { it.amount } > 2000.0
        }
        val overshootRatio = largeDays.toFloat() / dayCount

        val streakFactor = (streakDays.toFloat() / 60f).coerceIn(0f, 1f)
        val todayPenalty = if (isOverLimit) 0.2f else 0f

        return (streakFactor * 0.5f + (1f - overshootRatio) * 0.5f - todayPenalty).coerceIn(0f, 1f)
    }

    private fun computeStabilityScore(monthTxns: List<TransactionEntity>): Float {
        val dailyAmounts = monthTxns.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.map { (_, txns) -> txns.sumOf { it.amount }.toFloat() }

        if (dailyAmounts.size < 3) return 0.5f

        val mean = dailyAmounts.average().toFloat()
        val variance = dailyAmounts.map { (it - mean) * (it - mean) }.sum() / dailyAmounts.size
        val cv = kotlin.math.sqrt(variance.toDouble()).toFloat() / (mean.coerceAtLeast(1f))
        return (1f - cv.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun computeStreakResilience(
        streakDays: Int,
        isOverLimit: Boolean,
        monthTxns: List<TransactionEntity>
    ): Float {
        if (streakDays == 0 && isOverLimit) return 0f
        val streakFactor = (streakDays.toFloat() / 30f).coerceIn(0f, 1f)
        val overLimitCount = monthTxns.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.count { (_, txns) ->
            txns.sumOf { it.amount } > 2000.0
        }
        val monthDays = monthTxns.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.size.coerceAtLeast(1)
        val overLimitRatio = overLimitCount.toFloat() / monthDays
        return (streakFactor * 0.7f + (1f - overLimitRatio) * 0.3f).coerceIn(0f, 1f)
    }

    private fun computeStreakRisk(
        streakDays: Int,
        todayTxns: List<TransactionEntity>,
        monthTxns: List<TransactionEntity>
    ): Float {
        if (streakDays == 0) return 1f

        val now = LocalDate.now(zone)
        val hour = java.time.LocalTime.now(zone).hour
        val timeRisk = when {
            hour >= 21 -> 0.3f
            hour >= 18 -> 0.15f
            else -> 0f
        }
        val dayRisk = when (now.dayOfWeek) {
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> 0.2f
            DayOfWeek.SUNDAY -> 0.15f
            else -> 0f
        }

        val todayWants = todayTxns.count { it.spendNature == "WANT" }
        val wantsRisk = (todayWants.toFloat() * 0.15f).coerceAtMost(0.3f)

        val recentWantsRatio = computeWantsRatio(monthTxns)
        val trendRisk = if (recentWantsRatio > 0.6f) 0.2f else 0f

        return (timeRisk + dayRisk + wantsRisk + trendRisk).coerceIn(0f, 1f)
    }

    private fun computeTrend(
        monthTxns: List<TransactionEntity>,
        weekTxns: List<TransactionEntity>
    ): String {
        val monthWantsRatio = computeWantsRatio(monthTxns)
        val weekWantsRatio = computeWantsRatio(weekTxns)
        return when {
            weekWantsRatio > monthWantsRatio + 0.15f -> "WORSENING"
            weekWantsRatio < monthWantsRatio - 0.15f -> "IMPROVING"
            else -> "STABLE"
        }
    }
}
