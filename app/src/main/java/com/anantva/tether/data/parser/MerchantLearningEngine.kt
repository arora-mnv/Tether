package com.anantva.tether.data.parser

import com.anantva.tether.data.local.dao.MerchantPatternDao
import com.anantva.tether.data.local.entity.MerchantPatternEntity
import com.anantva.tether.data.local.entity.RecurringType
import com.anantva.tether.data.local.entity.SpendingCategories
import javax.inject.Inject
import javax.inject.Singleton

data class PredictionResult(
    val category: String,
    val confidence: Float,
    val source: PredictionSource,
    val recurringType: RecurringType = RecurringType.OTHER,
    val isRecurring: Boolean = false
)

enum class PredictionSource { EXACT, PARTIAL, TOKEN, RECURRING_HISTORY }

@Singleton
class MerchantLearningEngine @Inject constructor(
    private val patternDao: MerchantPatternDao
) {
    suspend fun predict(merchant: String): PredictionResult? {
        val normalized = normalize(merchant)
        if (normalized.isBlank()) return null

        val exact = patternDao.getPattern(normalized)
        if (exact != null && exact.confidenceScore >= 0.4f && exact.usageCount >= 1) {
            val recurringType = RecurringType.infer(exact.category, merchant)
            return PredictionResult(
                exact.category, exact.confidenceScore, PredictionSource.EXACT,
                recurringType = recurringType,
                isRecurring = recurringType != RecurringType.OTHER
            )
        }

        val all = patternDao.getAllPatterns()
            .filter { it.confidenceScore >= 0.4f && it.usageCount >= 1 }

        val partial = all
            .filter { normalized.contains(it.normalizedMerchant) || it.normalizedMerchant.contains(normalized) }
            .maxByOrNull { it.confidenceScore * it.usageCount }
        if (partial != null) {
            val recurringType = RecurringType.infer(partial.category, merchant)
            return PredictionResult(
                partial.category, partial.confidenceScore, PredictionSource.PARTIAL,
                recurringType = recurringType,
                isRecurring = recurringType != RecurringType.OTHER
            )
        }

        val tokens = normalized.split(" ").filter { it.length >= 3 }
        for (token in tokens) {
            val match = all.find { it.normalizedMerchant.contains(token) }
            if (match != null) {
                val recurringType = RecurringType.infer(match.category, merchant)
                return PredictionResult(
                    match.category, match.confidenceScore * 0.85f, PredictionSource.TOKEN,
                    recurringType = recurringType,
                    isRecurring = recurringType != RecurringType.OTHER
                )
            }
        }

        val keywordType = RecurringType.infer(SpendingCategories.OTHER, merchant)
        if (keywordType != RecurringType.OTHER) {
            val category = SpendingCategories.categorize(merchant, "Expense")
            return PredictionResult(
                category, 0.6f, PredictionSource.RECURRING_HISTORY,
                recurringType = keywordType,
                isRecurring = true
            )
        }

        return null
    }

    suspend fun predictRecurringType(merchant: String): RecurringType {
        val normalized = normalize(merchant)
        if (normalized.isBlank()) return RecurringType.OTHER

        val exact = patternDao.getPattern(normalized)
        if (exact != null) {
            val fromCategory = RecurringType.fromCategory(exact.category)
            if (fromCategory != RecurringType.OTHER) return fromCategory
            val inferred = RecurringType.infer(exact.category, merchant)
            if (inferred != RecurringType.OTHER) return inferred
        }

        val inferred = RecurringType.infer(SpendingCategories.OTHER, merchant)
        return inferred
    }

    suspend fun learn(merchant: String, category: String, isUserOverride: Boolean = false) {
        val normalized = normalize(merchant)
        if (normalized.isBlank() || category.isBlank()) return

        val existing = patternDao.getPattern(normalized)
        val now = System.currentTimeMillis()

        if (existing != null) {
            if (existing.category == category) {
                val newConfidence = minOf(1.0f, existing.confidenceScore + 0.15f)
                patternDao.upsertPattern(existing.copy(
                    confidenceScore = newConfidence,
                    usageCount = existing.usageCount + 1,
                    lastUsedTimestamp = now
                ))
            } else if (isUserOverride) {
                val newConfidence = minOf(1.0f, existing.confidenceScore * 0.3f + 0.5f)
                patternDao.upsertPattern(existing.copy(
                    category = category,
                    confidenceScore = newConfidence,
                    usageCount = existing.usageCount + 1,
                    lastUsedTimestamp = now
                ))
            } else {
                val newConfidence = maxOf(0.1f, existing.confidenceScore * 0.4f)
                patternDao.upsertPattern(existing.copy(
                    category = category,
                    confidenceScore = newConfidence,
                    usageCount = existing.usageCount + 1,
                    lastUsedTimestamp = now
                ))
            }
        } else {
            patternDao.upsertPattern(MerchantPatternEntity(
                normalizedMerchant = normalized,
                category = category,
                confidenceScore = 0.6f,
                usageCount = 1,
                lastUsedTimestamp = now
            ))
        }
    }

    suspend fun getRecurringMerchants(): List<String> {
        return patternDao.getAllPatterns()
            .filter { it.usageCount >= 2 }
            .mapNotNull { pattern ->
                val type = RecurringType.infer(pattern.category, pattern.normalizedMerchant)
                if (type != RecurringType.OTHER) pattern.normalizedMerchant else null
            }
    }

    fun normalize(merchant: String): String = merchant
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    suspend fun reset() {
        patternDao.deleteAll()
    }
}
