package com.anantva.tether

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.AuthRepository
import com.anantva.tether.state.SplashViewModel
import com.anantva.tether.ui.theme.TetherTheme
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui_elements.screens.AuthViewModel
import com.anantva.tether.ui_elements.screens.DashboardScreen
import com.anantva.tether.ui_elements.screens.PersonalityDetailScreen
import com.anantva.tether.ui_elements.screens.NameInputScreen
import com.anantva.tether.ui_elements.screens.OnboardingScreen
import com.anantva.tether.ui_elements.screens.ReceiptImportBottomSheet
import com.anantva.tether.ui_elements.screens.ReceiptImportViewModel
import com.anantva.tether.ui_elements.screens.SplashScreen
import com.anantva.tether.ui_elements.screens.TransactionToastEvent
import com.anantva.tether.ui_elements.screens.setup.SetupWizardScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var authRepository: AuthRepository

    private val authViewModel: AuthViewModel by viewModels()
    private val receiptImportViewModel: ReceiptImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleShareIntent(intent)

        setContent {
            val splashViewModel: SplashViewModel = hiltViewModel()
            val splashState by splashViewModel.state.collectAsState()
            val isOnline by splashViewModel.isOnline.collectAsState()

            TetherTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F0F))
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        val context = androidx.compose.ui.platform.LocalContext.current

                        var startDestination by remember { mutableStateOf<String?>(null) }

                        LaunchedEffect(Unit) {
                            val onboardingCompleted = preferencesRepository.hasCompletedOnboarding.first()
                            val setupCompleted = preferencesRepository.hasCompletedSetup.first()

                            startDestination = when {
                                !onboardingCompleted -> "onboarding"
                                !setupCompleted -> "setup"
                                else -> "dashboard"
                            }
                        }

                        val snackbarHostState = remember { SnackbarHostState() }
                        var previousOnline by remember { mutableStateOf(true) }

                        LaunchedEffect(isOnline) {
                            if (previousOnline && !isOnline) {
                                snackbarHostState.showSnackbar("You're offline. Using local data.")
                            } else if (!previousOnline && isOnline) {
                                snackbarHostState.showSnackbar("Back online. Sync will resume automatically.")
                            }
                            previousOnline = isOnline
                        }

                        val importState by receiptImportViewModel.uiState.collectAsState()

                        LaunchedEffect(receiptImportViewModel) {
                            receiptImportViewModel.toastEvent.collect { event ->
                                val message = when (event) {
                                    TransactionToastEvent.Success -> "Transaction saved"
                                    is TransactionToastEvent.Failure -> "Failed: ${event.message}"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }

                        if (importState.isVisible) {
                            ReceiptImportBottomSheet(
                                state = importState,
                                onAmountChange = receiptImportViewModel::updateAmount,
                                onMerchantChange = receiptImportViewModel::updateMerchant,
                                onCategoryChange = receiptImportViewModel::updateCategory,
                                onToggleType = receiptImportViewModel::toggleType,
                                onConfirm = receiptImportViewModel::confirm,
                                onDismiss = receiptImportViewModel::dismiss
                            )
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            val launchedDest = startDestination
                            if (launchedDest != null) {
                                NavHost(
                                    navController = navController,
                                    startDestination = launchedDest
                                ) {
                                    composable("onboarding") {
                                        val scope = rememberCoroutineScope()
                                        OnboardingScreen(
                                            onComplete = {
                                                scope.launch {
                                                    preferencesRepository.setHasCompletedOnboarding(true)
                                                }
                                                navController.navigate("setup") {
                                                    popUpTo("onboarding") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                    composable("setup") {
                                        SetupWizardScreen(
                                            onSetupComplete = {
                                                navController.navigate("dashboard") {
                                                    popUpTo("setup") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                    composable("auth") {
                                        AuthScreen(
                                            onLoginSuccess = {
                                                navController.navigate("dashboard") {
                                                    popUpTo("auth") { inclusive = true }
                                                }
                                            },
                                            onNameRequired = {
                                                navController.navigate("nameInput") {
                                                    popUpTo("auth") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                    composable("nameInput") {
                                        NameInputScreen(
                                            onNameSaved = {
                                                navController.navigate("dashboard") {
                                                    popUpTo("nameInput") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                    composable("dashboard") {
                                        DashboardScreen(
                                            onNavigateToPersonalityDetail = {
                                                navController.navigate("personalityDetail")
                                            }
                                        )
                                    }
                                    composable(
                                        "personalityDetail",
                                        enterTransition = {
                                            fadeIn(animationSpec = tween(400)) + scaleIn(
                                                initialScale = 0.92f,
                                                animationSpec = tween(400)
                                            )
                                        },
                                        exitTransition = {
                                            fadeOut(animationSpec = tween(300)) + scaleOut(
                                                targetScale = 0.95f,
                                                animationSpec = tween(300)
                                            )
                                        }
                                    ) {
                                        PersonalityDetailScreen(
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }
                            }

                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                                snackbar = { data ->
                                    Snackbar(
                                        snackbarData = data,
                                        containerColor = Color(0xFF2A2A2A),
                                        contentColor = Color(0xFFA0A0A0),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = !splashState.isAppReady,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(800))
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        Log.d("TetherShare", "Action: ${intent?.action}, Type: ${intent?.type}")
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val imageUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            if (imageUri != null) {
                Log.d("TetherShare", "Received image: $imageUri")
                Toast.makeText(this, "Screenshot received", Toast.LENGTH_SHORT).show()
                receiptImportViewModel.onReceiptShared(imageUri, contentResolver)
            } else {
                Log.d("TetherShare", "EXTRA_STREAM is null")
            }
        }
    }
}
