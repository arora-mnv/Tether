package com.anantva.tether.ui_elements.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.AuthRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val isCloudStorage: Boolean = false,
    val isAuthenticated: Boolean = false,
    val streakDays: Int = 0,
    val personality: String = "Forming",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val authInProgress: Boolean = false
)

private data class TransientState(
    val isSaving: Boolean,
    val saveSuccess: Boolean,
    val authInProgress: Boolean,
    val isAuthenticated: Boolean
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val authManager: FirebaseAuthManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    private val _saveSuccess = MutableStateFlow(false)
    private val _authInProgress = MutableStateFlow(false)

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
            preferencesRepository.streakDays,
            combine(
                _isSaving,
                _saveSuccess,
                _authInProgress,
                authRepository.observeAuthState()
            ) { isSaving, saveSuccess, authInProgress, isAuth ->
                TransientState(isSaving, saveSuccess, authInProgress, isAuth)
            }
        ) { user, email, cloud, streak, trans ->
            ProfileUiState(
                name = user?.name?.takeIf { it.isNotBlank() } ?: "",
                email = user?.email?.takeIf { it.isNotBlank() } ?: email,
                phone = user?.phone?.takeIf { it.isNotBlank() } ?: "",
                isCloudStorage = cloud,
                isAuthenticated = trans.isAuthenticated,
                streakDays = streak,
                isSaving = trans.isSaving,
                saveSuccess = trans.saveSuccess,
                authInProgress = trans.authInProgress
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileUiState()
        )

    fun save(name: String, email: String, phone: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                userRepository.saveUser(name = name, phone = phone, email = email)
                _isSaving.value = false
                _saveSuccess.value = true
                delay(2000)
                _saveSuccess.value = false
            } catch (_: Exception) {
                _isSaving.value = false
            }
        }
    }

    fun enableCloudSync() {
        viewModelScope.launch {
            preferencesRepository.setCloudStorageEnabled(true)
            userRepository.loadCurrentUser()
        }
    }

    fun disableCloudSync() {
        viewModelScope.launch {
            userRepository.clearUser()
            authRepository.logout()
            authManager.signOut()
            preferencesRepository.setCloudStorageEnabled(false)
        }
    }

    fun setAuthInProgress(inProgress: Boolean) {
        _authInProgress.value = inProgress
    }

    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }
}
