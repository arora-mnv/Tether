package com.anantva.tether.behavior

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskPredictor @Inject constructor() {

    fun interpretRisk(risk: Float): String = when {
        risk >= 0.7f -> "HIGH"
        risk >= 0.4f -> "MODERATE"
        risk >= 0.2f -> "LOW"
        else -> "MINIMAL"
    }

    fun expectedBreakIn(risk: Float): String = when {
        risk >= 0.8f -> "Today"
        risk >= 0.6f -> "This week"
        risk >= 0.4f -> "This weekend"
        risk >= 0.2f -> "Unlikely soon"
        else -> "Streak is secure"
    }
}
