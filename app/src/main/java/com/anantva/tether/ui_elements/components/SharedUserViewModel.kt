package com.anantva.tether.ui_elements.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserUiState(
    val displayName: String = "there",
    val profileImageUrl: String? = null,
    val isCloudSyncEnabled: Boolean = false,
    val streak: Int = 0,
    val stressLevel: Float = 0f
)

@HiltViewModel
class SharedUserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            userRepository.loadCurrentUser()
        }
    }

    val uiState: StateFlow<UserUiState> = combine(
        userRepository.user,
        userPreferencesRepository.isCloudStorage,
        userPreferencesRepository.streakDays,
    ) { userData, isCloudSync, streak ->
        UserUiState(
            displayName = userData?.displayName ?: "there",
            profileImageUrl = userData?.photoUrl,
            isCloudSyncEnabled = isCloudSync,
            streak = streak,
            stressLevel = 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserUiState()
    )
}
