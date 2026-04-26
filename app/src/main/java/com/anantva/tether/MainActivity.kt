package com.anantva.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.anantva.tether.ui.theme.TetherTheme
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui_elements.screens.DashboardScreen
import com.anantva.tether.ui_elements.screens.SplashScreen
import com.anantva.tether.ui_elements.screens.setup.SetupWizardScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var authManager: FirebaseAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {

        var isComposing = false
        installSplashScreen().setKeepOnScreenCondition { !isComposing }

        super.onCreate(savedInstanceState)

        // Check setup completion (blocking once at launch)
        val hasCompletedSetup = runBlocking {
            preferencesRepository.hasCompletedSetup.first()
        }

        setContent {
            SideEffect { isComposing = true }

            TetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val navController = rememberNavController()

                    // Cloud sync state
                    val isCloudSyncEnabled by preferencesRepository
                        .isCloudStorage
                        .collectAsState(initial = false)

                    // Auth state - reactive check
                    var isLoggedIn by remember {
                        mutableStateOf(authManager.isLoggedIn())
                    }

                    // Update isLoggedIn when cloud state changes
                    LaunchedEffect(isCloudSyncEnabled) {
                        if (isCloudSyncEnabled) {
                            isLoggedIn = authManager.isLoggedIn()
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {

                        // ✅ SPLASH
                        composable("splash") {
                            SplashScreen(
                                hasCompletedSetup = hasCompletedSetup,
                                onNavigateToDashboard = {
                                    navController.navigate("dashboard") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                },
                                onNavigateToSetup = {
                                    navController.navigate("setup") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ✅ SETUP
                        composable("setup") {
                            SetupWizardScreen(
                                onSetupComplete = {
                                    navController.navigate("dashboard") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ✅ AUTH
                        composable("auth") {
                            AuthScreen(
                                onLoginSuccess = {
                                    isLoggedIn = true
                                    navController.navigate("dashboard") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ✅ DASHBOARD (with auth logic)
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
                                        // Cloud ON + not logged in → go to auth
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