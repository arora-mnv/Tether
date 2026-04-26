package com.anantva.tether.ui_elements.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anantva.tether.auth.FirebaseAuthManager

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager = remember { FirebaseAuthManager() },
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            isLoading = true
            authManager.handleGoogleSignInResult(
                data = data,
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
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Login to Enable Cloud Sync",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Google Login
        Button(
            onClick = {
                errorMessage = null
                val intent = authManager.getGoogleIntent(context)
                googleLauncher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Divider()

        Spacer(modifier = Modifier.height(24.dp))

        // Phone Number Input
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number (+91...)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send OTP")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OTP Input
        if (verificationId != null) {
            OutlinedTextField(
                value = otpCode,
                onValueChange = { otpCode = it },
                label = { Text("Enter OTP") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verify OTP")
            }
        }

        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
        }
    }
}
