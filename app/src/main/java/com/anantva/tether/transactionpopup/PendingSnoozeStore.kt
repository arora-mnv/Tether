package com.anantva.tether.transactionpopup

import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two independent flags:
 * - **User sheet snooze**: user hid the dashboard bottom sheet for this id (countdown / dismiss).
 * - **Active notification suppress**: background confirmation notification is posted; blocks the
 *   auto bottom sheet only until the app is foregrounded, the notification is dismissed, or an
 *   action runs — never meant to persist like a user snooze.
 */
@Singleton
class PendingSnoozeStore @Inject constructor() {
    private val userSnoozedIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val activeNotificationSuppressIds = Collections.synchronizedSet(mutableSetOf<Long>())

    fun isBlockedFromAutoSheet(id: Long): Boolean =
        userSnoozedIds.contains(id) || activeNotificationSuppressIds.contains(id)

    fun snoozeUserDismissedSheet(id: Long) {
        userSnoozedIds.add(id)
    }

    fun clearUserSnooze(id: Long) {
        userSnoozedIds.remove(id)
    }

    fun suppressWhileNotificationActive(id: Long) {
        activeNotificationSuppressIds.add(id)
    }

    fun clearNotificationSuppress(id: Long) {
        activeNotificationSuppressIds.remove(id)
    }

    fun clearAllNotificationSuppress() {
        activeNotificationSuppressIds.clear()
    }

    fun clearAllForTransaction(id: Long) {
        userSnoozedIds.remove(id)
        activeNotificationSuppressIds.remove(id)
    }
}
