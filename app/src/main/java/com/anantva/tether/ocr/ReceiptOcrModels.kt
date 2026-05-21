package com.anantva.tether.ocr

enum class ReceiptApp(val displayName: String) {
    GPAY("Google Pay"),
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    CRED("CRED"),
    UNKNOWN("Unknown")
}

data class OcrLine(val text: String)

data class OcrBlock(
    val text: String,
    val top: Int,
    val bottom: Int
) {
    val centerY: Double get() = (top + bottom) / 2.0

    fun verticalFraction(imageHeight: Int): Double =
        if (imageHeight <= 0) 0.5 else centerY / imageHeight.toDouble()
}

data class ParsedReceipt(
    val amount: Double?,
    val receiver: String?,
    val timestamp: Long?,
    val transactionId: String?,
    val upiId: String?,
    val appSource: String?,
    val status: String?,
    val confidence: Float,
    val rawText: String,
    val detectedApp: ReceiptApp = ReceiptApp.UNKNOWN
)
