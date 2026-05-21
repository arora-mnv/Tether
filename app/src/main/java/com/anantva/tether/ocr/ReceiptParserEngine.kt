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

            val normalizedText = ReceiptOcrNormalizer.normalizeFullText(text)
            val blocks = result.textBlocks.map { block ->
                val rect = block.boundingBox
                OcrBlock(
                    text = ReceiptOcrNormalizer.normalizeLine(block.text),
                    top = rect?.top ?: 0,
                    bottom = rect?.bottom ?: 0
                )
            }
            val imageHeight = bitmap.height

            val ocrLines = ReceiptOcrNormalizer.linesFromText(normalizedText)
            val detectedApp = ReceiptAppDetector.detect(blocks, ocrLines)

            safeLog("ReceiptImport", "OCR lines:\n" + ocrLines.mapIndexed { i, l -> "  $i: ${l.text}" }.joinToString("\n"))
            safeLog("ReceiptImport", "OCR blocks:\n" + blocks.mapIndexed { i, b -> "  $i: ${b.text}" }.joinToString("\n"))

            val parser = parsers.firstOrNull { it.app == detectedApp }
                ?: parsers.firstOrNull { it.canParse(blocks) }

            if (parser != null) {
                val parsed = parser.parse(blocks, ocrLines, normalizedText, imageHeight)
                return@withContext parsed.copy(detectedApp = parser.app)
            }

            return@withContext emptyReceipt(normalizedText, 0f).copy(detectedApp = detectedApp)

        } catch (e: Exception) {
            emptyReceipt("Error: ${e.message}", 0f)
        }
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
