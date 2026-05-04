package com.anantva.tether.state

data class AppStartState(
    val isLoading: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val setupCompleted: Boolean = false
)
