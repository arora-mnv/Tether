package com.anantva.tether.insights

import com.anantva.tether.data.local.entity.SpendingCategories
import com.anantva.tether.data.local.entity.TxnCategory
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class PredictionTest {

    private val zone = ZoneId.systemDefault()
    private val now = java.time.LocalDate.now()

    private data class ThirtyDayPrediction(
        val projectedSpend: Int,
        val dailyAverage: Int,
        val topCategory: String,
        val confidence: Float
    )

    private fun computePrediction(transactions: List<PredictionTransaction>): ThirtyDayPrediction {
        val today = java.time.LocalDate.now()
        val start = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val recent = transactions.filter {
            it.status == "CONFIRMED" && it.type == "Expense" &&
                it.categoryType == TxnCategory.NORMAL && it.date in start..end
        }
        if (recent.isEmpty()) {
            return ThirtyDayPrediction(0, 0, "No pattern", 0f)
        }

        val activeDays = recent.map {
            Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
        }.distinct().size.coerceAtLeast(1)

        val observedWindow = minOf(30, java.time.temporal.ChronoUnit.DAYS.between(
            recent.minOf { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() },
            today
        ).toInt() + 1).coerceAtLeast(activeDays)

        val dailyAvg = (recent.sumOf { it.amount } / observedWindow).roundToInt()
        val topCat = recent.groupBy { it.category.ifBlank { SpendingCategories.OTHER } }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
            ?.key ?: "No pattern"

        val conf = when {
            activeDays >= 15 -> 0.9f
            activeDays >= 8 -> 0.7f
            activeDays >= 4 -> 0.45f
            else -> 0.25f
        }
        return ThirtyDayPrediction(
            projectedSpend = dailyAvg * 30,
            dailyAverage = dailyAvg,
            topCategory = topCat,
            confidence = conf
        )
    }

    data class PredictionTransaction(
        val date: Long,
        val amount: Double,
        val category: String,
        val status: String = "CONFIRMED",
        val type: String = "Expense",
        val categoryType: TxnCategory = TxnCategory.NORMAL
    )

    private fun daysAgo(n: Long): Long {
        val date = now.minusDays(n)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    @Test
    fun `empty transactions returns zero prediction`() {
        val result = computePrediction(emptyList())
        assertEquals(0, result.projectedSpend)
        assertEquals(0, result.dailyAverage)
        assertEquals("No pattern", result.topCategory)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `single day of spending returns low confidence`() {
        val txns = listOf(
            PredictionTransaction(date = daysAgo(0), amount = 100.0, category = "Food & Dining"),
            PredictionTransaction(date = daysAgo(0), amount = 50.0, category = "Food & Dining")
        )
        val result = computePrediction(txns)
        assertEquals(0.25f, result.confidence)
        assertEquals("Food & Dining", result.topCategory)
    }

    @Test
    fun `consistent daily spending over 2 weeks gives medium confidence`() {
        val txns = (0L..13L).flatMap { day ->
            listOf(
                PredictionTransaction(date = daysAgo(day), amount = 200.0, category = "Food & Dining")
            )
        }
        val result = computePrediction(txns)
        assertEquals(0.7f, result.confidence)
        assertTrue(result.dailyAverage > 0)
        assertTrue(result.projectedSpend > 0)
    }

    @Test
    fun `consistent daily spending over 3 weeks gives high confidence`() {
        val txns = (0L..20L).flatMap { day ->
            listOf(
                PredictionTransaction(date = daysAgo(day), amount = 200.0, category = "Food & Dining")
            )
        }
        val result = computePrediction(txns)
        assertEquals(0.9f, result.confidence)
        assertTrue(result.dailyAverage > 0)
        assertTrue(result.projectedSpend > 0)
    }

    @Test
    fun `top category reflects highest spend`() {
        val txns = listOf(
            PredictionTransaction(date = daysAgo(0), amount = 500.0, category = "Shopping"),
            PredictionTransaction(date = daysAgo(0), amount = 100.0, category = "Food & Dining"),
            PredictionTransaction(date = daysAgo(1), amount = 300.0, category = "Shopping"),
            PredictionTransaction(date = daysAgo(1), amount = 50.0, category = "Food & Dining")
        )
        val result = computePrediction(txns)
        assertEquals("Shopping", result.topCategory)
    }

    @Test
    fun `recurring transactions are excluded from prediction`() {
        val txns = listOf(
            PredictionTransaction(
                date = daysAgo(0), amount = 500.0, category = "Shopping",
                categoryType = TxnCategory.RECURRING
            ),
            PredictionTransaction(
                date = daysAgo(1), amount = 500.0, category = "Shopping",
                categoryType = TxnCategory.RECURRING
            )
        )
        val result = computePrediction(txns)
        assertEquals("No pattern", result.topCategory)
        assertEquals(0f, result.confidence)
    }
}
