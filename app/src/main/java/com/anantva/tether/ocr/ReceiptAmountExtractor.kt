package com.anantva.tether.ocr

/**
 * Amounts are extracted ONLY from currency-marked values — never from bare numbers in the OCR blob.
 */
object ReceiptAmountExtractor {

    private val CURRENCY_AMOUNT = Regex(
        """(?:₹|Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val PHONE_PATTERN = Regex("""\+?91[\s-]?\d{5}[\s-]?\d{5}""")
    private val UTR_PATTERN = Regex("""\b\d{12,}\b""")
    private val TIME_PATTERN = Regex(
        """\d{1,2}:\d{2}\s*(?:am|pm)?|\d{1,2}/\d{1,2}/\d{2,4}|\d{1,2}\s+[A-Za-z]{3,}\s+\d{4}""",
        RegexOption.IGNORE_CASE
    )

    data class AmountCandidate(
        val amount: Double,
        val block: OcrBlock,
        val regionWeight: Double
    )

    fun extractFromLine(line: String): List<Double> =
        extractFromText(ReceiptOcrNormalizer.normalizeLine(line))

    fun extractFromText(text: String): List<Double> {
        val normalized = ReceiptOcrNormalizer.normalizeLine(text)
        if (!hasCurrencyMarker(normalized)) return emptyList()

        return CURRENCY_AMOUNT.findAll(normalized).mapNotNull { match ->
            parseCurrencyValue(match.groupValues[1], normalized)
        }.toList()
    }

    fun extractFromBlocks(
        blocks: List<OcrBlock>,
        imageHeight: Int,
        minFraction: Double = 0.0,
        maxFraction: Double = 1.0,
        highPriorityTopFraction: Double = 0.4
    ): List<AmountCandidate> {
        val candidates = mutableListOf<AmountCandidate>()
        for (block in blocks) {
            val fraction = block.verticalFraction(imageHeight)
            if (fraction !in minFraction..maxFraction) continue

            val regionWeight = when {
                fraction <= highPriorityTopFraction -> 1.0
                fraction >= 0.5 -> 0.25
                else -> 0.6
            }

            for (amount in extractFromText(block.text)) {
                candidates.add(AmountCandidate(amount, block, regionWeight))
            }
        }
        return candidates
    }

    fun largestInRegion(
        blocks: List<OcrBlock>,
        imageHeight: Int,
        minFraction: Double = 0.0,
        maxFraction: Double = 0.4
    ): Double? = pickBest(
        extractFromBlocks(blocks, imageHeight, minFraction, maxFraction)
    )

    fun amountBesideKeyword(
        blocks: List<OcrBlock>,
        imageHeight: Int,
        keyword: String,
        verticalWindowFraction: Double = 0.12
    ): Double? {
        val windowPx = (imageHeight * verticalWindowFraction).toInt().coerceAtLeast(40)
        val keywordBlocks = blocks.filter { it.text.contains(keyword, ignoreCase = true) }
        if (keywordBlocks.isEmpty()) return null

        val nearby = blocks.filter { block ->
            keywordBlocks.any { anchor ->
                kotlin.math.abs(block.centerY - anchor.centerY) <= windowPx
            }
        }

        return pickBest(
            extractFromBlocks(
                blocks = nearby.ifEmpty { keywordBlocks },
                imageHeight = imageHeight,
                minFraction = 0.0,
                maxFraction = 0.45
            )
        )
    }

    fun amountNearAnchorText(
        blocks: List<OcrBlock>,
        imageHeight: Int,
        anchor: String,
        searchBelowFraction: Double = 0.35
    ): Double? {
        val anchorBlock = blocks.firstOrNull { it.text.contains(anchor, ignoreCase = true) } ?: return null
        val anchorY = anchorBlock.centerY
        val maxY = anchorY + imageHeight * searchBelowFraction

        val scoped = blocks.filter { it.centerY in anchorY..maxY }
        return pickBest(
            extractFromBlocks(scoped, imageHeight, minFraction = 0.0, maxFraction = 1.0)
        ) ?: extractFromText(anchorBlock.text).maxOrNull()
    }

    private fun pickBest(candidates: List<AmountCandidate>): Double? {
        if (candidates.isEmpty()) return null
        return candidates
            .sortedWith(
                compareByDescending<AmountCandidate> { it.regionWeight }
                    .thenByDescending { it.amount }
            )
            .first()
            .amount
    }

    private fun hasCurrencyMarker(text: String): Boolean =
        text.contains("₹") ||
            text.contains("Rs", ignoreCase = true) ||
            text.contains("INR", ignoreCase = true)

    private fun parseCurrencyValue(raw: String, context: String): Double? {
        val digitsOnly = raw.replace(",", "")
        if (digitsOnly.isBlank()) return null
        if (digitsOnly.length >= 10) return null
        if (PHONE_PATTERN.containsMatchIn(context)) return null
        if (UTR_PATTERN.containsMatchIn(context) && digitsOnly.length >= 12) return null
        if (TIME_PATTERN.containsMatchIn(context) && !context.contains("₹")) return null

        val value = digitsOnly.toDoubleOrNull() ?: return null
        if (value <= 0 || value >= 1_000_000) return null
        return value
    }
}
