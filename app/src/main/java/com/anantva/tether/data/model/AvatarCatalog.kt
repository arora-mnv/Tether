package com.anantva.tether.data.model

data class AvatarMotion(
    val morphSpeed: Float,
    val wobbleFreq: Float,
    val wobbleAmp: Float,
    val driftSpeed: Float
)

data class AvatarOption(
    val id: String,
    val label: String,
    val colors: List<Long>,
    val motion: AvatarMotion,
    val glowIntensity: Float
)

object AvatarCatalog {
    val defaultAvatars = listOf(
        AvatarOption("pulse",  "Pulse",  listOf(0xFF00E5FF, 0xFF1DE9B6, 0xFF84FFFF),
            AvatarMotion(morphSpeed = 1.0f, wobbleFreq = 3f, wobbleAmp = 0.12f, driftSpeed = 0.8f), glowIntensity = 1f),
        AvatarOption("nova",   "Nova",   listOf(0xFFE040FB, 0xFF7C4DFF, 0xFFEA80FC),
            AvatarMotion(morphSpeed = 1.3f, wobbleFreq = 4f, wobbleAmp = 0.14f, driftSpeed = 1.0f), glowIntensity = 1.1f),
        AvatarOption("aura",   "Aura",   listOf(0xFFFF4081, 0xFFE040FB, 0xFFFF80AB),
            AvatarMotion(morphSpeed = 0.8f, wobbleFreq = 2.5f, wobbleAmp = 0.10f, driftSpeed = 0.6f), glowIntensity = 0.9f),
        AvatarOption("flux",   "Flux",   listOf(0xFF448AFF, 0xFF00BCD4, 0xFF82B1FF),
            AvatarMotion(morphSpeed = 1.5f, wobbleFreq = 5f, wobbleAmp = 0.16f, driftSpeed = 1.2f), glowIntensity = 1.2f),
        AvatarOption("orbit",  "Orbit",  listOf(0xFF3D5AFE, 0xFF536DFE, 0xFF8C9EFF),
            AvatarMotion(morphSpeed = 0.6f, wobbleFreq = 2f, wobbleAmp = 0.08f, driftSpeed = 0.5f), glowIntensity = 0.8f),
        AvatarOption("zenith", "Zenith", listOf(0xFF00E676, 0xFF00BFA5, 0xFFA7FFEB),
            AvatarMotion(morphSpeed = 0.7f, wobbleFreq = 2.8f, wobbleAmp = 0.09f, driftSpeed = 0.5f), glowIntensity = 0.85f),
        AvatarOption("bloom",  "Bloom",  listOf(0xFF76FF03, 0xFF00E676, 0xFFCCFF90),
            AvatarMotion(morphSpeed = 0.9f, wobbleFreq = 3.2f, wobbleAmp = 0.11f, driftSpeed = 0.7f), glowIntensity = 0.95f),
        AvatarOption("surge",  "Surge",  listOf(0xFFFF6E40, 0xFFFF3D00, 0xFFFF9E80),
            AvatarMotion(morphSpeed = 1.6f, wobbleFreq = 5.5f, wobbleAmp = 0.18f, driftSpeed = 1.4f), glowIntensity = 1.3f),
        AvatarOption("drift",  "Drift",  listOf(0xFF18FFFF, 0xFF00B8D4, 0xFF84FFFF),
            AvatarMotion(morphSpeed = 0.5f, wobbleFreq = 1.8f, wobbleAmp = 0.07f, driftSpeed = 0.4f), glowIntensity = 0.7f),
        AvatarOption("phase",  "Phase",  listOf(0xFFD500F9, 0xFF651FFF, 0xFFEA80FC),
            AvatarMotion(morphSpeed = 1.1f, wobbleFreq = 3.5f, wobbleAmp = 0.13f, driftSpeed = 0.9f), glowIntensity = 1.05f),
        AvatarOption("prism",  "Prism",  listOf(0xFF2979FF, 0xFF00E5FF, 0xFF82B1FF),
            AvatarMotion(morphSpeed = 1.2f, wobbleFreq = 4.5f, wobbleAmp = 0.15f, driftSpeed = 1.1f), glowIntensity = 1.15f),
        AvatarOption("echo",   "Echo",   listOf(0xFF78909C, 0xFF546E7A, 0xFFB0BEC5),
            AvatarMotion(morphSpeed = 0.4f, wobbleFreq = 1.5f, wobbleAmp = 0.06f, driftSpeed = 0.3f), glowIntensity = 0.6f),
        AvatarOption("ember",  "Ember",  listOf(0xFFFF6B35, 0xFFE74C3C, 0xFFFFAB91),
            AvatarMotion(morphSpeed = 1.4f, wobbleFreq = 4.8f, wobbleAmp = 0.17f, driftSpeed = 1.3f), glowIntensity = 1.25f),
        AvatarOption("halo",   "Halo",   listOf(0xFFFFD740, 0xFFFFAB00, 0xFFFFE082),
            AvatarMotion(morphSpeed = 0.75f, wobbleFreq = 2.2f, wobbleAmp = 0.09f, driftSpeed = 0.55f), glowIntensity = 0.9f),
        AvatarOption("current","Current",listOf(0xFF40C4FF, 0xFF2979FF, 0xFF80D8FF),
            AvatarMotion(morphSpeed = 1.0f, wobbleFreq = 3f, wobbleAmp = 0.12f, driftSpeed = 0.8f), glowIntensity = 1f),
        AvatarOption("lumen",  "Lumen",  listOf(0xFFE0E0E0, 0xFFBDBDBD, 0xFFFFFFFF),
            AvatarMotion(morphSpeed = 0.5f, wobbleFreq = 2f, wobbleAmp = 0.08f, driftSpeed = 0.4f), glowIntensity = 0.7f)
    )

    fun getAvatarById(id: String): AvatarOption =
        defaultAvatars.find { it.id == id } ?: defaultAvatars.first()
}
