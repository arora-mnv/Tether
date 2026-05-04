package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_patterns")
data class CategoryPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val keyword: String,        // normalized keyword (lowercase, trimmed)
    val category: String,       // matched category
    val count: Int = 1,       // number of times this pattern was confirmed
    val isGlobal: Boolean = false // true if synced from Firestore global patterns
)
