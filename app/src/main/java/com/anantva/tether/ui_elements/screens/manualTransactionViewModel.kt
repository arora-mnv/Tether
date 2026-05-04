package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.TxnCategory
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManualTransactionViewModel @Inject constructor(
    private val tetherRepository: TetherRepository
) : ViewModel() {

    fun addManualTransaction(
        amount: Double,
        merchant: String,
        isDebit: Boolean,
        category: String = SpendingCategories.OTHER,
        isRecurring: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val txnCategory = if (isRecurring) TxnCategory.RECURRING.toDbValue() else TxnCategory.NORMAL.toDbValue()
        viewModelScope.launch {
            tetherRepository.addTransaction(
                TransactionEntity(
                    transactionId = now,
                    amount = amount,
                    merchant = merchant,
                    type = if (isDebit) "Expense" else "Credit",
                    source = "Manual",
                    date = now,
                    status = "CONFIRMED",
                    category = category,
                    txnCategory = txnCategory
                )
            )
        }
    }
}

