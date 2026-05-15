package com.anantva.tether.behavior

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BehaviorClassifier @Inject constructor() {

    private var previousPersonality: String = "Forming"
    private var smoothingCounter: Int = 0

    fun classify(snapshot: BehaviorSnapshot): String {
        val raw = rawClassify(snapshot)
        previousPersonality = smoothTransition(previousPersonality, raw)
        return previousPersonality
    }

    private fun rawClassify(snapshot: BehaviorSnapshot): String = when {
        snapshot.disciplineScore >= 0.8f && snapshot.impulseScore <= 0.25f -> "Disciplined"
        snapshot.disciplineScore >= 0.65f && snapshot.stabilityScore >= 0.6f -> "Stable"
        snapshot.disciplineScore >= 0.5f && snapshot.impulseScore <= 0.4f -> "Controlled"
        snapshot.impulseScore >= 0.7f || snapshot.wantsRatio >= 0.75f -> "Impulsive"
        snapshot.stabilityScore <= 0.25f && snapshot.impulseScore >= 0.5f -> "Spiraling"
        snapshot.streakResilience <= 0.25f -> "Reactive"
        snapshot.impulseScore >= 0.45f -> "Coasting"
        snapshot.disciplineScore >= 0.4f -> "Balanced"
        else -> "Aware"
    }

    private fun smoothTransition(current: String, target: String): String {
        if (current == target || current == "Forming") return target
        val instability = current in setOf("Spiraling", "Impulsive", "Reactive")
        val targetIsStable = target in setOf("Disciplined", "Stable", "Controlled", "Balanced")
        if (!instability || !targetIsStable) return target
        smoothingCounter++
        if (smoothingCounter < 3) return current
        smoothingCounter = 0
        return target
    }

    fun reset() {
        previousPersonality = "Forming"
        smoothingCounter = 0
    }
}
