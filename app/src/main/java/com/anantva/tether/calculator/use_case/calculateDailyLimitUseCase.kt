package com.anantva.tether.calculator.use_case

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class DailyLimitResult(
    val dailyLimit: Int,
    val remainingToday: Int,
    val exceeded: Boolean
)

class CalculateDailyLimitUseCase @Inject constructor() {

    operator fun invoke(
        currentBalance: Int,
        monthlyCommitment: Int,
        spentToday: Int,
        currentDate: LocalDate = LocalDate.now()
    ): DailyLimitResult {
        // spendable = currentBalance - monthlyCommitment
        val spendable = currentBalance - monthlyCommitment
        
        // If currentBalance is negative → dailyLimit = 0
        // If spendable is negative → dailyLimit = 0
        if (currentBalance < 0 || spendable < 0) {
            val remainingToday = 0 - spentToday
            return DailyLimitResult(
                dailyLimit = 0,
                remainingToday = remainingToday,
                exceeded = remainingToday < 0
            )
        }
        
        // remainingDaysInMonth: Include today, ensure minimum value = 1
        val month = YearMonth.from(currentDate)
        val daysInMonth = month.lengthOfMonth()
        val remainingDaysInMonth = (daysInMonth - currentDate.dayOfMonth + 1).coerceAtLeast(1)
        
        // dailyLimit = spendable / remainingDaysInMonth
        val dailyLimit = spendable / remainingDaysInMonth
        
        // remainingToday = dailyLimit - spentToday
        val remainingToday = dailyLimit - spentToday
        
        // exceeded = remainingToday < 0
        val exceeded = remainingToday < 0
        
        return DailyLimitResult(
            dailyLimit = dailyLimit,
            remainingToday = remainingToday,
            exceeded = exceeded
        )
    }
}
