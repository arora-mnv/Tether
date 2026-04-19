package com.anantva.tether.data.parser

import com.anantva.tether.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransactionParserTest {

    private val parser = TransactionParser()

    @Test
    fun parsesSmsStyleHdfcDebitMessage() {
        val message = """
            Sent Rs.281.00
            From HDFC Bank A/C *5526
            To Blinkit
            On 24/01/26
            Ref 117629096970
            Not You?
            Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()

        val parsed = parser.parse(
            text = message,
            sourcePackage = "com.google.android.apps.messaging"
        )

        assertNotNull(parsed)
        assertEquals(281.0, parsed!!.amount, 0.0)
        assertEquals("Blinkit", parsed.merchant)
        assertEquals(TransactionType.DEBIT, parsed.type)
    }
}
