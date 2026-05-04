package com.anantva.tether.data.repository

import com.anantva.tether.auth.FirebaseAuthManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AuthResult(
    val success: Boolean,
    val errorMessage: String? = null
)

@Singleton
class AuthRepository @Inject constructor(
    private val authManager: FirebaseAuthManager
) {
    private val auth = FirebaseAuth.getInstance()

    suspend fun signUp(email: String, password: String): AuthResult {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            AuthResult(success = true)
        } catch (e: FirebaseAuthException) {
            AuthResult(success = false, errorMessage = e.errorCode.toReadableError())
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = e.message ?: "Sign up failed")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            AuthResult(success = true)
        } catch (e: FirebaseAuthException) {
            AuthResult(success = false, errorMessage = e.errorCode.toReadableError())
        } catch (e: Exception) {
            AuthResult(success = false, errorMessage = e.message ?: "Login failed")
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Delegate to existing FirebaseAuthManager for Google/Phone auth
    fun getFirebaseAuthManager(): FirebaseAuthManager = authManager
}

private fun String.toReadableError(): String {
    return when (this) {
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Email already registered"
        "ERROR_INVALID_EMAIL" -> "Invalid email format"
        "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters"
        "ERROR_USER_NOT_FOUND" -> "No account found with this email"
        "ERROR_WRONG_PASSWORD" -> "Incorrect password"
        "ERROR_USER_DISABLED" -> "This account has been disabled"
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again later"
        "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your connection"
        else -> "Authentication failed: $this"
    }
}
