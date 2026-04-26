package com.anantva.tether.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class FirebaseAuthManager {

    private val auth = FirebaseAuth.getInstance()
    private var phoneCallback: PhoneAuthProvider.OnVerificationStateChangedCallbacks? = null

    // 🔵 Configure Google Sign-In
    private fun getGoogleClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(com.anantva.tether.R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    // 🔵 Get intent for launcher
    fun getGoogleIntent(context: Context): Intent {
        return getGoogleClient(context).signInIntent
    }

    // 🔵 Handle result from launcher
    fun handleGoogleSignInResult(
        data: Intent,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.result
            val idToken = account.idToken
            if (idToken == null) {
                onError("Google ID Token is null")
                return
            }
            firebaseAuthWithGoogle(idToken, onSuccess, onError)
        } catch (e: Exception) {
            onError(e.message ?: "Google sign-in failed")
        }
    }

    // 🔐 Firebase auth with Google
    private fun firebaseAuthWithGoogle(
        idToken: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onSuccess()
                else onError(task.exception?.message ?: "Firebase auth failed")
            }
    }

    // 📱 Send OTP
    fun sendOtp(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (String) -> Unit,   // renamed internally to avoid shadowing the override
        onError: (String) -> Unit
    ) {
        // Capture lambdas as local vals so the anonymous class can close over them
        // without any name collision with the override methods below.
        val codeSentCallback: (String) -> Unit = onCodeSent
        val errorCallback: (String) -> Unit    = onError

        phoneCallback = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            // Auto-retrieval path: Firebase verified the OTP silently
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // No explicit onSuccess here — auto-verify just signs in;
                            // the caller should observe FirebaseAuth.currentUser instead.
                        } else {
                            errorCallback(task.exception?.message ?: "Auto-verify failed")
                        }
                    }
            }

            // Fix 1: must be FirebaseException, not Exception
            override fun onVerificationFailed(e: FirebaseException) {
                errorCallback(e.message ?: "Verification failed")
            }

            // Fix 2: use the captured local val — avoids shadowing the override name
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                codeSentCallback(verificationId)
            }
        }

        // Fix 3: use the modern PhoneAuthOptions builder instead of the deprecated
        // PhoneAuthProvider.getInstance().verifyPhoneNumber() overload
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(phoneCallback!!)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // 🔐 Verify OTP
    fun verifyOtp(
        verificationId: String,
        otp: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, otp)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) onSuccess()
                else onError(task.exception?.message ?: "Invalid OTP")
            }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun signOut() {
        auth.signOut()
    }
}