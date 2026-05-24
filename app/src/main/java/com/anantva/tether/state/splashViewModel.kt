package com.anantva.tether.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppStartupState(
    val isAppReady: Boolean = false,
    val readiness: StartupReadiness = StartupReadiness()
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val startupCoordinator: AppStartupCoordinator,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _state = MutableStateFlow(AppStartupState())
    val state: StateFlow<AppStartupState> = _state.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    init {
        startupCoordinator.start()

        viewModelScope.launch {
            startupCoordinator.readiness.collect { readiness ->
                _state.update { it.copy(readiness = readiness, isAppReady = readiness.isReady) }
            }
        }
    }
}
