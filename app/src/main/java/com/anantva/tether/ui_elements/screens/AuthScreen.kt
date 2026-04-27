package com.anantva.tether.ui_elements.screens

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anantva.tether.auth.FirebaseAuthManager
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager = remember { FirebaseAuthManager() },
    forceReauth: Boolean = false,
    onLoginSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    if (activity == null) return

    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var didComplete by remember { mutableStateOf(false) }

    // When enabling cloud sync, we always want the user to explicitly pick an account.
    LaunchedEffect(forceReauth) {
        if (forceReauth) {
            authManager.signOut()
            // Best-effort: clear cached Google account so the chooser can appear again.
            authManager.signOutGoogle(context)
        }
    }

    // OTP auto-verification signs in without hitting our explicit callbacks.
    DisposableEffect(Unit) {
        val firebaseAuth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { fa ->
            if (!didComplete && fa.currentUser != null) {
                didComplete = true
                isLoading = false
                onLoginSuccess()
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        Log.e("TetherAuth", "Google sign-in resultCode=${result.resultCode} dataNull=${data == null}")

        // Google Play services sometimes fails before returning a proper intent back to the app.
        // In that case, show an actionable error instead of silently doing nothing.
        val safeIntent = data ?: Intent()
        isLoading = true
        authManager.handleGoogleSignInResult(
            context = context,
            data = safeIntent,
            onSuccess = {
                if (didComplete) return@handleGoogleSignInResult
                didComplete = true
                isLoading = false
                Toast.makeText(context, "Login Success", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
            },
            onError = { error ->
                isLoading = false
                errorMessage = error
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (onCancel != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = onCancel) { Text("Back") }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "Enable Cloud Sync",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sign in to sync your data across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            errorMessage = null
                            if (!authManager.isGoogleSignInConfigured(context)) {
                                errorMessage = "Google Sign-In isn’t configured for this build. Use Phone OTP for now."
                                return@Button
                            }
                            isLoading = true
                            googleLauncher.launch(authManager.getGoogleIntent(context))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Continue with Google")
                    }

                    if (!authManager.isGoogleSignInConfigured(context)) {
                        Text(
                            text = "Google Sign-In isn’t configured in Firebase yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number (+91...)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (phoneNumber.isBlank()) {
                                errorMessage = "Enter phone number"
                                return@Button
                            }
                            errorMessage = null
                            isLoading = true

                            authManager.sendOtp(
                                phoneNumber = phoneNumber,
                                activity = activity,
                                onCodeSent = { id ->
                                    isLoading = false
                                    verificationId = id
                                    Toast.makeText(context, "OTP Sent", Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    isLoading = false
                                    errorMessage = error
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Send OTP")
                    }

                    if (verificationId != null) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it },
                            label = { Text("Enter OTP") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (otpCode.isBlank()) {
                                    errorMessage = "Enter OTP"
                                    return@Button
                                }
                                errorMessage = null
                                isLoading = true

                                authManager.verifyOtp(
                                    verificationId = verificationId!!,
                                    otp = otpCode,
                                    onSuccess = {
                                        isLoading = false
                                        Toast.makeText(context, "Login Success", Toast.LENGTH_SHORT).show()
                                        onLoginSuccess()
                                    },
                                    onError = { error ->
                                        isLoading = false
                                        errorMessage = error
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Filled.Sms, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Verify OTP")
                        }
                    }
                }
            }

            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(4.dp))
                CircularProgressIndicator()
            }
        }
    }
}
