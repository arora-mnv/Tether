package com.anantva.tether

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.AuthRepository
import com.anantva.tether.ui.theme.TetherTheme
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui_elements.screens.AuthViewModel
import com.anantva.tether.ui_elements.screens.DashboardScreen
import com.anantva.tether.ui_elements.screens.NameInputScreen
import com.anantva.tether.ui_elements.screens.OnboardingScreen
import com.anantva.tether.ui_elements.screens.ReceiptImportBottomSheet
import com.anantva.tether.ui_elements.screens.ReceiptImportViewModel
import com.anantva.tether.ui_elements.screens.SplashScreen
import com.anantva.tether.ui_elements.screens.TransactionToastEvent
import com.anantva.tether.ui_elements.screens.setup.SetupWizardScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
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
        var isReady by mutableStateOf(false)

        installSplashScreen().setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        setContent {
            TetherTheme {
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
                        val isCloudSyncEnabled = preferencesRepository.isCloudStorage.first()
                        val isLoggedIn = authRepository.isLoggedIn()

                        startDestination = when {
                            !onboardingCompleted -> "onboarding"
                            !setupCompleted -> "setup"
                            isCloudSyncEnabled && !isLoggedIn -> "auth"
                            else -> "dashboard"
                        }

                        isReady = true
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
                                DashboardScreen()
                            }
                        }
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
            val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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
