package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TransactionFilter { ALL, EXPENSE, CREDIT }

enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

data class VaultUiState(
    val filter: TransactionFilter = TransactionFilter.ALL,
    val sort: TransactionSort = TransactionSort.DATE_DESC,
    val transactions: List<TransactionEntity> = emptyList()
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val tetherRepository: TetherRepository
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val sort = MutableStateFlow(TransactionSort.DATE_DESC)

    val uiState: StateFlow<VaultUiState> =
        combine(
            filter,
            sort,
            tetherRepository.getAllTransactions()
        ) { f, s, txns ->
            val filtered = when (f) {
                TransactionFilter.ALL -> txns
                TransactionFilter.EXPENSE -> txns.filter { it.type == "Expense" }
                TransactionFilter.CREDIT -> txns.filter { it.type == "Credit" }
            }

            val sorted = when (s) {
                TransactionSort.DATE_DESC -> filtered.sortedByDescending { it.date }
                TransactionSort.DATE_ASC -> filtered.sortedBy { it.date }
                TransactionSort.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
                TransactionSort.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            }

            VaultUiState(
                filter = f,
                sort = s,
                transactions = sorted
            )
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = VaultUiState()
        )

    fun setFilter(value: TransactionFilter) {
        filter.value = value
    }

    fun setSort(value: TransactionSort) {
        sort.value = value
    }

    fun updateTransaction(updated: TransactionEntity) {
        viewModelScope.launch {
            tetherRepository.updateTransaction(updated)
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            tetherRepository.deleteTransaction("", transactionId)
        }
    }
}

