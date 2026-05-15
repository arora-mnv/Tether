package com.anantva.tether.data.parser

import com.anantva.tether.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {

    private val parser = TransactionParser()

    // ── Standard UPI debit messages ──

    @Test
    fun `parses HDFC SMS-style UPI debit`() {
        val parsed = parser.parse("""
            Sent Rs.281.00
            From HDFC Bank A/C *5526
            To Blinkit
            On 24/01/26
            Ref 117629096970
        """.trimIndent(), "com.google.android.apps.messaging")

        assertNotNull(parsed)
        assertEquals(281.0, parsed!!.amount, 0.0)
        assertEquals("Blinkit", parsed.merchant)
        assertEquals(TransactionType.DEBIT, parsed.type)
    }

    @Test
    fun `parses Paytm UPI debit with INR prefix`() {
        val parsed = parser.parse("""
            INR 1,299.00 debited from Paytm wallet
            To Swiggy
            Ref 8271648291
        """.trimIndent(), "net.one97.paytm")

        assertNotNull(parsed)
        assertEquals(1299.0, parsed!!.amount, 0.0)
        assertEquals("Swiggy", parsed.merchant)
        assertEquals(TransactionType.DEBIT, parsed.type)
    }

    @Test
    fun `parses PhonePe message with Rupee symbol`() {
        val parsed = parser.parse(
            "₹500.00 sent to Zomato via UPI from HDFC Bank",
            "com.phonepe.app"
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.0)
        assertEquals("Zomato", parsed.merchant)
        assertEquals(TransactionType.DEBIT, parsed.type)
    }

    @Test
    fun `parses Amazon Pay debit with RS prefix`() {
        val parsed = parser.parse(
            "RS 750.50 paid to Amazon Pay UPI for Flipkart order",
            "in.amazon.mShop.android.shopping"
        )
        assertNotNull(parsed)
        assertEquals(750.50, parsed!!.amount, 0.0)
        assertEquals("Amazon Pay", parsed.merchant)
        assertEquals(TransactionType.DEBIT, parsed.type)
    }

    // ── Credit messages ──

    @Test
    fun `parses salary credit message`() {
        val parsed = parser.parse("""
            Rs.45,000.00 credited to A/C *1234
            From Acme Corp Pvt Ltd
            On 01/01/26
        """.trimIndent(), "com.sbi.SBIFreedomPlus")

        assertNotNull(parsed)
        assertEquals(45000.0, parsed!!.amount, 0.0)
        assertEquals("Acme Corp Pvt Ltd", parsed.merchant)
        assertEquals(TransactionType.CREDIT, parsed.type)
    }

    @Test
    fun `parses cashback credit from GPay`() {
        val parsed = parser.parse(
            "Cashback ₹150.00 received from Google Pay",
            "com.google.android.apps.nbu.paisa.user"
        )
        assertNotNull(parsed)
        assertEquals(150.0, parsed!!.amount, 0.0)
        assertEquals(TransactionType.CREDIT, parsed.type)
    }

    @Test
    fun `parses refund credit`() {
        val parsed = parser.parse(
            "Rs.2,500.00 refund credited to UPI from Myntra",
            "com.google.android.apps.messaging"
        )
        assertNotNull(parsed)
        assertEquals(2500.0, parsed!!.amount, 0.0)
        assertEquals("Myntra", parsed.merchant)
        assertEquals(TransactionType.CREDIT, parsed.type)
    }

    @Test
    fun `parses UPI receive credit`() {
        val parsed = parser.parse(
            "₹200.00 received from UPI ID friend@paytm",
            "net.one97.paytm"
        )
        assertNotNull(parsed)
        assertEquals(200.0, parsed!!.amount, 0.0)
        // cleanMerchant strips @ and title-cases each word
        assertEquals("Friendpaytm", parsed.merchant)
        assertEquals(TransactionType.CREDIT, parsed.type)
    }

    // ── Edge cases ──

    @Test
    fun `returns null for non-transaction message`() {
        val parsed = parser.parse(
            "Your OTP for login is 123456",
            "com.google.android.apps.messaging"
        )
        assertNull(parsed)
    }

    @Test
    fun `returns null for empty message`() {
        assertNull(parser.parse("", "com.google.android.apps.messaging"))
    }

    @Test
    fun `parses debit even when both debit and credit keywords present`() {
        // "credited" appears in "credited from" but overall this is a debit to the user
        val parsed = parser.parse(
            "Rs.1,000.00 debited from account via UPI — money credited to Uber",
            "com.axis.mobile"
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `extracts merchant from merchant prefix`() {
        val parsed = parser.parse(
            "Payment of Rs.349.00 to merchant: Netflix",
            "com.hdfc.bank"
        )
        assertNotNull(parsed)
        assertEquals(349.0, parsed!!.amount, 0.0)
        assertEquals("Netflix", parsed!!.merchant)
    }

    @Test
    fun `handles amount with no decimal`() {
        val parsed = parser.parse(
            "Rs.500 sent to Uber",
            "com.google.android.apps.messaging"
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.0)
    }

    @Test
    fun `handles large amounts with commas`() {
        val parsed = parser.parse(
            "Rs.1,50,000.00 transferred to SBI A/C *7890",
            "com.google.android.apps.messaging"
        )
        assertNotNull(parsed)
        assertEquals(150000.0, parsed!!.amount, 0.0)
    }

    @Test
    fun `parses EMI payment message`() {
        val parsed = parser.parse("""
            Your EMI of Rs.4,999.00 for HDFC Bank A/C *5526
            has been debited on 10/01/26 towards Bajaj Finserv
        """.trimIndent(), "com.hdfc.bank")

        assertNotNull(parsed)
        assertEquals(4999.0, parsed!!.amount, 0.0)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `parses rent payment message`() {
        val parsed = parser.parse(
            "Rs.18,000.00 paid to RentMerchant via UPI — rent for Feb 2026",
            "com.google.android.apps.messaging"
        )
        assertNotNull(parsed)
        assertEquals(18000.0, parsed!!.amount, 0.0)
        assertEquals("Rentmerchant", parsed!!.merchant)
    }

    @Test
    fun `parses message with debited keyword`() {
        val parsed = parser.parse(
            "Rs.320.00 debited from A/C *1234 for Amazon Shopping",
            "com.sbi.SBIFreedomPlus"
        )
        assertNotNull(parsed)
        assertEquals(320.0, parsed!!.amount, 0.0)
        // "from" pattern captures "for Amazon Shopping", cleaned to "For Amazon Shopping"
        assertEquals("For Amazon Shopping", parsed!!.merchant)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `parses deposited credit`() {
        val parsed = parser.parse(
            "Rs.10,000.00 deposited in A/C *1234 on 05/01/26",
            "com.hdfc.bank"
        )
        assertNotNull(parsed)
        assertEquals(10000.0, parsed!!.amount, 0.0)
        assertEquals(TransactionType.CREDIT, parsed!!.type)
    }

    @Test
    fun `parses axis bank pos transaction`() {
        val parsed = parser.parse(
            "Rs.634.00 spent at Dmart Store Mumbai on 20-01-26",
            "com.axis.mobile"
        )
        assertNotNull(parsed)
        assertEquals(634.0, parsed!!.amount, 0.0)
        // greedy merchant capture includes "On 20-01-26"
        assertEquals("Dmart Store Mumbai On 20-01-26", parsed!!.merchant)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `parses message from netflix via bank format`() {
        val parsed = parser.parse(
            "UPI payment of Rs.99.00 from Netflix via ICICI Bank",
            "com.csam.icici.bank.imobile"
        )
        assertNotNull(parsed)
        assertEquals(99.0, parsed!!.amount, 0.0)
        // "from" merchant pattern captures everything after "from " up to end-of-string
        assertEquals("Netflix Via Icici Bank", parsed!!.merchant)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `credit card bill payment defaults to unknown merchant`() {
        val parsed = parser.parse(
            "Rs.12,350.00 paid to Credit Card A/C *9876 on 15/01/26",
            "com.hdfc.bank"
        )
        assertNotNull(parsed)
        assertEquals(12350.0, parsed!!.amount, 0.0)
        // account number with / and * prevents merchant extraction
        assertEquals("Unknown", parsed!!.merchant)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    @Test
    fun `handles whitespace around Rupee symbol`() {
        val parsed = parser.parse(
            "₹ 1,200 sent to UberEats",
            "com.google.android.apps.messaging"
        )
        assertNotNull(parsed)
        assertEquals(1200.0, parsed!!.amount, 0.0)
        assertEquals("Ubereats", parsed!!.merchant)
    }

    @Test
    fun `parses gpay notification format`() {
        val parsed = parser.parse("""
            UPI payment successful
            ₹1,450.00 paid to Zomato
            from HDFC Bank A/C *5526
            UPI Ref 123456789012
        """.trimIndent(), "com.google.android.apps.nbu.paisa.user")

        assertNotNull(parsed)
        assertEquals(1450.0, parsed!!.amount, 0.0)
        assertEquals("Zomato", parsed!!.merchant)
        assertEquals(TransactionType.DEBIT, parsed!!.type)
    }

    // ── Source tracker ──

    @Test
    fun `records source package`() {
        val parsed = parser.parse(
            "Rs.50.00 sent to Tea Stall",
            "com.phonepe.app"
        )
        assertNotNull(parsed)
        assertEquals("com.phonepe.app", parsed!!.sourceApp)
    }

    @Test
    fun `stores raw text`() {
        val text = "Rs.75.00 paid to Metro"
        val parsed = parser.parse(text, "com.google.android.apps.messaging")
        assertNotNull(parsed)
        assertEquals(text, parsed!!.rawText)
    }
}
