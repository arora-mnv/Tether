package com.anantva.tether.ui_elements.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.local.entity.TxnCategory
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class TransactionToastEvent {
    object Success : TransactionToastEvent()
    data class Failure(val message: String?) : TransactionToastEvent()
}

@HiltViewModel
class ManualTransactionViewModel @Inject constructor(
    private val tetherRepository: TetherRepository
) : ViewModel() {

    private val _toastEvent = Channel<TransactionToastEvent>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    suspend fun suggestCategory(merchant: String, isDebit: Boolean): String =
        tetherRepository.suggestCategory(merchant, if (isDebit) "Expense" else "Credit")

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
            Log.d("TetherTxn", "Saving transaction started")
            val success = tetherRepository.addTransaction(
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
            if (success) {
                Log.d("TetherTxn", "Transaction saved")
                _toastEvent.send(TransactionToastEvent.Success)
            } else {
                Log.e("TetherTxn", "Error")
                _toastEvent.send(TransactionToastEvent.Failure("Error"))
            }
        }
    }
}
