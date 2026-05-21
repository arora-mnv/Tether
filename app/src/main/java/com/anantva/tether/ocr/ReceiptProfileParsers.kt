package com.anantva.tether.ocr

interface ReceiptParser {
    val app: ReceiptApp
    fun canParse(blocks: List<OcrBlock>): Boolean
    fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt
}

// Helpers
private fun extractCleanAmount(text: String): Double? =
    ReceiptAmountExtractor.extractFromLine(text).maxOrNull()

private fun extractValidAmounts(blocks: List<OcrBlock>, imageHeight: Int, startFraction: Double = 0.0, endFraction: Double = 1.0): List<Double> {
    return ReceiptAmountExtractor.extractFromBlocks(
        blocks = blocks,
        imageHeight = imageHeight,
        minFraction = startFraction,
        maxFraction = endFraction
    ).map { it.amount }
}

private fun extractMerchant(lines: List<OcrLine>, keywordRegex: Regex): String? =
    ReceiptMerchantExtractor.afterKeyword(lines, keywordRegex)

class GPayParser : ReceiptParser {
    override val app = ReceiptApp.GPAY

    override fun canParse(blocks: List<OcrBlock>): Boolean {
        return blocks.any {
            val lower = it.text.lowercase()
            lower.contains("google pay") || lower.contains("pay again")
        }
    }

    override fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt {
        val amounts = extractValidAmounts(blocks, imageHeight, 0.0, 0.5)
        val amount = amounts.maxOrNull()

        val merchant = extractMerchant(lines, Regex("""\bTo\b""", RegexOption.IGNORE_CASE))

        return ParsedReceipt(
            amount = amount,
            receiver = merchant,
            timestamp = System.currentTimeMillis(),
            transactionId = null,
            upiId = null,
            appSource = "Google Pay",
            status = "Completed",
            confidence = if (amount != null && merchant != null) 0.9f else 0.5f,
            rawText = originalText
        )
    }
}

class PhonePeParser : ReceiptParser {
    override val app = ReceiptApp.PHONEPE

    override fun canParse(blocks: List<OcrBlock>): Boolean {
        return blocks.any {
            val lower = it.text.lowercase()
            lower.contains("paid to") || lower.contains("debited from")
        }
    }

    override fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt {
        var amount: Double? = null
        for (block in blocks) {
            if (block.text.lowercase().contains("paid to")) {
                amount = extractCleanAmount(block.text)
                if (amount != null) break
            }
        }
        if (amount == null) {
            amount = extractValidAmounts(blocks, imageHeight, 0.0, 0.4).maxOrNull()
        }

        val merchant = extractMerchant(lines, Regex("""Paid to""", RegexOption.IGNORE_CASE))

        return ParsedReceipt(
            amount = amount,
            receiver = merchant,
            timestamp = System.currentTimeMillis(),
            transactionId = null,
            upiId = null,
            appSource = "PhonePe",
            status = "Completed",
            confidence = if (amount != null && merchant != null) 0.9f else 0.5f,
            rawText = originalText
        )
    }
}

class PaytmParser : ReceiptParser {
    override val app = ReceiptApp.PAYTM

    override fun canParse(blocks: List<OcrBlock>): Boolean {
        return blocks.any {
            val lower = it.text.lowercase()
            lower.contains("payment successful") || lower.contains("upi ref")
        }
    }

    override fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt {
        val amounts = extractValidAmounts(blocks, imageHeight, 0.0, 0.5)
        val amount = amounts.maxOrNull()

        val merchant = extractMerchant(lines, Regex("""To:?""", RegexOption.IGNORE_CASE))

        return ParsedReceipt(
            amount = amount,
            receiver = merchant,
            timestamp = System.currentTimeMillis(),
            transactionId = null,
            upiId = null,
            appSource = "Paytm",
            status = "Completed",
            confidence = if (amount != null && merchant != null) 0.9f else 0.5f,
            rawText = originalText
        )
    }
}

class CredParser : ReceiptParser {
    override val app = ReceiptApp.CRED

    override fun canParse(blocks: List<OcrBlock>): Boolean {
        return blocks.any {
            val lower = it.text.lowercase()
            lower.contains("paid via cred") || lower.contains("paid securely by cred")
        }
    }

    override fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt {
        val amounts = extractValidAmounts(blocks, imageHeight, 0.0, 0.6)
        val amount = amounts.maxOrNull()

        val merchant = ReceiptMerchantExtractor.lineAboveCredFooter(lines)

        return ParsedReceipt(
            amount = amount,
            receiver = merchant,
            timestamp = System.currentTimeMillis(),
            transactionId = null,
            upiId = null,
            appSource = "CRED",
            status = "Completed",
            confidence = if (amount != null && merchant != null) 0.9f else 0.5f,
            rawText = originalText
        )
    }
}
