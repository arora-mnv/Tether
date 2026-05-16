package com.anantva.tether.ui_elements.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.repository.TetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _toastEvent = Channel<TransactionToastEvent>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    private val filter = MutableStateFlow(TransactionFilter.ALL)
    private val sort = MutableStateFlow(TransactionSort.DATE_DESC)

    val pagedTransactions: Flow<PagingData<TransactionEntity>> =
        tetherRepository.getTransactionsPaged().cachedIn(viewModelScope)

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

    suspend fun suggestCategory(merchant: String, isDebit: Boolean): String =
        tetherRepository.suggestCategory(merchant, if (isDebit) "Expense" else "Credit")

    fun updateTransaction(updated: TransactionEntity) {
        viewModelScope.launch {
            Log.d("TetherTxn", "Saving transaction started")
            val success = tetherRepository.updateTransaction(updated)
            if (success) {
                Log.d("TetherTxn", "Transaction saved")
                _toastEvent.send(TransactionToastEvent.Success)
            } else {
                Log.e("TetherTxn", "Error")
                _toastEvent.send(TransactionToastEvent.Failure("Error"))
            }
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            tetherRepository.deleteTransaction("", transactionId)
        }
    }
}
