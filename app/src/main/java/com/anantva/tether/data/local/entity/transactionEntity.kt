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

    fun categorize(merchant: String, type: String): String {
        val m = merchant.lowercase()
        return when {
            type == "Credit" -> INCOME
            m.containsAny("rent", "house rent", "housing") -> RENT
            m.containsAny("emi", "loan", "hdfc", "icici loan", "sbi loan") -> EMI
            m.containsAny("netflix", "spotify", "prime", "youtube premium", "disney", "hotstar", "jio", "airtel plan", "monthly plan", "subscription") -> SUBSCRIPTION
            m.containsAny("mutual fund", "zerodha", "groww", "upstox", "sip", "stock", "invest") -> INVESTMENTS
            m.containsAny("swiggy", "zomato", "uber eats", "domino", "mcdonald", "burger", "pizza", "food", "restaurant", "cafe", "starbucks") -> FOOD
            m.containsAny("uber", "ola", "metro", "rapido", "bus", "train", "petrol", "fuel", "parking") -> TRANSPORT
            m.containsAny("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "shopping", "mall", "retail") -> SHOPPING
            m.containsAny("electricity", "water", "gas", "broadband", "wifi", "postpaid", "bill") -> BILLS
            m.containsAny("pharmacy", "hospital", "doctor", "medicine", "lab", "diagnostic") -> HEALTH
            m.containsAny("coursera", "udemy", "school", "college", "tuition", "book") -> EDUCATION
            m.containsAny("paytm", "phonepe", "gpay", "transfer", "upi", "bank transfer") -> TRANSFER
            else -> OTHER
        }
    }

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