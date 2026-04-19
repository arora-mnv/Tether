package com.anantva.tether.data.model

data class ParsedTransaction(
    val amount:       Double,
    val merchant:     String,
    val type:         TransactionType,
    val rawText:      String,           // original notification text
    val sourceApp:    String,           // package name that sent it
    val detectedAt:   Long = System.currentTimeMillis()
)

enum class TransactionType { DEBIT, CREDIT }