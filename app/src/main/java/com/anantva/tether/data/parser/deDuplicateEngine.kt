package com.anantva.tether.data.parser

import com.anantva.tether.data.model.ParsedTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeduplicationEngine @Inject constructor() {

    private val recentFingerprints = mutableMapOf<String, Long>()

    // ✅ 60 seconds — tight enough to catch duplicate notifications,
    // loose enough to allow two different payments in quick succession
    private val windowMillis = 60 * 1000L

    fun isDuplicate(transaction: ParsedTransaction): Boolean {
        pruneExpired()
        val fingerprint = buildFingerprint(transaction)
        val existing    = recentFingerprints[fingerprint]

        return if (existing != null &&
            (transaction.detectedAt - existing) < windowMillis) {
            true  // exact same amount + merchant within 60 seconds = duplicate
        } else {
            recentFingerprints[fingerprint] = transaction.detectedAt
            false
        }
    }

    private fun buildFingerprint(t: ParsedTransaction): String {
        // ✅ No time bucket — just amount + merchant
        // Two transactions only collide if both match exactly
        val amountRounded      = "%.2f".format(t.amount)
        val merchantNormalized = t.merchant
            .lowercase()
            .replace(Regex("""[^a-z0-9]"""), "")
        return "$amountRounded|$merchantNormalized"
    }

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - windowMillis
        recentFingerprints.entries.removeIf { it.value < cutoff }
    }
}