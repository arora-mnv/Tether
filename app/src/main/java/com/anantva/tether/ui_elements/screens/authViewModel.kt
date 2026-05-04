package com.anantva.tether.ui_elements.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.repository.AuthRepository
import com.anantva.tether.data.repository.FirestoreRepository
import com.anantva.tether.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TetherAuth"

enum class PhoneVerificationStatus { Idle, Sending, CodeSent, Verifying, Verified, Error }

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val userId: String? = null,
    val userEmail: String? = null,
    val phoneVerificationStatus: PhoneVerificationStatus = PhoneVerificationStatus.Idle
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepository: FirestoreRepository,
    private val authManager: FirebaseAuthManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { isLoggedIn ->
                if (isLoggedIn) {
                    userRepository.loadCurrentUser()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = isLoggedIn,
                    userId = if (isLoggedIn) authRepository.getCurrentUserId() else null,
                    userEmail = if (isLoggedIn) authRepository.getCurrentUserEmail() else null
                )
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.login(email, password)
            if (!result.success) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.errorMessage
                )
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.signUp(email, password)
            if (!result.success) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.errorMessage
                )
            }
        }
    }

    /**
     * Check if user has a profile in Firestore.
     * Returns true if profile exists (go to Dashboard).
     * Returns false if profile missing (go to NameInput).
     *
     * FAIL-SAFE: On Firestore failure (PERMISSION_DENIED, offline, etc.),
     * logs the error and returns true to route to Dashboard.
     */
    suspend fun checkUserProfile(userId: String): Boolean {
        return try {
            val hasProfile = firestoreRepository.hasUserProfile(userId)
            Log.d(TAG, "checkUserProfile uid=$userId hasProfile=$hasProfile")
            hasProfile
        } catch (e: Exception) {
            Log.e(TAG, "Firestore checkUserProfile failed for uid=$userId: ${e.message}", e)
            true
        }
    }

    /**
     * Save user profile to Firestore after phone login.
     * Stores name, phoneNumber, and createdAt timestamp.
     *
     * FAIL-SAFE: Always calls onDone() even if Firestore save fails,
     * so the user is not stuck on the NameInput screen.
     */
    fun saveUserProfile(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val userId = authManager.getCurrentUserId()
            val phone = authManager.getCurrentUserPhone()
            if (userId != null) {
                userRepository.saveUser(name = name, phone = phone)
                Log.d(TAG, "saveUserProfile done uid=$userId name=$name")
                _uiState.value = _uiState.value.copy(isLoading = false)
                onDone()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No user logged in"
                )
                Log.e(TAG, "saveUserProfile: no userId available")
            }
        }
    }

    fun logout() {
        userRepository.clearUser()
        authRepository.logout()
        _uiState.value = AuthUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
