package com.anantva.tether.data.repository

import android.util.Log
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TetherUserRepo"

data class UserData(
    val uid: String = "",
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val photoUrl: String? = null
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "there"
}

// Removed old UserUiState here, it's now managed by SharedUserViewModel
@Singleton
class UserRepository @Inject constructor(
    private val authManager: FirebaseAuthManager,
    private val firestoreRepository: FirestoreRepository,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val _user = MutableStateFlow(UserData())
    val user: StateFlow<UserData?> = _user.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Call this after any successful login (Phone, Google, Email).
     * Loads user data from Firebase Auth + Firestore + DataStore
     * and merges into a single UserData StateFlow.
     */
    fun loadCurrentUser() {
        scope.launch {
            val uid = authManager.getCurrentUserId()
            val localName = preferencesRepository.userName.first()
            val localEmail = preferencesRepository.userEmail.first()
            val localPhone = preferencesRepository.userPhone.first()

            if (uid == null) {
                Log.d(TAG, "loadCurrentUser: local-only mode, using DataStore identity")
                _user.value = UserData(
                    name = localName.takeIf { it.isNotBlank() },
                    phone = localPhone.takeIf { it.isNotBlank() },
                    email = localEmail.takeIf { it.isNotBlank() }
                )
                return@launch
            }

            var name: String? = null
            var phone: String? = null
            var email: String? = null
            var photoUrl = authManager.getCurrentUserPhotoUrl()

            // Try Firestore first
            try {
                val profile = firestoreRepository.getUserProfile(uid)
                if (profile != null) {
                    name = profile.name.takeIf { it.isNotBlank() }
                    phone = profile.phoneNumber.takeIf { it.isNotBlank() }
                    if (photoUrl.isNullOrBlank()) {
                        photoUrl = profile.photoUrl.takeIf { it.isNotBlank() }
                    }
                    Log.d(TAG, "loadCurrentUser: loaded from Firestore name=$name phone=$phone")
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadCurrentUser: Firestore fetch failed for uid=$uid: ${e.message}", e)
            }

            if (name.isNullOrBlank()) {
                name = localName.takeIf { it.isNotBlank() }
            }
            if (phone.isNullOrBlank()) {
                phone = localPhone.takeIf { it.isNotBlank() }
            }
            if (email.isNullOrBlank()) {
                email = localEmail.takeIf { it.isNotBlank() }
            }

            if (name.isNullOrBlank()) {
                name = authManager.getCurrentUserName()
            }
            if (email.isNullOrBlank()) {
                email = authManager.getCurrentUserEmail()
            }
            if (phone.isNullOrBlank()) {
                phone = authManager.getCurrentUserPhone()
            }

            _user.value = UserData(
                uid = uid,
                name = name,
                phone = phone,
                email = email,
                photoUrl = photoUrl
            )
            Log.d(TAG, "loadCurrentUser: final state uid=$uid name=$name")
        }
    }

    /**
     * Save user data to both Firestore and DataStore.
     * Updates the StateFlow immediately so UI reflects changes without delay.
     */
    fun saveUser(name: String? = null, phone: String? = null, email: String? = null) {
        scope.launch {
            val current = _user.value
            val uid = current.uid.takeIf { it.isNotBlank() } ?: authManager.getCurrentUserId()
            if (uid == null) {
                Log.w(TAG, "saveUser: no uid available")
                return@launch
            }

            val newName = name ?: current.name
            val newPhone = phone ?: current.phone
            val newEmail = email ?: current.email

            _user.value = _user.value.copy(
                uid = uid,
                name = newName,
                phone = newPhone,
                email = newEmail
            )

            try {
                preferencesRepository.updateUserProfile(
                    name = newName.orEmpty(),
                    email = newEmail.orEmpty(),
                    phone = newPhone.orEmpty()
                )
            } catch (e: Exception) {
                Log.e(TAG, "saveUser: DataStore update failed: ${e.message}", e)
            }

            try {
                firestoreRepository.saveUserProfile(
                    uid,
                    newName.orEmpty(),
                    newPhone.orEmpty(),
                    current.photoUrl.orEmpty()
                )
                Log.d(TAG, "saveUser: Firestore save success uid=$uid")
            } catch (e: Exception) {
                Log.e(TAG, "saveUser: Firestore save failed: ${e.message}", e)
            }
        }
    }

    /**
     * Clear user state on logout.
     */
    fun clearUser() {
        _user.value = UserData()
        Log.d(TAG, "clearUser: state cleared")
    }

    /**
     * Returns the current cached user (synchronous, for non-compose contexts).
     */
    fun getCachedUser(): UserData? {
        val u = _user.value
        return if (u.uid.isNotBlank()) u else null
    }
}
