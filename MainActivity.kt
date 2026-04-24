package com.anantva.tether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anantva.tether.auth.FirebaseAuthManager
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui_elements.screens.DashboardScreen
import com.anantva.tether.ui.theme.TetherTheme
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

    private var isLoggedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        val isCloudSyncEnabled = runBlocking {
            preferencesRepository.isCloudStorage.first()
        }

        if (!isCloudSyncEnabled) {
            // If cloud sync is disabled, show DashboardScreen directly
            isLoggedIn = true
        } else {
            // Check if user is logged in
            isLoggedIn = authManager.isLoggedIn()
        }

        setContent {
            TetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    if (isLoggedIn) {
                        DashboardScreen()
                    } else {
                        AuthScreen { onLoginSuccess() }
                    }
                }
            }
        }
    }

    private fun onLoginSuccess() {
        isLoggedIn = true
    }
}
