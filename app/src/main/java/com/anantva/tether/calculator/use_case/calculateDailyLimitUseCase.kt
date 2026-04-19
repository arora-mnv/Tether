package com.anantva.tether.calculator.use_case

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DailyLimitResult(
    val dailyLimit: Double,
    val dailyNetSpent: Double,
    val dailyLimitRemaining: Double,
    val isOverLimit: Boolean,
    val daysRemainingInMonth: Int
)

class CalculateDailyLimitUseCase @Inject constructor() {

    operator fun invoke(
        currentBalance: Double,
        monthlyCommitment: Double,
        dailyNetSpent: Double,
        date: LocalDate = LocalDate.now()
    ): DailyLimitResult {
        val month = YearMonth.from(date)
        val daysInMonth = month.lengthOfMonth()
        val daysRemainingInMonth = (daysInMonth - date.dayOfMonth + 1).coerceAtLeast(1)

        val spendablePool = (currentBalance - monthlyCommitment).coerceAtLeast(0.0)
        val dailyLimit = spendablePool / daysRemainingInMonth

        val remaining = (dailyLimit - dailyNetSpent).coerceAtLeast(0.0)
        val isOver = dailyLimit > 0.0 && dailyNetSpent > dailyLimit

        return DailyLimitResult(
            dailyLimit = dailyLimit,
            dailyNetSpent = dailyNetSpent,
            dailyLimitRemaining = remaining,
            isOverLimit = isOver,
            daysRemainingInMonth = daysRemainingInMonth
        )
    }
}
