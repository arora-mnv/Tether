package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val transactionId: Long,
    val amount:        Double,
    val merchant:      String,
    val type:          String,
    val source:        String,
    val date:          Long,
    // ✅ NEW — "PENDING" until user confirms, "CONFIRMED" after
    val status:        String = "CONFIRMED"
)