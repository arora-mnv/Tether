package com.anantva.tether.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anantva.tether.MainActivity
import com.anantva.tether.R
import com.anantva.tether.data.local.entity.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingTransactionNotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    fun showPendingConfirmation(entity: TransactionEntity) {
        ensureChannel()
        val nm = NotificationManagerCompat.from(appContext)
        val nid = notificationId(entity.transactionId)

        val confirmPi = actionPendingIntent(entity.transactionId, PendingTransactionNotificationReceiver.ACTION_CONFIRM, 1)
        val deletePi = actionPendingIntent(entity.transactionId, PendingTransactionNotificationReceiver.ACTION_DELETE, 2)
        val dismissPi = actionPendingIntent(
            entity.transactionId,
            PendingTransactionNotificationReceiver.ACTION_NOTIFICATION_DISMISSED,
            3
        )

        val launchApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPi = PendingIntent.getActivity(
            appContext,
            nid + REQUEST_FULL_SCREEN,
            launchApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountLabel = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(entity.amount)
        val typeLabel = if (entity.type == "Expense") "Debit (expense)" else "Credit"
        val bigBody = buildString {
            appendLine(entity.merchant)
            appendLine(amountLabel)
            appendLine(typeLabel)
            append("Source: ${entity.source}")
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Confirm transaction")
            .setContentText("${entity.merchant} · $amountLabel · $typeLabel")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setDeleteIntent(dismissPi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .addAction(0, "Confirm", confirmPi)
            .addAction(0, "Delete", deletePi)
            .build()

        try {
            nm.notify(nid, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked by the user (Android 13+).
        }
    }

    fun cancel(transactionId: Long) {
        NotificationManagerCompat.from(appContext).cancel(notificationId(transactionId))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pending transactions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Confirm transactions detected in the background"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setBypassDnd(false)
            }
        }
        mgr.createNotificationChannel(channel)
    }

    private fun actionPendingIntent(transactionId: Long, action: String, requestSalt: Int): PendingIntent {
        val intent = Intent(appContext, PendingTransactionNotificationReceiver::class.java).apply {
            this.action = action
            putExtra(PendingTransactionNotificationReceiver.EXTRA_TRANSACTION_ID, transactionId)
        }
        val requestCode = notificationId(transactionId) + requestSalt
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "pending_transaction_confirm"

        private const val REQUEST_FULL_SCREEN = 10_000

        fun notificationId(transactionId: Long): Int =
            (transactionId xor (transactionId ushr 32)).toInt()
    }
}
