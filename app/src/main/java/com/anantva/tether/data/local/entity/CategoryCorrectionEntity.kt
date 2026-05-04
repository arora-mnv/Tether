package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_corrections")
data class CategoryCorrectionEntity(
    @PrimaryKey
    val merchantKey: String,  // lowercase merchant name
    val category: String
)
