package com.anantva.tether.behavior

import com.anantva.tether.data.local.entity.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BehaviorLearningEngine @Inject constructor(
    private val patternAnalyzer: PatternAnalyzer,
    private val classifier: BehaviorClassifier,
    private val riskPredictor: RiskPredictor,
    private val personalityEngine: FinancialPersonalityEngine
) {
    private var lastSnapshot: BehaviorSnapshot = BehaviorSnapshot()

    suspend fun computeSnapshot(
        transactions: List<TransactionEntity>,
        streakDays: Int,
        isOverLimit: Boolean,
        spentToday: Int
    ): BehaviorSnapshot {
        val snapshot = patternAnalyzer.analyze(transactions, streakDays, isOverLimit, spentToday)
        val personality = classifier.classify(snapshot)
        val profile = personalityEngine.compute(transactions, streakDays, isOverLimit)
        lastSnapshot = snapshot.copy(currentPersonality = personality, personalityProfile = profile)
        return lastSnapshot
    }

    fun getLastSnapshot(): BehaviorSnapshot = lastSnapshot

    fun riskLevel(): String = riskPredictor.interpretRisk(lastSnapshot.streakRisk)

    fun expectedBreakIn(): String = riskPredictor.expectedBreakIn(lastSnapshot.streakRisk)

    fun reset() {
        classifier.reset()
        lastSnapshot = BehaviorSnapshot()
    }
}
