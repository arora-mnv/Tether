package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_patterns")
data class MerchantPatternEntity(
    @PrimaryKey
    val normalizedMerchant: String,
    val category: String,
    val confidenceScore: Float,
    val usageCount: Int,
    val lastUsedTimestamp: Long
)
