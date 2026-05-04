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
    suspend fun categorize(merchant: String): String {
        val key = merchant.lowercase().trim()
        val correction = correctionDao.getCorrection(key)
        return correction ?: SpendingCategories.categorize(merchant, "Expense")
    }

    suspend fun saveCorrection(merchant: String, category: String) {
        val key = merchant.lowercase().trim()
        correctionDao.insertCorrection(CategoryCorrectionEntity(key, category))
    }
}
