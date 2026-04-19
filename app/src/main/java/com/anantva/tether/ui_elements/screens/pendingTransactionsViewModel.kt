package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    private val tetherRepository: TetherRepository
) : ViewModel() {

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
        isDebit: Boolean
    ) {
        viewModelScope.launch {
            tetherRepository.confirmAndUpdateTransaction(
                id = id,
                amount = amount,
                merchant = merchant,
                type = if (isDebit) "Expense" else "Credit"
            )
        }
    }

    fun discardPendingTransaction(id: Long) {
        viewModelScope.launch {
            tetherRepository.deletePendingTransaction(id)
        }
    }
}

