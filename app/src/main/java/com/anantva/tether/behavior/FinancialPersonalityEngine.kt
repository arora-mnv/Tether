package com.anantva.tether.behavior

import com.anantva.tether.data.local.entity.TransactionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class PersonalityProfile(
    val title: String = "Reading your rhythm\u2026",
    val description: String = "Learning your financial patterns over time.",
    val waveformSharpness: Float = 0f,
    val waveformSpeed: Float = 0.8f,
    val auraIntensity: Float = 0.8f,
    val isReady: Boolean = false,
    val rawScores: PersonalityScores = PersonalityScores()
)

data class PersonalityScores(
    val wantsRatio: Float = 0f,
    val volatility: Float = 0f,
    val frequency: Float = 0f,
    val streakHealth: Float = 0f,
    val lateNightRatio: Float = 0f,
    val overspendRate: Float = 0f
)

@Singleton
class FinancialPersonalityEngine @Inject constructor() {

    private var previousPersonality: PersonalityProfile = PersonalityProfile()
    private var adaptationCount: Int = 0

    private val zone = ZoneId.systemDefault()

    fun compute(
        transactions: List<TransactionEntity>,
        streakDays: Int,
        isOverLimit: Boolean
    ): PersonalityProfile {
        val now = LocalDate.now(zone)
        val monthAgo = now.minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekAgo = now.minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

        val recentTxns = transactions.filter { it.date >= weekAgo && it.type == "Expense" }
        val monthTxns = transactions.filter { it.date >= monthAgo && it.type == "Expense" }

        val activeDays = recentTxns.map {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.distinct().size

        if (activeDays < 3 || monthTxns.size < 5) {
            return PersonalityProfile()
        }

        val scores = computeScores(recentTxns, monthTxns, streakDays, isOverLimit)
        val candidate = classify(scores)

        return smoothTransition(candidate)
    }

    private fun computeScores(
        recent: List<TransactionEntity>,
        month: List<TransactionEntity>,
        streakDays: Int,
        isOverLimit: Boolean
    ): PersonalityScores {
        val wants = recent.filter { it.spendNature == "WANT" }.sumOf { it.amount }.toFloat()
        val totalSpend = recent.sumOf { it.amount }.toFloat()
        val wantsRatio = if (totalSpend > 0f) wants / totalSpend else 0f

        val dailyAmounts = month.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.map { (_, txns) -> txns.sumOf { it.amount }.toFloat() }
        val mean = dailyAmounts.average().toFloat().coerceAtLeast(1f)
        val variance = dailyAmounts.map { (it - mean) * (it - mean) }.sum() / dailyAmounts.size.coerceAtLeast(1)
        val volatility = (kotlin.math.sqrt(variance.toDouble()).toFloat() / mean).coerceIn(0f, 1f)

        val avgFreq = recent.size.toFloat() / 7f.coerceAtLeast(1f)

        val streakHealth = (streakDays.toFloat() / 30f).coerceIn(0f, 1f)

        val lateNight = recent.count {
            val hour = java.time.Instant.ofEpochMilli(it.date).atZone(zone).hour
            hour >= 21 || hour <= 5
        }
        val lateNightRatio = if (recent.isNotEmpty()) lateNight.toFloat() / recent.size else 0f

        val overspendDays = month.groupBy {
            java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.count { (_, txns) -> txns.sumOf { t -> t.amount } > 2000.0 }
        val overspendRate = if (streakDays == 0 && isOverLimit) 1f
            else (overspendDays.toFloat() / 30f.coerceAtMost(month.size.coerceAtLeast(1).toFloat())).coerceIn(0f, 1f)

        return PersonalityScores(
            wantsRatio = wantsRatio,
            volatility = volatility,
            frequency = (avgFreq / 8f).coerceIn(0f, 1f),
            streakHealth = streakHealth,
            lateNightRatio = lateNightRatio,
            overspendRate = overspendRate
        )
    }

    private fun classify(s: PersonalityScores): PersonalityProfile {
        val high = 0.6f
        val mid = 0.35f

        val isHighWants = s.wantsRatio > high
        val isHighVol = s.volatility > high
        val isHighFreq = s.frequency > high
        val isLowStreak = s.streakHealth < 0.2f
        val isHighLate = s.lateNightRatio > mid
        val isHighOverspend = s.overspendRate > mid

        return when {
            isHighWants && isHighVol && isHighFreq && isHighOverspend ->
                PersonalityProfile("Impulse Machine",
                    "Quick-trigger spending. Wants win almost every time.", 0.7f, 2f, 1.3f, true, s)

            isHighWants && isHighLate && isHighVol ->
                PersonalityProfile("Mood Buyer",
                    "Evening emotional spending. Late-night wants spike.", 0.55f, 1.7f, 1.2f, true, s)

            s.wantsRatio > 0.5f && s.frequency > mid && !isHighOverspend ->
                PersonalityProfile("Comfort Spender",
                    "Small regular purchases that add up. Feels good in the moment.", 0.35f, 1.4f, 1f, true, s)

            isHighWants && s.frequency < mid && isHighOverspend ->
                PersonalityProfile("Doom Spender",
                    "Each big purchase hits hard. Recovery is becoming rare.", 0.85f, 2.2f, 1.5f, true, s)

            s.wantsRatio > mid && isHighLate ->
                PersonalityProfile("Midnight Burner",
                    "Late nights bring spending impulses. Sleep on big purchases.", 0.6f, 1.8f, 1.2f, true, s)

            s.wantsRatio < 0.3f && isHighFreq ->
                PersonalityProfile("Snack Economist",
                    "Small frequent spends, mostly on essentials. Efficient.", 0.15f, 1f, 0.85f, true, s)

            isHighVol && s.wantsRatio > mid ->
                PersonalityProfile("Financial Freestyler",
                    "Wild spending swings. No two days look the same.", 0.75f, 2f, 1.4f, true, s)

            s.wantsRatio < 0.25f && s.streakHealth > 0.6f ->
                PersonalityProfile("Steady Builder",
                    "Consistent. Controlled. Your financial foundation is solid.", 0f, 0.8f, 0.8f, true, s)

            s.wantsRatio < 0.3f && s.streakHealth > 0.3f ->
                PersonalityProfile("Quiet Saver",
                    "Low noise. Low wants. The balance is growing quietly.", 0.05f, 0.85f, 0.8f, true, s)

            s.streakHealth > 0.7f && s.wantsRatio < 0.4f ->
                PersonalityProfile("Goal Chaser",
                    "Every transaction aligns with the bigger target.", 0f, 0.75f, 0.75f, true, s)

            s.wantsRatio > 0.6f && !isHighOverspend && !isHighVol ->
                PersonalityProfile("Reward Seeker",
                    "You work hard and spend on what matters to you.", 0.3f, 1.2f, 0.95f, true, s)

            s.wantsRatio < 0.2f && s.volatility < 0.2f ->
                PersonalityProfile("Structured Minimalist",
                    "Every rupee has a place. Clean, lean, intentional.", 0f, 0.7f, 0.7f, true, s)

            s.wantsRatio > 0.4f && s.frequency < 0.3f ->
                PersonalityProfile("Weekend Drifter",
                    "Weekends loosen the wallet. Weekdays stay tight.", 0.25f, 1.1f, 0.9f, true, s)

            s.volatility < 0.2f && s.wantsRatio < 0.4f ->
                PersonalityProfile("Calm Controller",
                    "Nothing extreme. Just steady financial awareness.", 0f, 0.8f, 0.75f, true, s)

            else -> PersonalityProfile("Balanced Drifter",
                "No strong patterns yet. You adapt to what comes.", 0.1f, 0.9f, 0.85f, true, s)
        }
    }

    private fun smoothTransition(candidate: PersonalityProfile): PersonalityProfile {
        if (!candidate.isReady) return candidate
        if (previousPersonality.title == "Reading your rhythm\u2026") {
            previousPersonality = candidate
            adaptationCount = 0
            return candidate
        }

        if (candidate.title == previousPersonality.title) {
            adaptationCount = 0
            return candidate
        }

        adaptationCount++
        if (adaptationCount < 3) return previousPersonality

        adaptationCount = 0
        previousPersonality = candidate
        return candidate
    }

    fun reset() {
        previousPersonality = PersonalityProfile()
        adaptationCount = 0
    }
}
