package com.anantva.tether.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.transactionpopup.PendingSnoozeStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PendingTransactionNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var tetherRepository: TetherRepository
    @Inject lateinit var snoozeStore: PendingSnoozeStore
    @Inject lateinit var notificationHelper: PendingTransactionNotificationHelper

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        if (transactionId == -1L) return

        if (intent.action == ACTION_NOTIFICATION_DISMISSED) {
            snoozeStore.clearNotificationSuppress(transactionId)
            notificationHelper.cancel(transactionId)
            return
        }

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CONFIRM -> {
                        val entity = tetherRepository.getTransactionById(transactionId)
                        if (entity != null && entity.status == "PENDING") {
                            tetherRepository.confirmAndUpdateTransaction(
                                id = entity.transactionId,
                                amount = entity.amount,
                                merchant = entity.merchant,
                                type = entity.type
                            )
                        }
                        snoozeStore.clearAllForTransaction(transactionId)
                    }
                    ACTION_DELETE -> {
                        tetherRepository.deletePendingTransaction(transactionId)
                        snoozeStore.clearAllForTransaction(transactionId)
                    }
                }
            } finally {
                notificationHelper.cancel(transactionId)
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "com.anantva.tether.EXTRA_PENDING_TXN_ID"
        const val ACTION_CONFIRM = "com.anantva.tether.action.PENDING_TXN_CONFIRM"
        const val ACTION_DELETE = "com.anantva.tether.action.PENDING_TXN_DELETE"
        const val ACTION_NOTIFICATION_DISMISSED = "com.anantva.tether.action.PENDING_TXN_NOTIFICATION_DISMISSED"
    }
}
