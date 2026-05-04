package com.anantva.tether.ui_elements.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.data.local.dao.CategorySpend
import com.anantva.tether.insights.InsightsEngine
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.VintageCream

private val DarkBg = Color(0xFF0F0F0F)
private val CardBg = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)
private val SafeGreen = Color(0xFF7FAE65)
private val StreakRed = Color(0xFFD32F2F)
private val StreakOrange = Color(0xFFE65100)
private val StreakPurple = Color(0xFF6A1B9A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    innerPadding: PaddingValues,
    insightsState: InsightsUiState,
    spendTrendValues: List<Int>,
    trendLabels: List<String>,
    uiState: DashboardUiState,
    onRefresh: () -> Unit = {}
) {
    if (insightsState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = TetherRed)
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Insights",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        item { WeeklyStreakCard(uiState = uiState) }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            SmartInsightCard(
                message = insightsState.dailyInsightMessage,
                healthScore = insightsState.dailyHealthScore
            )
        }

        item { Spacer(Modifier.height(18.dp)) }

        item { DailyBreakdownCard(insightsState) }

        item { Spacer(Modifier.height(18.dp)) }

        item { SpendingTrendSection(spendTrendValues, trendLabels) }

        item { Spacer(Modifier.height(18.dp)) }

        item { GoalProgressCard(uiState = uiState) }

        item { Spacer(Modifier.height(18.dp)) }

        item { WeeklyReportCard(insightsState, uiState) }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun SmartInsightCard(message: String, healthScore: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today's read",
                    color = GrimeGrey,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    TetherRed.copy(alpha = 0.8f),
                                    TetherRed.copy(alpha = 0.4f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(healthScore * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message.ifEmpty { "No data yet. Start spending to get insights." },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun DailyBreakdownCard(state: InsightsUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Text(
                text = "Today's breakdown",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStat("Needs", formatCurrency(state.dailyNeedSpend), SafeGreen)
                MiniStat("Wants", formatCurrency(state.dailyWantSpend), TetherRed)
                MiniStat("Ratio", if (state.dailyNeedWantRatio.isInfinite()) "∞" else String.format("%.1f", state.dailyNeedWantRatio), GrimeGrey)
            }

            if (state.dailyCategoryBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141414))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "Top categories",
                            fontSize = 12.sp,
                            color = GrimeGrey
                        )
                        Spacer(Modifier.height(8.dp))
                        state.dailyCategoryBreakdown.take(3).forEach { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = cat.category, fontSize = 13.sp, color = Color.White)
                                Text(text = formatCurrency(cat.total), fontSize = 13.sp, color = GrimeGrey, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = GrimeGrey)
        Spacer(Modifier.height(4.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun WeeklyStreakCard(uiState: DashboardUiState) {
    val streakColor = when {
        uiState.streakDays < 100 -> StreakRed
        uiState.streakDays < 200 -> lerp(StreakRed, StreakOrange, ((uiState.streakDays - 100).toFloat() / 100f).coerceIn(0f, 1f))
        else -> lerp(StreakOrange, StreakPurple, ((uiState.streakDays - 200).toFloat() / 165f).coerceIn(0f, 1f))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(colors = listOf(streakColor, streakColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔥",
                    fontSize = 26.sp
                )
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    text = "${uiState.streakDays} day streak",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = streakMotivation(uiState.streakDays),
                    fontSize = 13.sp,
                    color = GrimeGrey
                )
            }
        }
    }
}

private fun streakMotivation(days: Int): String {
    return when {
        days >= 30 -> "You're a different breed. Keep going."
        days >= 14 -> "Two weeks. The streak respects you."
        days >= 7 -> "A week strong. Don't fumble it."
        days >= 3 -> "It's alive. Protect it."
        days >= 1 -> "Streak started. Day one."
        else -> "No streak yet. Today could be day one."
    }
}

@Composable
private fun SpendingTrendSection(trendValues: List<Int>, labels: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Spending trend",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                ) {
                    val topPadding = 8.dp.toPx()
                    val bottomPadding = 10.dp.toPx()
                    val graphHeight = size.height - topPadding - bottomPadding
                    val maxValue = trendValues.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val minValue = trendValues.minOrNull()?.coerceAtLeast(0) ?: 0
                    val range = (maxValue - minValue).coerceAtLeast(1)
                    val stepX = size.width / (trendValues.lastIndex.coerceAtLeast(1))

                    repeat(3) { lineIndex ->
                        val y = topPadding + graphHeight * lineIndex / 2f
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val points = trendValues.mapIndexed { index, value ->
                        val normalized = (value - minValue).toFloat() / range.toFloat()
                        Offset(
                            x = stepX * index,
                            y = topPadding + graphHeight * (1f - normalized)
                        )
                    }

                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(
                            color = TetherRed,
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    points.forEach { point ->
                        drawCircle(
                            color = TetherRed,
                            radius = 3.dp.toPx(),
                            center = point
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    labels.forEach { label ->
                        Text(
                            text = label,
                            color = Color(0xFF6F6F6F),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoalProgressCard(uiState: DashboardUiState) {
    if (uiState.savingsGoal <= 0.0 || uiState.monthlyCommitment <= 0.0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal progress",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Text(
                    text = formatCurrency(uiState.savingsGoal),
                    color = GrimeGrey,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { uiState.goalProgressPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = if (uiState.isGoalCompleted) VintageCream else TetherRed,
                trackColor = Color(0xFF2A2A2A)
            )

            Spacer(Modifier.height(10.dp))

            val pctText = (uiState.goalProgressPct * 100).toInt().coerceIn(0, 100)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$pctText% complete",
                    color = GrimeGrey,
                    fontSize = 13.sp
                )
                Text(
                    text = if (uiState.isGoalCompleted)
                        "Goal completed!"
                    else
                        "${formatCurrency(uiState.goalRemainingAmount)} remaining",
                    color = if (uiState.isGoalCompleted) VintageCream else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WeeklyReportCard(insightsState: InsightsUiState, uiState: DashboardUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "This week",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                val trendIcon = when (insightsState.weeklyTrendDirection) {
                    InsightsEngine.TrendDirection.UP -> "↗"
                    InsightsEngine.TrendDirection.DOWN -> "↘"
                    InsightsEngine.TrendDirection.STABLE -> "→"
                }
                Text(
                    text = trendIcon,
                    fontSize = 18.sp,
                    color = when (insightsState.weeklyTrendDirection) {
                        InsightsEngine.TrendDirection.UP -> TetherRed
                        InsightsEngine.TrendDirection.DOWN -> SafeGreen
                        InsightsEngine.TrendDirection.STABLE -> GrimeGrey
                    }
                )
            }
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReportStat(
                    label = "Total spent",
                    value = formatCurrency(insightsState.weeklyTotalSpend),
                    color = TetherRed
                )
                ReportStat(
                    label = "Avg/day",
                    value = formatCurrency(insightsState.weeklyAvgDailySpend),
                    color = Color.White
                )
                ReportStat(
                    label = "Peak day",
                    value = insightsState.weeklyPeakDay,
                    color = GrimeGrey
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ReportStat(
                    label = "Needs",
                    value = formatCurrency(insightsState.weeklyNeedVsWant.first),
                    color = SafeGreen
                )
                ReportStat(
                    label = "Wants",
                    value = formatCurrency(insightsState.weeklyNeedVsWant.second),
                    color = TetherRed
                )
                ReportStat(
                    label = "Top category",
                    value = insightsState.weeklyCategoryBreakdown.firstOrNull()?.category ?: "—",
                    color = Color.White
                )
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = insightsState.weeklyInsightMessage,
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReportStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = GrimeGrey
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
