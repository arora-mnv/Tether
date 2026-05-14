package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.VintageCream
import kotlin.math.sin
import kotlin.math.PI

private val CardBg = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)
private val SubtextGrey = Color(0xFF6F6F6F)
private val DimWhite = Color.White.copy(alpha = 0.7f)

private val MoodClean = Color(0xFF7FAE65)
private val MoodGood = Color(0xFF4FC3F7)
private val MoodMixed = Color(0xFFFFB74D)
private val MoodBad = Color(0xFFE53935)
private val MoodGreat = Color(0xFF81C784)

private data class HeroMoodStyle(
    val glowStart: Color,
    val glowEnd: Color,
    val accent: Color,
    val ornamentHue: String
)

private fun heroMood(mood: String): HeroMoodStyle = when (mood) {
    "great" -> HeroMoodStyle(Color(0xFF1A3A2A), Color(0xFF0F1F15), MoodGreat, "great")
    "good" -> HeroMoodStyle(Color(0xFF1A2A3A), Color(0xFF0F151F), MoodGood, "good")
    "clean" -> HeroMoodStyle(Color(0xFF1A1A2A), Color(0xFF0F0F1F), Color(0xFF7FAE65), "clean")
    "mixed" -> HeroMoodStyle(Color(0xFF2A2520), Color(0xFF1F1A15), MoodMixed, "mixed")
    "bad" -> HeroMoodStyle(Color(0xFF2A1A1A), Color(0xFF1F0F0F), MoodBad, "bad")
    else -> HeroMoodStyle(CardBg, Color(0xFF151515), GrimeGrey, "quiet")
}

private fun vibeColor(personality: String): Color = when (personality) {
    "Elite", "Consistent", "Controlled", "Balanced" -> MoodGood
    "Clean", "Steady" -> MoodClean
    "Impulsive", "Reactive", "Chaotic" -> MoodBad
    "Survival mode", "Rebounding" -> MoodMixed
    else -> GrimeGrey
}

private data class DayMood(
    val label: String,
    val amount: Int,
    val level: String
)

private fun computeDayMoods(values: List<Int>, labels: List<String>): List<DayMood> {
    if (values.isEmpty() || labels.isEmpty()) return emptyList()
    val avg = values.average().toInt().coerceAtLeast(1)
    return values.zip(labels).map { (amount, label) ->
        val level = when {
            amount == 0 -> "clean"
            amount <= (avg * 0.7f).toInt() -> "low"
            amount <= (avg * 1.3f).toInt() -> "average"
            else -> "high"
        }
        DayMood(label, amount, level)
    }
}

private fun dayDotColor(level: String): Color = when (level) {
    "clean" -> Color(0xFF2A2A2A)
    "low" -> MoodClean
    "average" -> MoodMixed
    "high" -> MoodBad
    else -> GrimeGrey
}

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

    val hasData = insightsState.weeklyTotalSpend > 0 || insightsState.dailyTotalSpend > 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item { HeroInsightCard(insightsState = insightsState) }

        if (hasData) {
            item { SpendingPersonalityCard(insightsState = insightsState) }

            item {
                if (spendTrendValues.isNotEmpty() && trendLabels.isNotEmpty()) {
                    WeeklyTimelineSection(spendTrendValues, trendLabels, insightsState.dailyMood)
                }
            }

            if (insightsState.observations.isNotEmpty()) {
                item { SmartObservationsSection(observations = insightsState.observations) }
            }
        } else {
            item { EmptyInsightState() }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun HeroInsightCard(insightsState: InsightsUiState) {
    val mood = heroMood(insightsState.dailyMood)

    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val ornamentPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val insightMessage = when {
        insightsState.dailyInsightMessage.isNotBlank() -> insightsState.dailyInsightMessage
        insightsState.weeklyTotalSpend == 0 && insightsState.dailyTotalSpend == 0 -> "We're still learning your habits."
        else -> "No activity today."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(mood.glowStart, mood.glowEnd)
                )
            )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodOrnament(
                    mood = mood.ornamentHue,
                    accent = mood.accent,
                    pulse = ornamentPulse,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = moodText(insightsState.dailyMood),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = mood.accent.copy(alpha = 0.8f),
                        letterSpacing = 0.3.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Today's read",
                        fontSize = 11.sp,
                        color = DimWhite
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = insightMessage,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                lineHeight = 28.sp
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(mood.accent.copy(alpha = 0.04f * glowAlpha))
        )
    }
}

@Composable
private fun MoodOrnament(
    mood: String,
    accent: Color,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) / 2f

        when (mood) {
            "great" -> {
                val phase = pulse * 2f * PI.toFloat()
                for (i in 0..2) {
                    val offset = (phase + i * 2.094f) % (2f * PI.toFloat())
                    val radius = r * (0.35f + 0.5f * (0.5f + 0.5f * sin(offset)))
                    val alpha = 0.15f * (0.5f + 0.5f * sin(offset + PI.toFloat()))
                    drawCircle(accent.copy(alpha = alpha), radius, Offset(cx, cy))
                }
                drawCircle(accent.copy(alpha = 0.8f), r * 0.15f, Offset(cx, cy))
            }
            "good" -> {
                val ringAlpha = 0.12f * (0.5f + 0.5f * pulse)
                drawCircle(accent.copy(alpha = ringAlpha), r * 0.85f, Offset(cx, cy))
                drawCircle(accent.copy(alpha = 0.1f), r * 0.65f, Offset(cx, cy))
                drawCircle(accent.copy(alpha = 0.6f), r * 0.13f, Offset(cx, cy))
            }
            "clean" -> {
                val waveAlpha = 0.15f * (0.5f + 0.5f * pulse)
                drawCircle(accent.copy(alpha = waveAlpha), r * 0.55f, Offset(cx, cy))
                drawCircle(accent.copy(alpha = 0.5f), r * 0.12f, Offset(cx, cy))
            }
            "mixed" -> {
                val offset = 0.25f * r * (pulse - 0.5f)
                drawCircle(accent.copy(alpha = 0.08f), r * 0.65f, Offset(cx - offset, cy))
                drawCircle(accent.copy(alpha = 0.08f), r * 0.65f, Offset(cx + offset, cy))
                drawCircle(accent.copy(alpha = 0.5f), r * 0.12f, Offset(cx, cy))
            }
            "bad" -> {
                val s = 0.5f + 0.5f * pulse
                drawCircle(accent.copy(alpha = 0.12f * s), r * 0.8f, Offset(cx, cy))
                drawCircle(accent.copy(alpha = 0.15f * (1f - s * 0.5f)), r * 0.45f, Offset(cx, cy))
                drawCircle(accent.copy(alpha = 0.6f), r * 0.12f, Offset(cx, cy))
            }
            else -> {
                drawCircle(accent.copy(alpha = 0.5f), r * 0.15f, Offset(cx, cy))
            }
        }
    }
}

private fun moodText(mood: String): String = when (mood) {
    "great" -> "THRIVING"
    "good" -> "STEADY"
    "clean" -> "RESTING"
    "mixed" -> "UNEVEN"
    "bad" -> "ROUGH"
    else -> "QUIET"
}

@Composable
private fun SpendingPersonalityCard(insightsState: InsightsUiState) {
    val accent = vibeColor(insightsState.spendingPersonality)

    val vibeTransition = rememberInfiniteTransition(label = "vibe")
    val breathAlpha by vibeTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.04f * breathAlpha))
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f * breathAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.7f))
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Today's vibe",
                    fontSize = 11.sp,
                    color = GrimeGrey,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = insightsState.spendingPersonality.ifEmpty { "Quiet" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = insightsState.personalitySupporting.ifEmpty { "No data yet." },
                    fontSize = 13.sp,
                    color = DimWhite,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun WeeklyTimelineSection(
    values: List<Int>,
    labels: List<String>,
    todayMood: String
) {
    val days = remember(values, labels) { computeDayMoods(values, labels) }
    var expanded by remember { mutableStateOf(false) }

    val lineTransition = rememberInfiniteTransition(label = "line")
    val lineProgress by lineTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "line"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "This week",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .clickable { expanded = !expanded }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Week details" else "Tap to expand",
                        fontSize = 11.sp,
                        color = SubtextGrey,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.weight(1f))
                    val chevron = if (expanded) "▼" else "▶"
                    Text(text = chevron, fontSize = 10.sp, color = SubtextGrey)
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    val lineColor = when (todayMood) {
                        "great", "good" -> MoodGood
                        "clean" -> MoodClean
                        "mixed" -> MoodMixed
                        "bad" -> MoodBad
                        else -> GrimeGrey
                    }
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (days.size < 2) return@Canvas
                        val dotSpacing = size.width / (days.size - 1)
                        val points = days.mapIndexed { index, day ->
                            val x = dotSpacing * index
                            val y = when (day.level) {
                                "clean" -> size.height * 0.75f
                                "low" -> size.height * 0.28f
                                "average" -> size.height * 0.5f
                                "high" -> size.height * 0.18f
                                else -> size.height * 0.5f
                            }
                            Offset(x, y)
                        }
                        val fillPath = Path().apply {
                            moveTo(points.first().x, size.height)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, size.height)
                            close()
                        }
                        drawPath(
                            fillPath,
                            lineColor.copy(alpha = 0.12f * lineProgress)
                        )
                        points.zipWithNext().forEach { (from, to) ->
                            drawLine(
                                color = lineColor.copy(alpha = 0.5f * lineProgress),
                                start = from,
                                end = to,
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        days.forEach { day ->
                            val isToday = day.label == days.lastOrNull()?.label
                            val dotColor = if (isToday) {
                                when (todayMood) {
                                    "great" -> MoodGreat
                                    "good" -> MoodGood
                                    "mixed" -> MoodMixed
                                    "bad" -> MoodBad
                                    else -> dayDotColor(day.level)
                                }
                            } else {
                                dayDotColor(day.level)
                            }
                            val dotSize = if (isToday) 10.dp else 8.dp
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(dotSize)
                                        .clip(CircleShape)
                                        .background(dotColor.copy(alpha = 0.8f))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = day.label,
                                    fontSize = 10.sp,
                                    color = SubtextGrey
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        days.forEach { day ->
                            val isToday = day.label == days.lastOrNull()?.label
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(dayDotColor(day.level).copy(alpha = 0.7f))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = day.label,
                                        fontSize = 13.sp,
                                        color = if (isToday) Color.White else DimWhite,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isToday) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "today",
                                            fontSize = 9.sp,
                                            color = SubtextGrey,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = formatCurrency(day.amount),
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val highest = days.maxByOrNull { it.amount }
                        val lowest = days.filter { it.amount > 0 }.minByOrNull { it.amount }
                        Text(
                            text = buildString {
                                if (highest != null && highest.amount > 0) {
                                    append("Peak: ${highest.label} (${formatCurrency(highest.amount)})")
                                }
                                if (lowest != null && lowest.amount > 0 && lowest != highest) {
                                    append("  ·  Lowest: ${lowest.label}")
                                }
                            },
                            fontSize = 11.sp,
                            color = SubtextGrey,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartObservationsSection(observations: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Patterns",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        observations.forEach { obs ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161616))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = obs,
                    fontSize = 13.sp,
                    color = DimWhite,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun EmptyInsightState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Your spending personality is forming.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "A few more transactions will unlock deeper insights.",
            fontSize = 13.sp,
            color = GrimeGrey,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun GoalProgressCard(uiState: DashboardUiState) {
    if (uiState.savingsGoal <= 0.0 || uiState.monthlyCommitment <= 0.0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal momentum",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = formatCurrency(uiState.savingsGoal),
                    color = GrimeGrey,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { uiState.goalProgressPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = if (uiState.isGoalCompleted) VintageCream else TetherRed,
                trackColor = Color(0xFF2A2A2A)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val pctText = (uiState.goalProgressPct * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "$pctText%",
                    color = GrimeGrey,
                    fontSize = 12.sp
                )
                Text(
                    text = if (uiState.isGoalCompleted)
                        "Completed!"
                    else
                        "${formatCurrency(uiState.goalRemainingAmount)} to go",
                    color = if (uiState.isGoalCompleted) VintageCream else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
