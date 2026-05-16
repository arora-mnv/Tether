package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isCloudStorage: Boolean = false,
    val streakDays: Int = 0,
    val personality: String = "Forming"
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager
) : ViewModel() {

    val userUiState = userRepository.userUiState

    init {
        viewModelScope.launch {
            if (preferencesRepository.isCloudStorage.first()) {
                userRepository.loadCurrentUser()
            }
        }
    }

    val uiState: StateFlow<ProfileUiState> =
        combine(
            userRepository.user,
            preferencesRepository.userEmail,
            preferencesRepository.isCloudStorage,
            preferencesRepository.streakDays
        ) { user, email, cloud, streak ->
            ProfileUiState(
                name = user?.name?.takeIf { it.isNotBlank() } ?: "",
                email = user?.email?.takeIf { it.isNotBlank() } ?: email,
                phone = user?.phone?.takeIf { it.isNotBlank() } ?: "",
                isCloudStorage = cloud,
                streakDays = streak
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )

    fun save(name: String, email: String, phone: String) {
        viewModelScope.launch {
            userRepository.saveUser(name = name, phone = phone, email = email)
        }
    }

    fun setCloudStorage(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCloudStorageEnabled(enabled)
        }
    }
}
