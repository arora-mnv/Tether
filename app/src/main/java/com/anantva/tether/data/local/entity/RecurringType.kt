package com.anantva.tether.data.local.entity

enum class RecurringType {
    SUBSCRIPTION,
    EMI,
    RENT,
    BILL,
    SIP,
    INSURANCE,
    OTHER;

    companion object {
        fun infer(category: String, merchant: String): RecurringType {
            val m = SpendingCategories.normalizeMerchant(merchant)
            return when {
                category == SpendingCategories.EMI || m.contains("emi") || m.contains("loan") -> EMI
                category == SpendingCategories.RENT || m.contains("rent") -> RENT
                category == SpendingCategories.SUBSCRIPTION || m.containsAny("netflix", "spotify", "prime", "youtube premium", "disney", "hotstar", "subscription") -> SUBSCRIPTION
                category == SpendingCategories.BILLS || m.containsAny("electricity", "water", "gas", "broadband", "wifi", "postpaid", "bill") -> BILL
                category == SpendingCategories.INVESTMENTS || m.containsAny("mutual fund", "sip") -> SIP
                m.containsAny("insurance", "lic", "policy", "premium") -> INSURANCE
                else -> OTHER
            }
        }

        private fun String.containsAny(vararg keywords: String): Boolean =
            keywords.any { this.contains(it, ignoreCase = true) }
    }
}
