package com.anantva.tether.calculator.use_case

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalculateDailyLimitUseCaseTest {

    private val useCase = CalculateDailyLimitUseCase()

    @Test
    fun `normal budget with balance exceeding commitment`() {
        val result = useCase(
            currentBalance = 30000,
            monthlyCommitment = 10000,
            spentToday = 500,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // spendable = 30000 - 10000 = 20000
        // remainingDays = 31 - 1 + 1 = 31
        // dailyLimit = 20000 / 31 = 645
        assertEquals(645, result.dailyLimit)
        assertEquals(145, result.remainingToday)  // 645 - 500
        assertFalse(result.exceeded)
    }

    @Test
    fun `overspending detected`() {
        val result = useCase(
            currentBalance = 30000,
            monthlyCommitment = 10000,
            spentToday = 1000,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // spendable = 20000, remainingDays = 31
        // dailyLimit = 645, remainingToday = 645 - 1000 = -355
        assertEquals(645, result.dailyLimit)
        assertEquals(-355, result.remainingToday)
        assertTrue(result.exceeded)
    }

    @Test
    fun `zero balance results in zero daily limit`() {
        val result = useCase(
            currentBalance = 0,
            monthlyCommitment = 10000,
            spentToday = 0,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        // spendable = 0 - 10000 = -10000 → negative → limit 0
        assertEquals(0, result.dailyLimit)
        assertEquals(0, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `negative balance results in zero daily limit`() {
        val result = useCase(
            currentBalance = -5000,
            monthlyCommitment = 10000,
            spentToday = 200,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        assertEquals(0, result.dailyLimit)
        // remainingToday = 0 - 200 = -200
        assertEquals(-200, result.remainingToday)
        assertTrue(result.exceeded)
    }

    @Test
    fun `balance exactly equals commitment gives zero spendable`() {
        val result = useCase(
            currentBalance = 10000,
            monthlyCommitment = 10000,
            spentToday = 0,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        // spendable = 10000 - 10000 = 0
        // dailyLimit = 0 / remainingDays = 0
        assertEquals(0, result.dailyLimit)
        assertEquals(0, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `last day of month uses remaining day count of 1`() {
        val result = useCase(
            currentBalance = 10000,
            monthlyCommitment = 2000,
            spentToday = 0,
            currentDate = LocalDate.of(2026, 5, 31)
        )
        // spendable = 8000, remainingDays = 31 - 31 + 1 = 1
        // dailyLimit = 8000 / 1 = 8000
        assertEquals(8000, result.dailyLimit)
        assertEquals(8000, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `mid month calculation`() {
        val result = useCase(
            currentBalance = 50000,
            monthlyCommitment = 15000,
            spentToday = 300,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        // spendable = 35000, remainingDays = 31 - 15 + 1 = 17
        // dailyLimit = 35000 / 17 = 2058
        assertEquals(2058, result.dailyLimit)
        assertEquals(1758, result.remainingToday)  // 2058 - 300
        assertFalse(result.exceeded)
    }

    @Test
    fun `short month February calculation`() {
        val result = useCase(
            currentBalance = 20000,
            monthlyCommitment = 5000,
            spentToday = 100,
            currentDate = LocalDate.of(2026, 2, 1)
        )
        // spendable = 15000, Feb 2026 has 28 days
        // remainingDays = 28 - 1 + 1 = 28
        // dailyLimit = 15000 / 28 = 535
        assertEquals(535, result.dailyLimit)
        assertEquals(435, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `overspent already before calculation`() {
        val result = useCase(
            currentBalance = 30000,
            monthlyCommitment = 10000,
            spentToday = 10000,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // dailyLimit = 645, remainingToday = 645 - 10000 = -9355
        assertEquals(645, result.dailyLimit)
        assertEquals(-9355, result.remainingToday)
        assertTrue(result.exceeded)
    }

    @Test
    fun `large balance produces reasonable daily limit`() {
        val result = useCase(
            currentBalance = 500000,
            monthlyCommitment = 100000,
            spentToday = 5000,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // spendable = 400000, remainingDays = 31
        // dailyLimit = 400000 / 31 = 12903
        assertEquals(12903, result.dailyLimit)
        assertEquals(7903, result.remainingToday)  // 12903 - 5000
        assertFalse(result.exceeded)
    }

    @Test
    fun `balance barely covers commitment`() {
        val result = useCase(
            currentBalance = 10500,
            monthlyCommitment = 10000,
            spentToday = 50,
            currentDate = LocalDate.of(2026, 5, 15)
        )
        // spendable = 500, remainingDays = 17
        // dailyLimit = 500 / 17 = 29
        assertEquals(29, result.dailyLimit)
        assertEquals(-21, result.remainingToday)  // 29 - 50
        assertTrue(result.exceeded)
    }

    @Test
    fun `february leap year has 29 days`() {
        val result = useCase(
            currentBalance = 30000,
            monthlyCommitment = 5000,
            spentToday = 0,
            currentDate = LocalDate.of(2024, 2, 1)  // 2024 is a leap year
        )
        // spendable = 25000, remainingDays = 29 - 1 + 1 = 29
        // dailyLimit = 25000 / 29 = 862
        assertEquals(862, result.dailyLimit)
        assertEquals(862, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `last day of February leap year`() {
        val result = useCase(
            currentBalance = 10000,
            monthlyCommitment = 2000,
            spentToday = 100,
            currentDate = LocalDate.of(2024, 2, 29)
        )
        // spendable = 8000, remainingDays = 1
        // dailyLimit = 8000
        assertEquals(8000, result.dailyLimit)
        assertEquals(7900, result.remainingToday)
        assertFalse(result.exceeded)
    }

    @Test
    fun `small balance early in month`() {
        val result = useCase(
            currentBalance = 5000,
            monthlyCommitment = 3000,
            spentToday = 50,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // spendable = 2000, remainingDays = 31
        // dailyLimit = 2000 / 31 = 64
        assertEquals(64, result.dailyLimit)
        assertEquals(14, result.remainingToday)  // 64 - 50
        assertFalse(result.exceeded)
    }

    @Test
    fun `zero spent today does not trigger exceeded`() {
        val result = useCase(
            currentBalance = 30000,
            monthlyCommitment = 10000,
            spentToday = 0,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        assertFalse(result.exceeded)
        assertEquals(645, result.dailyLimit)
        assertEquals(645, result.remainingToday)
    }

    @Test
    fun `integer division truncates fractional result`() {
        val result = useCase(
            currentBalance = 10000,
            monthlyCommitment = 3000,
            spentToday = 0,
            currentDate = LocalDate.of(2026, 5, 1)
        )
        // spendable = 7000, remainingDays = 31
        // 7000 / 31 = 225.8... → integer division = 225
        assertEquals(225, result.dailyLimit)
    }
}
