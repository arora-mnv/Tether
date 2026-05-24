package com.anantva.tether.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.auth.PhoneAuthProvider
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class FirebaseAuthManager {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private var phoneCallback: PhoneAuthProvider.OnVerificationStateChangedCallbacks? = null

    /**
     * Useful for debugging the common Google sign-in failure:
     * ApiException statusCode=10 (DEVELOPER_ERROR) which usually means SHA-1 / package name mismatch
     * in Firebase or Google Cloud OAuth client.
     */
    fun getAppSigningSha1(context: Context): String? {
        return try {
            val pkg = context.packageName
            val pm = context.packageManager

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo ?: return null
                val sigs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                sigs.map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }.orEmpty()
            }

            val first = signatures.firstOrNull() ?: return null
            val md = MessageDigest.getInstance("SHA1")
            val digest = md.digest(first)
            digest.joinToString(":") { b -> "%02X".format(b) }
        } catch (t: Throwable) {
            Log.w("TetherAuth", "Failed to compute SHA-1", t)
            null
        }
    }

    fun isGoogleSignInConfigured(context: Context): Boolean {
        val webClientId = context.getString(com.anantva.tether.R.string.default_web_client_id).trim()
        return webClientId.isNotBlank() && webClientId != "YOUR_DEFAULT_WEB_CLIENT_ID"
    }

    // 🔵 Configure Google Sign-In
    @Suppress("DEPRECATION")
    private fun getGoogleClient(context: Context): GoogleSignInClient {
        if (!isGoogleSignInConfigured(context)) {
            throw IllegalStateException(
                "Google sign-in is not configured. Replace default_web_client_id and re-download google-services.json with OAuth clients."
            )
        }
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
        context: Context,
        data: Intent,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                onError("Google ID Token is null")
                return
            }
            firebaseAuthWithGoogle(idToken, onSuccess, onError)
        } catch (e: ApiException) {
            // Common: 10 (DEVELOPER_ERROR) when SHA-1/package/oauth is misconfigured
            val sha1 = getAppSigningSha1(context)
            val suffix = if (e.statusCode == 10 && !sha1.isNullOrBlank()) {
                "\n\nFix: add this SHA-1 to Firebase Console -> Project settings -> Your apps -> SHA certificate fingerprints:\n$sha1"
            } else {
                ""
            }
            onError("Google sign-in failed (code=${e.statusCode}): ${e.message ?: "ApiException"}$suffix")
        } catch (e: Exception) {
            onError("Google sign-in failed: ${e.message ?: "Unknown error"}")
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

    fun getCurrentUserName(): String? = auth.currentUser?.displayName

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun getCurrentUserPhone(): String? = auth.currentUser?.phoneNumber

    fun getCurrentUserPhotoUrl(): String? = auth.currentUser?.photoUrl?.toString()

    fun signOut() {
        auth.signOut()
    }

    @Suppress("DEPRECATION")
    fun signOutGoogle(context: Context) {
        try {
            // Use a minimal config; we just want to clear cached account selection.
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, options).signOut()
        } catch (_: Throwable) {
            // Best-effort only.
        }
    }
}
