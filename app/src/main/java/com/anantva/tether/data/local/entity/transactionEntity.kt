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
    val status:        String = "CONFIRMED"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "transactionId" to transactionId,
        "amount" to amount,
        "merchant" to merchant,
        "type" to type,
        "source" to source,
        "date" to date,
        "status" to status
    )
}