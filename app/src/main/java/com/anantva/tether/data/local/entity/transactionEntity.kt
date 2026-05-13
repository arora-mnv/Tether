package com.anantva.tether.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TxnCategory {
    NORMAL,
    RECURRING,
    INVESTMENT,
    INCOME;

    fun toDbValue(): String = name

    companion object {
        fun fromDbValue(value: String): TxnCategory {
            return entries.find { it.name == value } ?: NORMAL
        }
    }
}

enum class SpendNature {
    NEED,
    WANT,
    UNKNOWN;

    fun toDbValue(): String = name

    companion object {
        fun fromDbValue(value: String): SpendNature {
            return entries.find { it.name == value } ?: UNKNOWN
        }
    }
}

object SpendingCategories {
    const val FOOD = "Food & Dining"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health"
    const val EDUCATION = "Education"
    const val INVESTMENTS = "Investments"
    const val RENT = "Rent"
    const val EMI = "EMI"
    const val SUBSCRIPTION = "Subscription"
    const val INCOME = "Income"
    const val TRANSFER = "Transfer"
    const val OTHER = "Other"

    fun normalizeMerchant(merchant: String): String =
        merchant
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun categorize(merchant: String, type: String): String {
        val m = normalizeMerchant(merchant)
        return when {
            type == "Credit" -> INCOME
            m.containsAny("rent", "house rent", "housing") -> RENT
            m.containsAny("emi", "loan", "hdfc", "icici loan", "sbi loan") -> EMI
            m.containsAny("netflix", "spotify", "prime", "youtube premium", "disney", "hotstar", "jio", "airtel plan", "monthly plan", "subscription") -> SUBSCRIPTION
            m.containsAny("mutual fund", "zerodha", "groww", "upstox", "sip", "stock", "invest") -> INVESTMENTS
            m.containsAny("swiggy", "zomato", "uber eats", "domino", "dominos", "mcdonald", "burger", "pizza", "food", "restaurant", "cafe", "starbucks", "reliance fresh", "bigbasket", "bbdaily", "blinkit", "zepto", "instamart", "dmart", "grocery") -> FOOD
            m.containsAny("uber", "ola", "metro", "rapido", "bus", "train", "petrol", "fuel", "parking") -> TRANSPORT
            m.containsAny("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "shopping", "mall", "retail") -> SHOPPING
            m.containsAny("electricity", "water", "gas", "broadband", "wifi", "postpaid", "bill", "insurance", "lic", "policy", "premium") -> BILLS
            m.containsAny("pharmacy", "hospital", "doctor", "medicine", "lab", "diagnostic") -> HEALTH
            m.containsAny("coursera", "udemy", "school", "college", "tuition", "book") -> EDUCATION
            m.containsAny("paytm", "phonepe", "gpay", "transfer", "upi", "bank transfer") -> TRANSFER
            else -> OTHER
        }
    }

    fun spendNatureFor(
        category: String,
        merchant: String,
        txnCategory: String = TxnCategory.NORMAL.toDbValue()
    ): SpendNature {
        val normalizedMerchant = normalizeMerchant(merchant)
        val typedCategory = TxnCategory.fromDbValue(txnCategory)
        return when {
            category in setOf(RENT, EMI, BILLS, HEALTH, EDUCATION, INVESTMENTS) -> SpendNature.NEED
            category == TRANSPORT -> SpendNature.NEED
            category == FOOD && normalizedMerchant.containsAny(
                "reliance fresh", "bigbasket", "bbdaily", "blinkit", "zepto", "instamart", "dmart", "grocery"
            ) -> SpendNature.NEED
            category == FOOD -> SpendNature.WANT
            category == SUBSCRIPTION && (typedCategory == TxnCategory.RECURRING || normalizedMerchant.containsAny(
                "jio", "airtel", "broadband", "wifi", "postpaid", "cloud storage"
            )) -> SpendNature.NEED
            category == SUBSCRIPTION -> SpendNature.WANT
            category in setOf(SHOPPING, ENTERTAINMENT) -> SpendNature.WANT
            else -> SpendNature.UNKNOWN
        }
    }

    fun streakPenaltyWeight(
        category: String,
        merchant: String,
        txnCategory: String = TxnCategory.NORMAL.toDbValue()
    ): Double {
        val normalizedMerchant = normalizeMerchant(merchant)
        val typedCategory = TxnCategory.fromDbValue(txnCategory)
        return when {
            typedCategory == TxnCategory.INCOME || typedCategory == TxnCategory.INVESTMENT -> 0.0
            category in setOf(RENT, EMI, BILLS) -> 0.0
            normalizedMerchant.containsAny("insurance", "lic", "policy", "premium") -> 0.0
            category == SUBSCRIPTION && (typedCategory == TxnCategory.RECURRING || normalizedMerchant.containsAny(
                "jio", "airtel", "broadband", "wifi", "postpaid"
            )) -> 0.15
            typedCategory == TxnCategory.RECURRING -> 0.25
            category in setOf(HEALTH, EDUCATION) -> 0.25
            category == TRANSPORT -> 0.45
            category == FOOD && spendNatureFor(category, merchant, txnCategory) == SpendNature.NEED -> 0.35
            category == FOOD -> 1.0
            category == SUBSCRIPTION -> 0.45
            category in setOf(SHOPPING, ENTERTAINMENT) -> 1.0
            else -> 0.8
        }
    }

    fun isDiscretionarySpend(
        category: String,
        merchant: String,
        txnCategory: String = TxnCategory.NORMAL.toDbValue()
    ): Boolean = streakPenaltyWeight(category, merchant, txnCategory) >= 0.6

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val transactionId: Long,
    val amount:        Double,
    val merchant:      String,
    val type:          String,
    val source:        String,
    val date:          Long,
    val status:        String = "CONFIRMED",
    val category:      String = SpendingCategories.OTHER,
    val txnCategory:   String = TxnCategory.NORMAL.toDbValue(),
    val spendNature:   String = SpendNature.UNKNOWN.toDbValue()
) {
    val typedCategory: TxnCategory
        get() = TxnCategory.fromDbValue(txnCategory)

    val typedSpendNature: SpendNature
        get() = SpendNature.fromDbValue(spendNature)

    val isStreakRelevant: Boolean
        get() = typedCategory == TxnCategory.NORMAL && type == "Expense"

    fun toMap(): Map<String, Any> = mapOf(
        "transactionId" to transactionId,
        "amount" to amount,
        "merchant" to merchant,
        "type" to type,
        "source" to source,
        "date" to date,
        "status" to status,
        "category" to category,
        "txnCategory" to txnCategory,
        "spendNature" to spendNature
    )

    companion object {
        fun fromMap(data: Map<String, Any?>): TransactionEntity {
            return TransactionEntity(
                transactionId = (data["transactionId"] as? Number)?.toLong() ?: 0L,
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                merchant = data["merchant"] as? String ?: "",
                type = data["type"] as? String ?: "Expense",
                source = data["source"] as? String ?: "unknown",
                date = (data["date"] as? Number)?.toLong() ?: 0L,
                status = data["status"] as? String ?: "CONFIRMED",
                category = data["category"] as? String ?: SpendingCategories.OTHER,
                txnCategory = data["txnCategory"] as? String ?: TxnCategory.NORMAL.toDbValue(),
                spendNature = data["spendNature"] as? String ?: SpendNature.UNKNOWN.toDbValue()
            )
        }
    }
}
