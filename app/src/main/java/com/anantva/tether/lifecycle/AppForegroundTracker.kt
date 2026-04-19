package com.anantva.tether.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.anantva.tether.transactionpopup.PendingSnoozeStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundTracker @Inject constructor(
    private val pendingSnoozeStore: PendingSnoozeStore
) {

    private val _isInForeground = MutableStateFlow(false)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _isInForeground.value = true
                    pendingSnoozeStore.clearAllNotificationSuppress()
                }

                override fun onStop(owner: LifecycleOwner) {
                    _isInForeground.value = false
                }
            }
        )
    }

    fun isInForegroundNow(): Boolean = _isInForeground.value
}
