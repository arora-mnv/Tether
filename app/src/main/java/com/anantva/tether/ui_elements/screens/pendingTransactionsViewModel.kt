package com.anantva.tether.ui_elements.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.transactionpopup.PendingSnoozeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingTransactionsUiState(
    val transactions: List<TransactionEntity> = emptyList()
) {
    val count: Int get() = transactions.size
}

@HiltViewModel
class PendingTransactionsViewModel @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val snoozeStore: PendingSnoozeStore
) : ViewModel() {

    private val _toastEvent = Channel<TransactionToastEvent>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    suspend fun suggestTransactionDetails(merchant: String, isDebit: Boolean): Pair<String, Boolean> =
        tetherRepository.suggestTransactionDetails(merchant, if (isDebit) "Expense" else "Credit")

    val uiState: StateFlow<PendingTransactionsUiState> =
        tetherRepository.observePendingTransactions()
            .map { list -> PendingTransactionsUiState(transactions = list.sortedByDescending { it.date }) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = PendingTransactionsUiState()
            )

    fun confirmPendingTransaction(
        id: Long,
        amount: Double,
        merchant: String,
        isDebit: Boolean,
        category: String = com.anantva.tether.data.local.entity.SpendingCategories.OTHER,
        isRecurring: Boolean = false
    ) {
        viewModelScope.launch {
            Log.d("TetherTxn", "Saving transaction started")
            val txnCategory = if (isRecurring) 
                com.anantva.tether.data.local.entity.TxnCategory.RECURRING.toDbValue() 
            else 
                com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
            
            val success = tetherRepository.confirmAndUpdateTransaction(
                id = id,
                amount = amount,
                merchant = merchant,
                type = if (isDebit) "Expense" else "Credit",
                category = category,
                txnCategory = txnCategory
            )
            if (success) {
                Log.d("TetherTxn", "Transaction saved")
                _toastEvent.send(TransactionToastEvent.Success)
            } else {
                Log.e("TetherTxn", "Error")
                _toastEvent.send(TransactionToastEvent.Failure("Error"))
            }
            snoozeStore.clearAllForTransaction(id)
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            tetherRepository.deletePendingTransaction(id)
            snoozeStore.clearAllForTransaction(id)
        }
    }
}
