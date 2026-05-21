package com.anantva.tether.data.model

import com.anantva.tether.data.local.entity.RecurringType
import com.anantva.tether.data.local.entity.SpendingCategories

data class TransactionSuggestion(
    val category: String = SpendingCategories.OTHER,
    val isRecurring: Boolean = false,
    val recurringType: RecurringType = RecurringType.OTHER,
    val showRecurringSuggestion: Boolean = false,
    val recurringSuggestionMessage: String? = null
)
