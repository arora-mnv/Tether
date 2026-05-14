package com.anantva.tether.ui_elements.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.transactionpopup.PendingSnoozeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingTransactionUiState(
    val isVisible:  Boolean = false,
    val id:         Long    = 0L,
    val amount:     Double  = 0.0,
    val merchant:   String  = "",
    val isDebit:    Boolean = true,
    val category:   String  = SpendingCategories.OTHER,
    val isRecurring: Boolean = false,
    val countdown:  Int     = 15
)

@HiltViewModel
class PendingTransactionViewModel @Inject constructor(
    private val tetherRepository: TetherRepository,
    private val snoozeStore: PendingSnoozeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingTransactionUiState())
    val uiState: StateFlow<PendingTransactionUiState> = _uiState.asStateFlow()

    private val _toastEvent = Channel<TransactionToastEvent>(Channel.BUFFERED)
    val toastEvent = _toastEvent.receiveAsFlow()

    suspend fun suggestTransactionDetails(merchant: String, isDebit: Boolean): Pair<String, Boolean> =
        tetherRepository.suggestTransactionDetails(merchant, if (isDebit) "Expense" else "Credit")

    private var countdownJob: Job? = null
    private var visiblePendingId: Long? = null

    init {
        observePendingTransactions()
    }

    private fun observePendingTransactions() {
        viewModelScope.launch {
            tetherRepository.observePendingTransactions().collect { pendingList ->
                val pending = pendingList.firstOrNull { !snoozeStore.isBlockedFromAutoSheet(it.transactionId) }
                if (pending == null) {
                    visiblePendingId = null
                    countdownJob?.cancel()
                    _uiState.value = PendingTransactionUiState()
                    return@collect
                }

                if (visiblePendingId != pending.transactionId) {
                    visiblePendingId = pending.transactionId
                    showPending(pending)
                }
            }
        }
    }

    private fun showPending(entity: TransactionEntity) {
        _uiState.value = PendingTransactionUiState(
            isVisible = true,
            id        = entity.transactionId,
            amount    = entity.amount,
            merchant  = entity.merchant,
            isDebit   = entity.type == "Expense",
            category  = entity.category,
            isRecurring = entity.typedCategory == com.anantva.tether.data.local.entity.TxnCategory.RECURRING,
            countdown = 15
        )
        startCountdown()
    }

    fun updateAmount(value: Double) {
        _uiState.value = _uiState.value.copy(amount = value)
    }

    fun updateMerchant(value: String) {
        _uiState.value = _uiState.value.copy(merchant = value)
    }

    fun updateCategory(value: String) {
        _uiState.value = _uiState.value.copy(category = value)
    }

    fun toggleRecurring() {
        _uiState.value = _uiState.value.copy(isRecurring = !_uiState.value.isRecurring)
    }

    fun toggleType() {
        _uiState.value = _uiState.value.copy(isDebit = !_uiState.value.isDebit)
    }

    fun confirm() {
        countdownJob?.cancel()
        confirmAndClose()
    }

    fun snooze() {
        countdownJob?.cancel()
        snoozeCurrent()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            repeat(15) { i ->
                delay(1000L)
                _uiState.value = _uiState.value.copy(countdown = 14 - i)
            }
            // No auto-confirm. If the user doesn’t confirm, keep it pending and hide for now.
            snoozeCurrent()
        }
    }

    private fun snoozeCurrent() {
        val id = _uiState.value.id
        if (id != 0L) snoozeStore.snoozeUserDismissedSheet(id)
        visiblePendingId = null
        _uiState.value = PendingTransactionUiState()
    }

    private fun confirmAndClose() {
        val state = _uiState.value
        viewModelScope.launch {
            Log.d("TetherTxn", "Saving transaction started")
            val txnCategory = if (state.isRecurring) com.anantva.tether.data.local.entity.TxnCategory.RECURRING.toDbValue() else com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
            val success = tetherRepository.confirmAndUpdateTransaction(
                id         = state.id,
                amount      = state.amount,
                merchant    = state.merchant,
                type        = if (state.isDebit) "Expense" else "Credit",
                category    = state.category,
                txnCategory = txnCategory
            )
            if (success) {
                Log.d("TetherTxn", "Transaction saved")
                _toastEvent.send(TransactionToastEvent.Success)
            } else {
                Log.e("TetherTxn", "Error")
                _toastEvent.send(TransactionToastEvent.Failure("Error"))
            }
            snoozeStore.clearAllForTransaction(state.id)
            visiblePendingId = null
            _uiState.value = PendingTransactionUiState()
        }
    }

    fun deleteTransaction() {
        countdownJob?.cancel()
        val id = _uiState.value.id
        if (id != 0L) {
            viewModelScope.launch {
                tetherRepository.deletePendingTransaction(id)
                snoozeStore.clearAllForTransaction(id)
                visiblePendingId = null
                _uiState.value = PendingTransactionUiState()
            }
        }
    }
}
