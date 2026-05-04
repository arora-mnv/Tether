package com.anantva.tether.data.model

data class RecurringTransaction(
    val id: String = "",
    val merchant: String,
    val amount: Double,
    val frequency: RecurringFrequency,
    val category: String,
    val nextDueDate: Long,
    val isActive: Boolean = true,
    val linkedTransactionId: Long? = null,
    val tags: List<String> = emptyList()
)

enum class RecurringFrequency(val label: String, val daysInterval: Int) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 90),
    YEARLY("Yearly", 365);

    companion object {
        fun fromString(value: String): RecurringFrequency {
            return entries.find { it.name == value } ?: MONTHLY
        }
    }
}

fun List<RecurringTransaction>.monthlyRecurringTotal(): Double {
    return sumOf { recurring ->
        val monthlyEquivalent = when (recurring.frequency) {
            RecurringFrequency.DAILY -> recurring.amount * 30
            RecurringFrequency.WEEKLY -> recurring.amount * 4.33
            RecurringFrequency.MONTHLY -> recurring.amount
            RecurringFrequency.QUARTERLY -> recurring.amount / 3.0
            RecurringFrequency.YEARLY -> recurring.amount / 12.0
        }
        if (recurring.isActive) monthlyEquivalent else 0.0
    }
}
