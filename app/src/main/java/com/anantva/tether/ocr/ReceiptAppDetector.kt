package com.anantva.tether.ocr

object ReceiptAppDetector {

    fun detect(blocks: List<OcrBlock>, lines: List<OcrLine>): ReceiptApp {
        val blob = (blocks.map { it.text } + lines.map { it.text }).joinToString("\n").lowercase()

        return when {
            blob.contains("google pay") || blob.contains("pay again") -> ReceiptApp.GPAY
            blob.contains("paid to") || blob.contains("debited from") -> ReceiptApp.PHONEPE
            blob.contains("payment successful") || blob.contains("upi ref") -> ReceiptApp.PAYTM
            blob.contains("paid via cred") || blob.contains("paid securely by cred") -> ReceiptApp.CRED
            else -> ReceiptApp.UNKNOWN
        }
    }
}
