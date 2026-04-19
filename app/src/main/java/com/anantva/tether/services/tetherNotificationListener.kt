package com.anantva.tether.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.model.TransactionType
import com.anantva.tether.data.parser.DeduplicationEngine
import com.anantva.tether.data.parser.TransactionParser
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.lifecycle.AppForegroundTracker
import com.anantva.tether.transactionpopup.PendingSnoozeStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class TetherNotificationListenerService : NotificationListenerService() {

    @Inject lateinit var parser:     TransactionParser
    @Inject lateinit var dedup:      DeduplicationEngine
    @Inject lateinit var repository: TetherRepository
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var appForegroundTracker: AppForegroundTracker
    @Inject lateinit var pendingTransactionNotificationHelper: PendingTransactionNotificationHelper
    @Inject lateinit var snoozeStore: PendingSnoozeStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName
        if (packageName !in parser.knownBankingPackages) return

        val extras  = sbn.notification?.extras ?: return
        val title   = extras.getString("android.title") ?: ""
        val body    = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val fullText = when {
            bigText.isNotBlank() -> bigText
            body.isNotBlank()    -> "$title\n$body"
            else                 -> return
        }

        serviceScope.launch {
            if (!preferencesRepository.notificationsEnabled.first()) return@launch
            val parsed = parser.parse(fullText, packageName) ?: return@launch
            if (dedup.isDuplicate(parsed)) return@launch

            // Stage as PENDING in the DB (same table/schema); not counted as confirmed until the user confirms.
            val transactionId = parsed.detectedAt
            val entity = TransactionEntity(
                transactionId = transactionId,
                amount        = parsed.amount,
                merchant      = parsed.merchant,
                type          = if (parsed.type == TransactionType.DEBIT) "Expense" else "Credit",
                source        = "Notification",
                date          = parsed.detectedAt,
                status        = "PENDING"
            )
            repository.addPendingTransactionFromNotification(entity)

            if (appForegroundTracker.isInForegroundNow()) {
                // Room → PendingTransactionViewModel shows TransactionConfirmationSheet only.
                return@launch
            }

            // Background: notification only; suppress auto bottom sheet for this id until dismiss / foreground / action.
            snoozeStore.suppressWhileNotificationActive(transactionId)
            pendingTransactionNotificationHelper.showPendingConfirmation(entity)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[Job]?.cancel()
    }
}
