package com.anantva.tether.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptAmountExtractorTest {

    @Test
    fun extractsOnlyCurrencyMarkedValues() {
        assertEquals(listOf(500.0), ReceiptAmountExtractor.extractFromText("₹500"))
        assertEquals(listOf(1050.0), ReceiptAmountExtractor.extractFromText("Rs. 1050"))
        assertEquals(listOf(400.0), ReceiptAmountExtractor.extractFromText("INR 400"))
    }

    @Test
    fun rejectsBareNumbers() {
        assertTrue(ReceiptAmountExtractor.extractFromText("123193706341").isEmpty())
        assertTrue(ReceiptAmountExtractor.extractFromText("9034330753").isEmpty())
    }

    @Test
    fun regionPriority_prefersTopAmount() {
        val blocks = listOf(
            OcrBlock("₹500", top = 50, bottom = 120),
            OcrBlock("UPI Ref 441634252587", top = 800, bottom = 860)
        )
        val top = ReceiptAmountExtractor.largestInRegion(blocks, imageHeight = 1000, maxFraction = 0.4)
        assertEquals(500.0, top)
    }
}
