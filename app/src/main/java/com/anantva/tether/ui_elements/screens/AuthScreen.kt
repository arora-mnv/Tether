package com.anantva.tether.ui_elements.screens

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.repository.AuthRepository
import com.anantva.tether.ui.theme.CardBg
import com.anantva.tether.ui.theme.DarkBg
import com.anantva.tether.ui.theme.GrimeGrey
import com.anantva.tether.ui.theme.GrimeGreyDark
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.TetherWhite
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AuthStep { PhoneInput, OtpInput }

private const val TAG = "TetherAuth"

@Composable
fun AuthScreen(
    authManager: FirebaseAuthManager = remember { FirebaseAuthManager() },
    forceReauth: Boolean = false,
    onLoginSuccess: () -> Unit,
    onNameRequired: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as Activity

    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

    var authStep by remember { mutableStateOf(AuthStep.PhoneInput) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var phoneDigits by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showEmailAuth by remember { mutableStateOf(false) }

    LaunchedEffect(forceReauth) {
        if (forceReauth) {
            authManager.signOut()
            authManager.signOutGoogle(context)
        }
    }

    LaunchedEffect(authState.phoneVerificationStatus) {
        if (authState.phoneVerificationStatus == PhoneVerificationStatus.Verified) {
            val userId = authState.userId
            Log.d(TAG, "phoneVerificationStatus=Verified, uid=$userId, checking profile...")
            if (!userId.isNullOrEmpty()) {
                val hasProfile = authViewModel.checkUserProfile(userId)
                Log.d(TAG, "profile check result: hasProfile=$hasProfile")
                if (hasProfile) {
                    Log.d(TAG, "Navigating to Dashboard (existing user)")
                    onLoginSuccess()
                } else {
                    Log.d(TAG, "Navigating to NameInput (new user)")
                    onNameRequired()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val firebaseAuth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { fa ->
            if (fa.currentUser != null) {
                val userId = fa.currentUser?.uid
                Log.d(TAG, "AuthStateListener: user signed in, uid=$userId")
                if (!userId.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val hasProfile = authViewModel.checkUserProfile(userId)
                        Log.d(TAG, "AuthStateListener: profile check result hasProfile=$hasProfile")
                        if (hasProfile) {
                            Log.d(TAG, "AuthStateListener: navigating to Dashboard")
                            onLoginSuccess()
                        } else {
                            Log.d(TAG, "AuthStateListener: navigating to NameInput")
                            onNameRequired()
                        }
                    }
                }
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        Log.d(TAG, "Google sign-in resultCode=${result.resultCode} dataNull=${data == null}")
        if (data == null) {
            Log.e(TAG, "Google sign-in: null intent data — user may have cancelled")
            return@rememberLauncherForActivityResult
        }
        val safeIntent = data
        authManager.handleGoogleSignInResult(
            context = context,
            data = safeIntent,
            onSuccess = {
                Log.d(TAG, "Google sign-in onSuccess — auth state listener will handle navigation")
            },
            onError = { error ->
                Log.e(TAG, "Google sign-in onError: $error")
                errorMessage = error
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Canvas(modifier = Modifier.fillMaxSize()) {
                val gradient = Brush.radialGradient(
                    colors = listOf(
                        TetherRed.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height * 0.25f),
                    radius = size.width * 0.8f
                )
                drawRect(gradient)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (onCancel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        TextButton(onClick = onCancel) {
                            Text("Back", color = GrimeGrey)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    FloatingCoinsIllustration()

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Catch your money before it disappears",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TetherWhite,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        fontSize = 26.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Do not let your money float away",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GrimeGrey,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (showEmailAuth) {
                    EmailAuthSection(
                        email = email,
                        onEmailChange = { email = it; errorMessage = null },
                        password = password,
                        onPasswordChange = { password = it; errorMessage = null },
                        isSignUp = isSignUp,
                        passwordVisible = passwordVisible,
                        onPasswordToggle = { passwordVisible = !passwordVisible },
                        isLoading = authState.isLoading,
                        errorMessage = errorMessage,
                        context = context,
                        onLogin = {
                            if (email.isBlank()) {
                                errorMessage = "Enter your email"
                                return@EmailAuthSection
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@EmailAuthSection
                            }
                            errorMessage = null
                            val authRepo = AuthRepository(authManager)
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = if (isSignUp) {
                                    authRepo.signUp(email.trim(), password)
                                } else {
                                    authRepo.login(email.trim(), password)
                                }
                                if (result.success) {
                                    onLoginSuccess()
                                } else {
                                    errorMessage = result.errorMessage
                                }
                            }
                        },
                        onToggleSignUp = { isSignUp = !isSignUp; errorMessage = null },
                        onBackToPhone = { showEmailAuth = false; errorMessage = null }
                    )
                } else {
                    PhoneAuthSection(
                        phoneDigits = phoneDigits,
                        onPhoneChange = {
                            if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                                phoneDigits = it
                                errorMessage = null
                            }
                        },
                        otpCode = otpCode,
                        onOtpChange = { otpCode = it; errorMessage = null },
                        verificationId = verificationId,
                        authStep = authStep,
                        isLoading = authState.isLoading,
                        errorMessage = errorMessage,
                        authManager = authManager,
                        activity = activity,
                        context = context,
                        onSendOtp = {
                            val fullPhone = "+91$phoneDigits"
                            authManager.sendOtp(
                                phoneNumber = fullPhone,
                                activity = activity,
                                onCodeSent = { id ->
                                    verificationId = id
                                    authStep = AuthStep.OtpInput
                                },
                                onError = { error ->
                                    errorMessage = error
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        onVerifyOtp = {
                            if (otpCode.isBlank()) {
                                errorMessage = "Enter OTP"
                                return@PhoneAuthSection
                            }
                            val vid = verificationId
                            if (vid == null) {
                                errorMessage = "Session expired. Please request OTP again."
                                return@PhoneAuthSection
                            }
                            errorMessage = null
                            authManager.verifyOtp(
                                verificationId = vid,
                                otp = otpCode,
                                onSuccess = {
                                    Log.d(TAG, "OTP verified successfully")
                                    val userId = authManager.getCurrentUserId()
                                    if (!userId.isNullOrEmpty()) {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            val hasProfile = authViewModel.checkUserProfile(userId)
                                            Log.d(TAG, "onVerifyOtp: profile check hasProfile=$hasProfile")
                                            if (hasProfile) {
                                                Log.d(TAG, "onVerifyOtp: navigating to Dashboard")
                                                onLoginSuccess()
                                            } else {
                                                Log.d(TAG, "onVerifyOtp: navigating to NameInput")
                                                onNameRequired()
                                            }
                                        }
                                    }
                                },
                                onError = { error ->
                                    Log.e(TAG, "OTP verification failed: $error")
                                    errorMessage = error
                                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    DividerWithText("or continue with")

                    Spacer(modifier = Modifier.height(20.dp))

                    GoogleSignInButton(
                        isLoading = authState.isLoading,
                        authManager = authManager,
                        context = context,
                        googleLauncher = googleLauncher,
                        onError = { error ->
                            errorMessage = error
                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    EmailSignInButton(
                        onClick = { showEmailAuth = true; errorMessage = null }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Most people lose track of ₹3,000/month. Not you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrimeGrey,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            AnimatedVisibility(
                visible = authState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = TetherRed,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingCoinsIllustration() {
    Box(
        modifier = Modifier
            .size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val coinRadius = 32.dp.toPx()

            val gradient = Brush.radialGradient(
                colors = listOf(TetherRed, TetherRed.copy(alpha = 0.6f)),
                center = Offset(centerX, centerY),
                radius = coinRadius
            )

            drawCircle(
                brush = gradient,
                radius = coinRadius,
                center = Offset(centerX, centerY)
            )

            drawCircle(
                color = TetherWhite.copy(alpha = 0.12f),
                radius = coinRadius * 0.7f,
                center = Offset(centerX - coinRadius * 0.2f, centerY - coinRadius * 0.2f)
            )

            listOf(
                Triple(centerX - 50.dp.toPx(), centerY - 30.dp.toPx(), 8.dp.toPx()),
                Triple(centerX + 45.dp.toPx(), centerY - 40.dp.toPx(), 6.dp.toPx()),
                Triple(centerX - 35.dp.toPx(), centerY + 40.dp.toPx(), 5.dp.toPx()),
                Triple(centerX + 50.dp.toPx(), centerY + 25.dp.toPx(), 7.dp.toPx())
            ).forEach { (x, y, r) ->
                drawCircle(
                    color = TetherRed.copy(alpha = 0.2f),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }

        Text(
            text = "₹",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TetherWhite
        )
    }
}

@Composable
private fun PhoneAuthSection(
    phoneDigits: String,
    onPhoneChange: (String) -> Unit,
    otpCode: String,
    onOtpChange: (String) -> Unit,
    verificationId: String?,
    authStep: AuthStep,
    isLoading: Boolean,
    errorMessage: String?,
    authManager: FirebaseAuthManager,
    activity: Activity,
    context: android.content.Context,
    onSendOtp: () -> Unit,
    onVerifyOtp: () -> Unit
) {
    val isValidPhone = phoneDigits.length == 10

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .border(
                    width = 1.dp,
                    color = if (errorMessage != null && phoneDigits.isNotEmpty() && phoneDigits.length < 10)
                        MaterialTheme.colorScheme.error
                    else
                        Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+91",
                    fontWeight = FontWeight.Bold,
                    color = TetherRed,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(GrimeGreyDark.copy(alpha = 0.5f)))

                OutlinedTextField(
                    value = phoneDigits,
                    onValueChange = onPhoneChange,
                    placeholder = { Text("Enter 10-digit number", color = GrimeGrey) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = {
                        Icon(Icons.Filled.Phone, contentDescription = null, tint = GrimeGrey)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = TetherRed
                    )
                )
            }
        }

        if (phoneDigits.isNotEmpty() && phoneDigits.length < 10) {
            Text(
                text = "${phoneDigits.length}/10 digits",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        AnimatedVisibility(
            visible = authStep == AuthStep.OtpInput,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                ) {
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = onOtpChange,
                        placeholder = { Text("Enter 6-digit OTP", color = GrimeGrey) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        leadingIcon = {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = GrimeGrey)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = TetherRed
                        )
                    )
                }
            }
        }

        RedButton(
            text = if (authStep == AuthStep.OtpInput) "Verify & Continue" else "Continue",
            onClick = { if (authStep == AuthStep.OtpInput) onVerifyOtp() else onSendOtp() },
            enabled = if (authStep == AuthStep.OtpInput) otpCode.length == 6 && !isLoading else isValidPhone && !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmailAuthSection(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isSignUp: Boolean,
    passwordVisible: Boolean,
    onPasswordToggle: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    context: android.content.Context,
    onLogin: () -> Unit,
    onToggleSignUp: () -> Unit,
    onBackToPhone: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSignUp) "Create account" else "Welcome back",
                style = MaterialTheme.typography.titleMedium,
                color = TetherWhite
            )
            TextButton(onClick = onBackToPhone) {
                Text("Phone", color = TetherRed)
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = { Text("Email", color = GrimeGrey) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = GrimeGrey) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TetherRed.copy(alpha = 0.5f),
                unfocusedBorderColor = GrimeGreyDark.copy(alpha = 0.5f),
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                cursorColor = TetherRed
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = { Text("Password", color = GrimeGrey) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = GrimeGrey) },
            trailingIcon = {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide" else "Show",
                        tint = GrimeGrey
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TetherRed.copy(alpha = 0.5f),
                unfocusedBorderColor = GrimeGreyDark.copy(alpha = 0.5f),
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                cursorColor = TetherRed
            )
        )

        RedButton(
            text = if (isSignUp) "Sign Up" else "Log In",
            onClick = onLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isSignUp) "Already have an account? " else "Don't have an account? ",
                style = MaterialTheme.typography.bodySmall,
                color = GrimeGrey
            )
            Text(
                text = if (isSignUp) "Log In" else "Sign Up",
                style = MaterialTheme.typography.bodySmall,
                color = TetherRed,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onToggleSignUp() }
            )
        }
    }
}

@Composable
private fun RedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.97f,
        animationSpec = tween(150),
        label = "redButtonScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = TetherRed.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TetherRed,
            disabledContainerColor = GrimeGreyDark.copy(alpha = 0.5f)
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = TetherWhite
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    authManager: FirebaseAuthManager,
    context: android.content.Context,
    googleLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onError: (String) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(150),
        label = "googleButtonScale"
    )

    Button(
        onClick = {
            if (!authManager.isGoogleSignInConfigured(context)) {
                Log.w(TAG, "Google Sign-In not configured — check google-services.json and Firebase console")
                onError("Google Sign-In isn't configured")
                return@Button
            }
            Log.d(TAG, "Launching Google Sign-In intent")
            googleLauncher.launch(authManager.getGoogleIntent(context))
        },
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CardBg,
            contentColor = TetherWhite
        ),
        border = BorderStroke(1.dp, GrimeGreyDark.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            tint = TetherWhite,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text("Continue with Google", fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable
private fun EmailSignInButton(
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Use email instead",
            color = GrimeGrey,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DividerWithText(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = GrimeGreyDark.copy(alpha = 0.4f),
            thickness = 1.dp
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = GrimeGrey,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = GrimeGreyDark.copy(alpha = 0.4f),
            thickness = 1.dp
        )
    }
}
