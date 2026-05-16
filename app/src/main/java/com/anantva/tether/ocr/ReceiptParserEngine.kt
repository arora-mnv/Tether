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



data class ParsedReceipt(
    val amount: Double?,
    val receiver: String?,
    val timestamp: Long?,
    val transactionId: String?,
    val upiId: String?,
    val appSource: String?,
    val status: String?,
    val confidence: Float,
    val rawText: String
)

@Singleton
class ReceiptParserEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val parsers: List<ReceiptParser> = listOf(
        GPayParser(),
        PhonePeParser(),
        PaytmParser(),
        CredParser()
    )

    suspend fun process(bitmap: Bitmap): ParsedReceipt = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text

            if (text.isBlank()) {
                return@withContext emptyReceipt(text, 0f)
            }

            val blocks = result.textBlocks.map { block ->
                val rect = block.boundingBox
                OcrBlock(
                    text = block.text,
                    top = rect?.top ?: 0,
                    bottom = rect?.bottom ?: 0
                )
            }
            val imageHeight = bitmap.height

            val normalizedText = normalizeOcrText(text)
            val lines = normalizedText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val ocrLines = lines.map { OcrLine(it) }

            safeLog("ReceiptImport", "OCR lines:\n" + ocrLines.mapIndexed { i, l -> "  $i: ${l.text}" }.joinToString("\n"))
            safeLog("ReceiptImport", "OCR blocks:\n" + blocks.mapIndexed { i, b -> "  $i: ${b.text}" }.joinToString("\n"))

            for (parser in parsers) {
                if (parser.canParse(blocks)) {
                    return@withContext parser.parse(blocks, ocrLines, text, imageHeight)
                }
            }

            return@withContext emptyReceipt(text, 0f)

        } catch (e: Exception) {
            emptyReceipt("Error: ${e.message}", 0f)
        }
    }

    private fun normalizeOcrText(raw: String): String {
        var text = raw

        text = text.replace("₹", "₹")
        text = text.replace("₨", "₹")

        text = text.replace(Regex("""T(?=\d)"""), "₹")

        text = text.replace("Rs.", "₹", ignoreCase = true)
        text = text.replace("INR", "₹", ignoreCase = true)

        text = text.replace("|", "1")
        text = text.replace(Regex("""(?<=\d)O"""), "0")
        text = text.replace(Regex("""(?<=\d)S(?=\d)"""), "5")

        text = text.replace(",", "")

        text = text.replace(Regex("""[ \t]+"""), " ")

        return text.trim()
    }



    private fun extractTransactionId(lines: List<OcrLine>): String? {
        val patterns = listOf(
            Regex("""(?:UPI transaction ID|UPI Ref ID|UTR|Ref ID|Transaction ID)[\s:]*([A-Za-z0-9]{12,})""", RegexOption.IGNORE_CASE),
            Regex("""\b([0-9]{12})\b""")
        )
        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line.text)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }
        return null
    }

    private fun safeLog(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: RuntimeException) {
            // android.util.Log not available (unit tests)
        }
    }

    private fun emptyReceipt(rawText: String, conf: Float) = ParsedReceipt(
        amount = null, receiver = null, timestamp = null, transactionId = null,
        upiId = null, appSource = "Unknown", status = null, confidence = conf, rawText = rawText
    )
}
