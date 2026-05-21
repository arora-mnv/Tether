package com.anantva.tether.ocr

/**
 * OCR cleanup applied before app-specific parsing.
 * Currency normalization is line-scoped for amount extraction; full-text cleanup is for detection only.
 */
object ReceiptOcrNormalizer {

    fun normalizeFullText(raw: String): String =
        raw
            .replace("₨", "₹")
            .replace("|", "1")
            .lines()
            .joinToString("\n") { normalizeLine(it) }

    fun normalizeLine(line: String): String {
        var text = line.trim()
        text = text.replace(Regex("""\bT(?=\d)"""), "₹")
        text = text.replace("Rs.", "₹", ignoreCase = true)
        text = text.replace(Regex("""\bINR\b""", RegexOption.IGNORE_CASE), "₹")
        text = text.replace(Regex("""[ \t]+"""), " ")
        return text.trim()
    }

    fun linesFromText(text: String): List<OcrLine> =
        normalizeFullText(text)
            .lines()
            .map { normalizeLine(it) }
            .filter { it.isNotBlank() }
            .map { OcrLine(it) }
}
