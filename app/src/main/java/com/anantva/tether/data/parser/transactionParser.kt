package com.anantva.tether.data.parser

import com.anantva.tether.data.model.ParsedTransaction
import com.anantva.tether.data.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionParser @Inject constructor() {

    // ─────────────────────────────────────────────
    // Amount patterns — covers all Indian bank formats
    // ─────────────────────────────────────────────
    private val amountPatterns = listOf(
        // Rs.231.25 / Rs 1,000.00 / RS.500
        Regex("""[Rr][Ss]\.?\s*([\d,]+(?:\.\d{1,2})?)"""),
        // INR 500 / INR500
        Regex("""INR\s*([\d,]+(?:\.\d{1,2})?)"""),
        // ₹500 / ₹ 1,000
        Regex("""₹\s*([\d,]+(?:\.\d{1,2})?)"""),
        // debited/credited with 500.00
        Regex("""(?:debited|credited)\s+(?:with\s+)?(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // payment of Rs 500
        Regex("""(?:payment|amount)\s+of\s+(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    // ─────────────────────────────────────────────
    // Debit keywords
    // ─────────────────────────────────────────────
    private val debitKeywords = listOf(
        "sent", "debited", "debit", "paid", "payment",
        "withdrawn", "purchase", "spent", "transferred to",
        "charged", "deducted"
    )

    // ─────────────────────────────────────────────
    // Credit keywords
    // ─────────────────────────────────────────────
    private val creditKeywords = listOf(
        "received", "credited", "credit", "refund",
        "cashback", "added", "deposited", "transferred from",
        "reward", "money added"
    )

    // ─────────────────────────────────────────────
    // Merchant patterns — ordered by specificity
    // ─────────────────────────────────────────────
    private val merchantPatterns = listOf(
        // "To MERCHANT NAME" — HDFC / SBI / Axis style
        Regex(
            """\bto\s+([A-Za-z][A-Za-z0-9\s&'\-\.]{2,40}?)(?=\n|$|\bOn\b|\bRef\b|\bUPI\b|\bvia\b)""",
            RegexOption.IGNORE_CASE
        ),
        // "at MERCHANT" — POS / card transactions
        Regex("""at\s+([A-Za-z0-9][A-Za-z0-9\s&'\-\.]{2,40})(?:\s+on|\s+for|\n|$)""", RegexOption.IGNORE_CASE),
        // "merchant: NAME"
        Regex("""[Mm]erchant[:\s]+([A-Za-z0-9][A-Za-z0-9\s&'\-\.]{2,40})(?:\n|$)"""),
        // "VPA abc@upi" — UPI handle as fallback merchant
        Regex("""(?:[Tt]o\s+)?([a-zA-Z0-9._\-]+@[a-zA-Z0-9]+)"""),
        // "From NAME" — for credits
        Regex(
            """\bfrom\s+(?:A/C\s*\*?\d+\s+)?([A-Za-z][A-Za-z0-9\s&'\-\.]{2,30}?)(?=\n|$|\bRef\b|\bUPI\b)""",
            RegexOption.IGNORE_CASE
        )
    )

    // ─────────────────────────────────────────────
    // Known banking app packages
    // ─────────────────────────────────────────────
    val knownBankingPackages = setOf(
        // UPI apps
        "com.google.android.apps.nbu.paisa.user",  // Google Pay
        "net.one97.paytm",                          // Paytm
        "com.phonepe.app",                          // PhonePe
        "in.amazon.mShop.android.shopping",         // Amazon Pay
        "com.whatsapp",                             // WhatsApp Pay
        "com.mobikwik_new",                         // MobiKwik
        "com.freecharge.android",                   // Freecharge
        "com.bharatpay.app",                        // BharatPe
        // Private banks
        "com.hdfc.bank",                            // HDFC Mobile Banking
        "com.snapwork.hdfc",                        // HDFC PayZapp
        "com.csam.icici.bank.imobile",              // ICICI iMobile
        "com.axis.mobile",                          // Axis Bank
        "com.msf.kbank.mobile",                     // Kotak Mahindra
        "com.idbi.mpassbook",                       // IDBI
        "com.indusind.mobile",                      // IndusInd
        "com.yesbank",                              // Yes Bank
        "com.rbl.rblmobilebanking",                 // RBL Bank
        // Public sector banks
        "com.sbi.SBIFreedomPlus",                   // SBI
        "com.pnb.mbanking",                         // PNB
        "com.boi.versions",                         // Bank of India
        "com.bob.rewardz",                          // Bank of Baroda
        "com.canarabank.mobility",                  // Canara Bank
        "com.infrasofttech.centralbank",            // Central Bank
        "com.lcode.smefinance",                     // Union Bank
        // Wallets / Fintech
        "com.slice.app",                            // Slice
        "com.jupiter.money",                        // Jupiter
        "com.fifipay",                              // Fi Money
        "in.finin.money",                           // Niyo
        "com.cred.club",                            // CRED
        // SMS apps that surface bank transaction texts as notifications
        "com.google.android.apps.messaging",        // Google Messages
        "com.samsung.android.messaging",            // Samsung Messages
        "com.microsoft.android.smsorganizer",       // SMS Organizer
        "com.android.mms"                           // AOSP Messages
    )

    // ─────────────────────────────────────────────
    // Main parse function
    // ─────────────────────────────────────────────
    fun parse(text: String, sourcePackage: String): ParsedTransaction? {
        val cleaned = text.trim()

        val amount   = extractAmount(cleaned)   ?: return null
        val type     = extractType(cleaned)     ?: return null
        val merchant = extractMerchant(cleaned, type)

        return ParsedTransaction(
            amount    = amount,
            merchant  = merchant,
            type      = type,
            rawText   = cleaned,
            sourceApp = sourcePackage
        )
    }

    // ─────────────────────────────────────────────
    // Extraction helpers
    // ─────────────────────────────────────────────

    private fun extractAmount(text: String): Double? {
        for (pattern in amountPatterns) {
            val match = pattern.find(text) ?: continue
            val raw   = match.groupValues[1].replace(",", "")
            return raw.toDoubleOrNull()
        }
        return null
    }

    private fun extractType(text: String): TransactionType? {
        val lower = text.lowercase()
        val isDebit  = debitKeywords.any  { lower.contains(it) }
        val isCredit = creditKeywords.any { lower.contains(it) }
        return when {
            isDebit && !isCredit -> TransactionType.DEBIT
            isCredit && !isDebit -> TransactionType.CREDIT
            isDebit && isCredit  -> {
                // Both found — use first keyword position as tiebreaker
                val debitPos  = debitKeywords.mapNotNull  { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull() ?: Int.MAX_VALUE
                val creditPos = creditKeywords.mapNotNull { lower.indexOf(it).takeIf { i -> i >= 0 } }.minOrNull() ?: Int.MAX_VALUE
                if (debitPos < creditPos) TransactionType.DEBIT else TransactionType.CREDIT
            }
            else -> null  // Can't determine type — skip
        }
    }

    private fun extractMerchant(text: String, type: TransactionType): String {
        // For credits, try "From" pattern first
        val patterns = if (type == TransactionType.CREDIT)
            merchantPatterns.reversed()
        else
            merchantPatterns

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val raw   = match.groupValues[1].trim()
            if (raw.length >= 2) return cleanMerchant(raw)
        }
        return "Unknown"
    }

    private fun cleanMerchant(raw: String): String =
        raw.trim()
            .replace(Regex("""\s+"""), " ")          // collapse spaces
            .replace(Regex("""[^\w\s&'\-\.]"""), "") // strip odd chars
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
            .take(40)
}
