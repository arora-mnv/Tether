package com.anantva.tether.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptProfileParsersTest {

    private fun makeBlocks(text: String, imageHeight: Int = 1000): List<OcrBlock> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val lineHeight = imageHeight / lines.size
        return lines.mapIndexed { index, line ->
            OcrBlock(text = line, top = index * lineHeight, bottom = (index + 1) * lineHeight)
        }
    }

    private fun makeLines(text: String): List<OcrLine> {
        return text.lines().map { it.trim() }.filter { it.isNotBlank() }.map { OcrLine(it) }
    }

    @Test
    fun testGooglePayParser() {
        val ocrText = """
            To Anamika Arora
            +91 90343 30753
            ₹500
            Pay again
            Completed
            15 May 2026, 10:14pm
            HDFC Bank 5526
            UPI transaction ID
            123193706341
            To: ANAMIKA ARORA
            Google Pay • contactanamika12-1@okaxis
            From: MOHIT ARORA (HDFC Bank)
            Google Pay • manavarora191100-3@okhdfcbank
            Google transaction ID
            CICAgOjXk4uhVg
        """.trimIndent()

        val parser = GPayParser()
        val blocks = makeBlocks(ocrText)
        val lines = makeLines(ocrText)

        assertTrue("Parser should detect Google Pay", parser.canParse(blocks))

        val result = parser.parse(blocks, lines, ocrText, 1000)

        assertEquals("Anamika Arora", result.receiver)
        assertEquals(500.0, result.amount)
        assertEquals("Completed", result.status)
        assertEquals("Google Pay", result.appSource)
    }

    @Test
    fun testPaytmParser() {
        val ocrText = """
            paytm
            Payment Successful
            ₹500
            Rupees Five Hundred Only
            To: Geeta Rani
            Paytm Payments Bank - 6915
            From: Raj Bhargava
            State Bank Of India - 6460
            UPI Ref. No: 4416342 52587
            19 Feb 2024 , 07:16 PM
        """.trimIndent()

        val parser = PaytmParser()
        val blocks = makeBlocks(ocrText)
        val lines = makeLines(ocrText)

        assertTrue("Parser should detect Paytm", parser.canParse(blocks))

        val result = parser.parse(blocks, lines, ocrText, 1000)

        assertEquals("Geeta Rani", result.receiver)
        assertEquals(500.0, result.amount)
        assertEquals("Completed", result.status)
        assertNull(result.transactionId)
        assertEquals("Paytm", result.appSource)
    }
}
