package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.data.local.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isCloudStorage: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(
            preferencesRepository.userName,
            preferencesRepository.userEmail,
            preferencesRepository.userPhone,
            preferencesRepository.isCloudStorage
        ) { name, email, phone, cloud ->
            ProfileUiState(
                name = name,
                email = email,
                phone = phone,
                isCloudStorage = cloud
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )

    fun save(name: String, email: String, phone: String) {
        viewModelScope.launch {
            preferencesRepository.updateUserProfile(name = name, email = email, phone = phone)
        }
    }

    fun setCloudStorage(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCloudStorageEnabled(enabled)
        }
    }
}

