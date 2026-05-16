package com.anantva.tether.ocr

data class OcrLine(val text: String)
data class OcrBlock(val text: String, val top: Int, val bottom: Int)

enum class ReceiptApp {
    GPAY,
    PHONEPE,
    PAYTM,
    CRED,
    UNKNOWN
}

interface ReceiptParser {
    val app: ReceiptApp
    fun canParse(blocks: List<OcrBlock>): Boolean
    fun parse(blocks: List<OcrBlock>, lines: List<OcrLine>, originalText: String, imageHeight: Int): ParsedReceipt
}

// Helpers
private fun extractCleanAmount(text: String): Double? {
    var textMod = text
    val hasTimestamp = Regex("""\d{1,2}:\d{2}\s*(?:am|pm|AM|PM)""").containsMatchIn(textMod) || Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").containsMatchIn(textMod) || Regex("""\d{1,2}\s+[a-zA-Z]{3,}\s+\d{4}""").containsMatchIn(textMod)
    if (!hasTimestamp) {
        textMod = textMod.replace(Regex("""\bT(?=\s*\d)"""), "₹")
    }
    val normalized = textMod.replace(",", "").replace(" ", "").replace("\n", "")
    val amountRegex = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)""", RegexOption.IGNORE_CASE)
    val match = amountRegex.find(normalized) ?: return null
    val raw = match.groupValues[1]
    if (raw.length >= 10) return null
    val parsed = raw.toDoubleOrNull()
    return parsed
}

private fun extractValidAmounts(blocks: List<OcrBlock>, imageHeight: Int, startFraction: Double = 0.0, endFraction: Double = 1.0): List<Double> {
    val candidates = mutableListOf<Double>()
    val amountRegex = Regex("""(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)""", RegexOption.IGNORE_CASE)

    for (block in blocks) {
        val yCenter = (block.top + block.bottom) / 2.0
        val fraction = if (imageHeight > 0) yCenter / imageHeight.toDouble() else 0.5
        if (fraction in startFraction..endFraction) {
            var text = block.text

            val hasTimestamp = Regex("""\d{1,2}:\d{2}\s*(?:am|pm|AM|PM)""").containsMatchIn(text) || Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").containsMatchIn(text) || Regex("""\d{1,2}\s+[a-zA-Z]{3,}\s+\d{4}""").containsMatchIn(text)
            if (!hasTimestamp) {
                text = text.replace(Regex("""\bT(?=\s*\d)"""), "₹")
            }

            val normalizedForAmount = text.replace(",", "").replace(" ", "").replace("\n", "")
            
            for (match in amountRegex.findAll(normalizedForAmount)) {
                val raw = match.groupValues[1]
                if (raw.length < 10) { 
                    val parsed = raw.toDoubleOrNull()
                    if (parsed != null) {
                        candidates.add(parsed)
                    }
                }
            }
        }
    }
    return candidates
}

private fun extractMerchant(lines: List<OcrLine>, keywordRegex: Regex): String? {
    for (i in lines.indices) {
        if (keywordRegex.containsMatchIn(lines[i].text)) {
            val match = keywordRegex.find(lines[i].text)
            if (match != null) {
                val afterKeyword = lines[i].text.substring(match.range.last + 1).trim()
                if (afterKeyword.length > 2) {
                    return cleanMerchant(afterKeyword)
                }
            }
            if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].text.trim()
                if (!nextLine.contains("₹") && nextLine.length > 2) {
                    return cleanMerchant(nextLine)
                }
            }
        }
    }
    return null
}

private fun cleanMerchant(text: String): String {
    var extracted = text
    val phoneIdx = extracted.indexOfFirst { it.isDigit() || it == '+' }
    if (phoneIdx != -1) extracted = extracted.substring(0, phoneIdx)
    val upiIdx = extracted.indexOf('@')
    if (upiIdx != -1) extracted = extracted.substring(0, upiIdx)
    return extracted.trim()
}

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

        val merchant = extractMerchant(lines, Regex("""\bpaid\b""", RegexOption.IGNORE_CASE))

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
