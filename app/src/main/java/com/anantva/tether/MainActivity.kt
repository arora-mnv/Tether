package com.anantva.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.data.local.UserPreferencesRepository
import com.anantva.tether.data.repository.TetherRepository
import com.anantva.tether.state.AppStartState
import com.anantva.tether.ui.theme.TetherTheme
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui_elements.screens.AuthViewModel
import com.anantva.tether.ui_elements.screens.DashboardScreen
import com.anantva.tether.ui_elements.screens.NameInputScreen
import com.anantva.tether.ui_elements.screens.OnboardingScreen
import com.anantva.tether.ui_elements.screens.SplashScreen
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
    lateinit var authManager: FirebaseAuthManager

    @Inject
    lateinit var tetherRepository: TetherRepository

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {

        var isComposing = false
        installSplashScreen().setKeepOnScreenCondition { !isComposing }

        super.onCreate(savedInstanceState)

        setContent {
            SideEffect { isComposing = true }

            TetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()

                    // App start state - load from DataStore
                    var appStartState by remember { mutableStateOf(AppStartState()) }
                    var splashStartTime by remember { mutableLongStateOf(0L) }

                    LaunchedEffect(Unit) {
                        splashStartTime = System.currentTimeMillis()

                        val onboardingCompleted = preferencesRepository.hasCompletedOnboarding.first()
                        val setupCompleted = preferencesRepository.hasCompletedSetup.first()

                        val elapsed = System.currentTimeMillis() - splashStartTime
                        val remainingDelay = 3000L - elapsed
                        if (remainingDelay > 0) {
                            kotlinx.coroutines.delay(remainingDelay)
                        }

                        appStartState = AppStartState(
                            isLoading = false,
                            onboardingCompleted = onboardingCompleted,
                            setupCompleted = setupCompleted
                        )
                    }

                    // Cloud sync state
                    val isCloudSyncEnabled by preferencesRepository
                        .isCloudStorage
                        .collectAsState(initial = false)

                    // Auth state from AuthViewModel
                    val authState by authViewModel.uiState.collectAsState()
                    val isLoggedIn = authState.isLoggedIn

                    // Sync with Firestore when user logs in
                    LaunchedEffect(isLoggedIn, isCloudSyncEnabled) {
                        if (isLoggedIn && isCloudSyncEnabled) {
                            val userId = authState.userId
                            if (!userId.isNullOrEmpty()) {
                                tetherRepository.syncLocalWithCloud(userId)
                            }
                        }
                    }

                    // Compute start destination BEFORE NavHost renders
                    val startDestination = when {
                        appStartState.isLoading -> "splash"
                        !appStartState.onboardingCompleted -> "onboarding"
                        !appStartState.setupCompleted -> "setup"
                        else -> "dashboard"
                    }

                    AnimatedContent(
                        targetState = appStartState.isLoading,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "splashTransition"
                    ) { isLoading ->
                        if (isLoading) {
                            SplashScreen()
                        } else {
                            NavHost(
                                navController = navController,
                                startDestination = startDestination
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
                                    LaunchedEffect(isCloudSyncEnabled, isLoggedIn) {
                                        when {
                                            !isCloudSyncEnabled -> {
                                                // Cloud OFF → stay on dashboard
                                            }
                                            isLoggedIn -> {
                                                // Cloud ON + logged in → stay on dashboard
                                            }
                                            else -> {
                                                navController.navigate("auth") {
                                                    popUpTo("dashboard") { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                    DashboardScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
