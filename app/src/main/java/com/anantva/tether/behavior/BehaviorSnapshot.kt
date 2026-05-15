package com.anantva.tether.behavior

data class BehaviorSnapshot(
    val impulseScore: Float = 0f,
    val disciplineScore: Float = 0f,
    val stabilityScore: Float = 0f,
    val streakResilience: Float = 0f,
    val currentPersonality: String = "Forming",
    val streakRisk: Float = 0f,
    val wantsRatio: Float = 0.5f,
    val behavioralTrend: String = "STABLE",
    val personalityProfile: PersonalityProfile = PersonalityProfile()
)
