package com.anantva.tether.data.local.entity

import org.junit.Test
import org.junit.Assert.*

class RecurringTypeTest {

    @Test
    fun `infer returns SUBSCRIPTION for known subscription merchants`() {
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.infer("", "Netflix"))
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.infer("", "Spotify"))
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.infer("", "Disney+Hotstar"))
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.infer("", "Amazon Prime"))
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.infer("", "Google One"))
    }

    @Test
    fun `infer returns EMI for known EMI merchants`() {
        assertEquals(RecurringType.EMI, RecurringType.infer("", "HDFC BANK"))
        assertEquals(RecurringType.EMI, RecurringType.infer("", "ICICI Bank"))
        assertEquals(RecurringType.EMI, RecurringType.infer("", "Bajaj Finance"))
        assertEquals(RecurringType.EMI, RecurringType.infer("", "Tata Capital"))
    }

    @Test
    fun `infer returns RENT for known rent merchants`() {
        assertEquals(RecurringType.RENT, RecurringType.infer("", "PayRent"))
        assertEquals(RecurringType.RENT, RecurringType.infer("", "Nobroker Rent"))
    }

    @Test
    fun `infer returns BILL for utility merchants`() {
        assertEquals(RecurringType.BILL, RecurringType.infer("", "Electricity Bill"))
        assertEquals(RecurringType.BILL, RecurringType.infer("", "Broadband"))
        assertEquals(RecurringType.BILL, RecurringType.infer("", "Phone Bill"))
    }

    @Test
    fun `infer returns OTHER for unknown merchants`() {
        assertEquals(RecurringType.OTHER, RecurringType.infer("", "Local Cafe"))
        assertEquals(RecurringType.OTHER, RecurringType.infer("", "Uber Ride"))
    }

    @Test
    fun `infer returns SALARY for known salary merchants`() {
        assertEquals(RecurringType.SALARY, RecurringType.infer("", "Salary Credit"))
        assertEquals(RecurringType.SALARY, RecurringType.infer("", "Payroll"))
    }

    @Test
    fun `infer detects insurance keywords`() {
        assertEquals(RecurringType.INSURANCE, RecurringType.infer("", "LIC Premium"))
        assertEquals(RecurringType.INSURANCE, RecurringType.infer("", "HDFC Life Insurance"))
    }

    @Test
    fun `infer handles EMI vs insurance ambiguity correctly`() {
        assertEquals(RecurringType.INSURANCE, RecurringType.infer("", "LIC Premium"))
        assertEquals(RecurringType.EMI, RecurringType.infer("", "HDFC Bank EMI"))
    }

    @Test
    fun `fromCategory maps categories correctly`() {
        assertEquals(RecurringType.EMI, RecurringType.fromCategory("EMI"))
        assertEquals(RecurringType.BILL, RecurringType.fromCategory("Bills & Utilities"))
        assertEquals(RecurringType.RENT, RecurringType.fromCategory("Rent"))
        assertEquals(RecurringType.SUBSCRIPTION, RecurringType.fromCategory("Subscription"))
        assertEquals(RecurringType.SIP, RecurringType.fromCategory("Investments"))
        assertEquals(RecurringType.OTHER, RecurringType.fromCategory("Food & Dining"))
    }

    @Test
    fun `fromCategory returns OTHER for categories without mapping`() {
        assertEquals(RecurringType.OTHER, RecurringType.fromCategory("Entertainment"))
        assertEquals(RecurringType.OTHER, RecurringType.fromCategory("Health"))
        assertEquals(RecurringType.OTHER, RecurringType.fromCategory("Transport"))
    }
}
