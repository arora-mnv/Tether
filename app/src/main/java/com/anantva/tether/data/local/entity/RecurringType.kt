package com.anantva.tether.data.local.entity

enum class RecurringType(val displayName: String) {
    SUBSCRIPTION("Subscription"),
    EMI("EMI"),
    RENT("Rent"),
    BILL("Bill"),
    SIP("SIP"),
    INSURANCE("Insurance"),
    SALARY("Salary"),
    TRANSFER("Transfer"),
    OTHER("Other");

    companion object {
        fun infer(category: String, merchant: String): RecurringType {
            val m = SpendingCategories.normalizeMerchant(merchant)
            val c = category.lowercase()
            val insuranceWords = arrayOf("insurance", "life insurance", "policy", "premium", "health insurance", "term plan", "car insurance", "bike insurance")
            return when {
                m.containsAny(*insuranceWords) || m.containsWord("lic") -> INSURANCE
                c == "emi" || m.containsWord("hdfc") || m.containsWord("icici") || m.containsWord("axis") || m.containsWord("sbi") || m.containsAny("bajaj finance", "tata capital", "equated monthly") -> EMI
                c == "rent" || m.containsAny("rent", "house rent", "housing", "landlord", "tenant") -> RENT
                c == "subscription" || m.containsAny("netflix", "spotify", "prime video", "youtube premium", "disney", "hotstar", "apple tv", "sony liv", "zee5", "jio hotstar", "amazon prime", "kindle unlimited", "icloud storage", "google one", "microsoft 365", "canva pro", "notion") -> SUBSCRIPTION
                c == "bills & utilities" || m.containsAny("electricity", "water bill", "gas bill", "broadband", "wifi", "internet", "postpaid", "phone bill", "mobile recharge", "vodafone", "airtel", "jio recharge", "bsnl", "property tax", "maintenance") -> BILL
                c == "investments" || m.containsAny("mutual fund", "sip", "zerodha", "groww", "upstox", "icici direct", "hdfc securities", "nps", "ppf", "epf") -> SIP
                m.containsAny("salary", "payroll", "wages") -> SALARY
                m.containsAny("rent received", "dividend", "interest") -> TRANSFER
                else -> OTHER
            }
        }

        fun fromCategory(category: String): RecurringType = when {
            category == SpendingCategories.EMI -> EMI
            category == SpendingCategories.RENT -> RENT
            category == SpendingCategories.SUBSCRIPTION -> SUBSCRIPTION
            category == SpendingCategories.BILLS -> BILL
            category == SpendingCategories.INVESTMENTS -> SIP
            else -> OTHER
        }

        private fun String.containsAny(vararg keywords: String): Boolean =
            keywords.any { this.contains(it, ignoreCase = true) }

        private fun String.containsWord(word: String): Boolean =
            this.split(" ").any { it == word }
    }
}
