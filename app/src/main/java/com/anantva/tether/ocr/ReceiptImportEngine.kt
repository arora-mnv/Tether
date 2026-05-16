package com.anantva.tether.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReceiptResult(
    val amount: Double? = null,
    val merchant: String? = null,
    val confidence: Float = 0f,
    val rawText: String = "",
    val error: String? = null
)

@Singleton
class ReceiptImportEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun process(bitmap: Bitmap): ReceiptResult = withContext(Dispatchers.Default) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text
            if (text.isBlank()) {
                return@withContext ReceiptResult(confidence = 0f, rawText = text, error = "No text found")
            }

            val lines = text.lines().filter { it.isNotBlank() }

            val amount = extractAmount(text)
            val merchant = extractMerchant(lines)
            val confidence = computeConfidence(amount, merchant)

            ReceiptResult(
                amount = amount,
                merchant = merchant,
                confidence = confidence,
                rawText = text
            )
        } catch (e: Exception) {
            ReceiptResult(confidence = 0f, error = e.message ?: "OCR failed")
        }
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:Rs\.?|₹|INR)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""([\d,]+\.\d{2})\s*(?:Rs\.?|₹|INR)?""", RegexOption.IGNORE_CASE),
            Regex("""(?:Amount|Total|Paid|Due)\s*[:.]?\s*(?:Rs\.?|₹|INR)?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""\b(\d{3,})\b""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val raw = match.groupValues[1].replace(",", "")
            val parsed = raw.toDoubleOrNull()
            if (parsed != null && parsed > 0 && parsed < 1_000_000) return parsed
        }
        return null
    }

    private fun extractMerchant(lines: List<String>): String? {
        val skipWords = setOf(
            "amount", "total", "paid", "date", "time", "ref", "upi", "status",
            "successful", "received", "sent", "from", "to", "bank", "account"
        )
        for (line in lines) {
            val trimmed = line.trim().lowercase()
            if (trimmed.length < 3 || trimmed.length > 40) continue
            if (skipWords.any { trimmed.startsWith(it) }) continue
            if (trimmed.any { it.isDigit() }) continue
            return line.trim()
        }
        return null
    }

    private fun computeConfidence(amount: Double?, merchant: String?): Float {
        var score = 0f
        if (amount != null && amount > 0) score += 0.5f
        if (merchant != null && merchant.length >= 3) score += 0.3f
        if (amount != null && merchant != null) score += 0.2f
        return score.coerceIn(0f, 1f)
    }
}
