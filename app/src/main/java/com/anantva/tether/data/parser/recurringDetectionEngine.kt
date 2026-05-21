package com.anantva.tether.data.parser

import com.anantva.tether.data.local.entity.RecurringType
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.TxnCategory
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class RecurringDetectionEngine @Inject constructor() {

    data class DetectionResult(
        val isLikelyRecurring: Boolean,
        val confidence: Float,
        val suggestedType: RecurringType,
        val message: String? = null
    ) {
        val showSuggestion: Boolean
            get() = isLikelyRecurring && confidence >= HIGH_CONFIDENCE_THRESHOLD
    }

    private val zone = ZoneId.systemDefault()

    fun detect(
        merchant: String,
        amount: Double,
        category: String,
        history: List<TransactionEntity>
    ): DetectionResult {
        if (merchant.isBlank()) {
            return DetectionResult(false, 0f, RecurringType.OTHER)
        }

        val keywordResult = detectFromMerchantKeywords(merchant, category)

        if (amount <= 0) return keywordResult

        val patternResult = detectFromHistory(merchant, amount, history)
        return when {
            patternResult.confidence >= keywordResult.confidence -> patternResult
            else -> keywordResult
        }
    }

    private fun detectFromMerchantKeywords(merchant: String, category: String): DetectionResult {
        val normalized = SpendingCategories.normalizeMerchant(merchant)
        val type = RecurringType.infer(category, merchant)

        val isEmiByAmount = category == SpendingCategories.EMI
        val isSubscriptionKeyword = normalized.containsAny(
            "netflix", "spotify", "prime video", "hotstar", "youtube premium",
            "apple tv", "sony liv", "zee5", "jio", "kindle", "icloud",
            "google one", "microsoft 365", "canva", "notion"
        )
        val isBillKeyword = normalized.containsAny(
            "electricity", "water", "gas", "broadband", "wifi", "internet",
            "postpaid", "mobile recharge", "vodafone", "airtel", "bsnl",
            "maintenance", "property tax"
        )
        val isEmiKeyword = normalized.containsAny(
            "tata capital", "bajaj finance", "emi", "loan repayment",
            "hdfc loan", "icici loan", "sbi loan", "axis loan"
        )
        val isRentKeyword = normalized.containsAny("rent", "house rent", "housing")
        val isInsuranceKeyword = normalized.containsAny(
            "lic ", "life insurance", "policy premium", "health insurance",
            "reliance general", "car insurance", "bike insurance", "term plan"
        )

        val matched = when {
            type != RecurringType.OTHER -> true
            isSubscriptionKeyword || isBillKeyword || isEmiKeyword || isRentKeyword || isInsuranceKeyword -> true
            else -> false
        }
        if (!matched) return DetectionResult(false, 0f, RecurringType.OTHER)

        val confidence = when (type) {
            RecurringType.EMI, RecurringType.RENT, RecurringType.SIP, RecurringType.INSURANCE -> 0.95f
            RecurringType.SUBSCRIPTION, RecurringType.BILL -> 0.90f
            RecurringType.SALARY -> 0.92f
            else -> 0.75f
        }
        return DetectionResult(
            isLikelyRecurring = true,
            confidence = confidence,
            suggestedType = type,
            message = SUGGESTION_MESSAGE
        )
    }

    private fun detectFromHistory(
        merchant: String,
        amount: Double,
        history: List<TransactionEntity>
    ): DetectionResult {
        val normalizedTarget = SpendingCategories.normalizeMerchant(merchant)
        val expenses = history.filter {
            it.type == "Expense" &&
                it.status == "CONFIRMED" &&
                merchantsMatch(normalizedTarget, SpendingCategories.normalizeMerchant(it.merchant))
        }
        if (expenses.size < MIN_PATTERN_OCCURRENCES) {
            return DetectionResult(false, 0f, RecurringType.OTHER)
        }

        val similarAmount = expenses.filter { amountsSimilar(it.amount, amount) }
        if (similarAmount.size < MIN_PATTERN_OCCURRENCES) {
            return DetectionResult(false, 0f, RecurringType.OTHER)
        }

        val sortedDates = similarAmount
            .map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .sorted()
        val intervals = sortedDates.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(a, b).toInt()
        }
        if (intervals.isEmpty()) return DetectionResult(false, 0f, RecurringType.OTHER)

        val monthlyHits = intervals.count { it in MONTHLY_INTERVAL_MIN..MONTHLY_INTERVAL_MAX }
        val weeklyHits = intervals.count { it in WEEKLY_INTERVAL_MIN..WEEKLY_INTERVAL_MAX }
        val yearlyHits = intervals.count { it in YEARLY_INTERVAL_MIN..YEARLY_INTERVAL_MAX }
        val cadenceScore = when {
            monthlyHits >= intervals.size / 2 -> 0.92f
            weeklyHits >= intervals.size / 2 -> 0.78f
            yearlyHits >= intervals.size / 2 -> 0.95f
            intervals.average() in MONTHLY_INTERVAL_MIN.toDouble()..MONTHLY_INTERVAL_MAX.toDouble() -> 0.7f
            else -> 0.4f
        }

        val countScore = (similarAmount.size.coerceAtMost(6) / 6f) * 0.35f
        val amountScore = 0.25f
        val confidence = (cadenceScore * 0.4f + countScore + amountScore).coerceIn(0f, 1f)

        if (confidence < MEDIUM_CONFIDENCE_THRESHOLD) {
            return DetectionResult(false, confidence, RecurringType.OTHER)
        }

        val latest = similarAmount.maxByOrNull { it.date }
        val suggestedType = latest?.let {
            if (it.isMarkedRecurring) it.typedRecurringType
            else RecurringType.infer(it.category, it.merchant)
        } ?: RecurringType.infer(category = SpendingCategories.categorize(merchant, "Expense"), merchant = merchant)

        return DetectionResult(
            isLikelyRecurring = true,
            confidence = confidence,
            suggestedType = suggestedType,
            message = if (confidence >= HIGH_CONFIDENCE_THRESHOLD) SUGGESTION_MESSAGE else null
        )
    }

    private fun merchantsMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val shorter = if (a.length < b.length) a else b
        val longer = if (a.length < b.length) b else a
        return longer.contains(shorter) && shorter.length >= 3
    }

    private fun amountsSimilar(existing: Double, candidate: Double): Boolean {
        if (existing <= 0 || candidate <= 0) return false
        val ratio = abs(existing - candidate) / maxOf(existing, candidate)
        return ratio <= AMOUNT_TOLERANCE_RATIO
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it, ignoreCase = true) }

    companion object {
        const val SUGGESTION_MESSAGE = "This looks like a recurring expense."
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
        private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.65f
        private const val MIN_PATTERN_OCCURRENCES = 2
        private const val AMOUNT_TOLERANCE_RATIO = 0.15
        private const val MONTHLY_INTERVAL_MIN = 25
        private const val MONTHLY_INTERVAL_MAX = 35
        private const val WEEKLY_INTERVAL_MIN = 6
        private const val WEEKLY_INTERVAL_MAX = 8
        private const val YEARLY_INTERVAL_MIN = 360
        private const val YEARLY_INTERVAL_MAX = 370
    }
}
