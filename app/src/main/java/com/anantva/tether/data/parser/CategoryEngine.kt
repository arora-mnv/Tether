package com.anantva.tether.data.parser

import com.anantva.tether.data.local.dao.CategoryCorrectionDao
import com.anantva.tether.data.local.entity.CategoryCorrectionEntity
import com.anantva.tether.data.local.entity.SpendingCategories
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryEngine @Inject constructor(
    private val correctionDao: CategoryCorrectionDao
) {
    suspend fun categorize(merchant: String, type: String = "Expense"): String {
        val normalizedMerchant = SpendingCategories.normalizeMerchant(merchant)
        if (normalizedMerchant.isBlank()) {
            return SpendingCategories.categorize(merchant, type)
        }

        correctionDao.getCorrection(normalizedMerchant)?.let { return it }

        val learnedMatch = correctionDao.getAllCorrections()
            .asSequence()
            .filter { it.merchantKey.startsWith(PATTERN_PREFIX) }
            .map { it.copy(merchantKey = it.merchantKey.removePrefix(PATTERN_PREFIX)) }
            .filter { pattern -> normalizedMerchant.contains(pattern.merchantKey) }
            .maxByOrNull { it.merchantKey.length }

        return learnedMatch?.category ?: SpendingCategories.categorize(merchant, type)
    }

    suspend fun saveCorrection(merchant: String, category: String): List<CategoryCorrectionEntity> {
        if (category == SpendingCategories.OTHER) return emptyList()

        val entries = buildCorrectionEntries(merchant, category)
        entries.forEach { correctionDao.insertCorrection(it) }
        return entries
    }

    private fun buildCorrectionEntries(merchant: String, category: String): List<CategoryCorrectionEntity> {
        val normalizedMerchant = SpendingCategories.normalizeMerchant(merchant)
        if (normalizedMerchant.isBlank()) return emptyList()

        val learnedPatterns = buildList {
            add(normalizedMerchant)
            addAll(extractLearnablePatterns(normalizedMerchant).map { "$PATTERN_PREFIX$it" })
        }.distinct()

        return learnedPatterns.map { CategoryCorrectionEntity(it, category) }
    }

    private fun extractLearnablePatterns(normalizedMerchant: String): List<String> {
        val tokens = normalizedMerchant
            .split(" ")
            .filter { token -> token.length >= 3 && token !in STOP_WORDS }

        return buildList {
            if (tokens.isNotEmpty()) add(tokens.first())
            if (tokens.size >= 2) add(tokens.take(2).joinToString(" "))
        }.distinct()
    }

    companion object {
        private const val PATTERN_PREFIX = "pattern:"
        private val STOP_WORDS = setOf(
            "ltd", "limited", "india", "pvt", "private", "services",
            "store", "market", "payment", "pay", "upi", "txn", "bank"
        )
    }
}
