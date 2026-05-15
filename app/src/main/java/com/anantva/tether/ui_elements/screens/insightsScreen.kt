package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

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
import com.anantva.tether.ui_elements.components.AvatarIcon
import com.anantva.tether.ui_elements.components.FinancialAuraAvatar
import com.anantva.tether.ui.theme.VintageCream
import java.time.LocalDate
import kotlin.math.sin
import kotlin.math.cos
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

private fun computeMorphFactor(mood: String, healthScore: Float, isOverLimit: Boolean, streakDays: Int): Float = when {
    streakDays == 0 && isOverLimit -> 0.95f
    mood == "great" && healthScore >= 0.8f -> 0f
    mood == "good" || mood == "clean" -> 0.1f
    mood == "mixed" -> 0.5f
    mood == "bad" || isOverLimit -> 0.85f
    else -> 0.2f
}

private fun waveformAccentColors(morph: Float): Pair<Color, Color> = when {
    morph <= 0.15f -> Color(0xFF2ECC71) to Color(0xFF1ABC9C)
    morph <= 0.5f -> Color(0xFF3498DB) to Color(0xFF2980B9)
    morph <= 0.7f -> Color(0xFFF39C12) to Color(0xFFE67E22)
    else -> Color(0xFFE74C3C) to Color(0xFFC0392B)
}

private fun financialPersonality(
    dailyMood: String, healthScore: Float, streakDays: Int,
    needWantRatio: Float, wantSpend: Int, needSpend: Int,
    discretionarySpend: Int, totalSpend: Int, isOverLimit: Boolean
): String = when {
    isOverLimit && wantSpend > needSpend -> if (streakDays > 0) "Controlled Chaos" else "Impulse Goblin"
    isOverLimit -> "Survival Mode"
    totalSpend == 0 && streakDays >= 14 -> "Disciplined"
    totalSpend == 0 && streakDays >= 7 -> "Locked In"
    totalSpend == 0 -> "Resting"
    streakDays >= 30 && healthScore >= 0.8f -> "Disciplined"
    streakDays >= 14 && healthScore >= 0.7f -> "Stable Builder"
    healthScore >= 0.8f -> "Thriving"
    healthScore >= 0.6f -> "Steady"
    needWantRatio >= 3f -> "Stable Builder"
    needWantRatio >= 1.5f -> "Balanced"
    wantSpend > needSpend * 2 -> "Dopamine Spending"
    discretionarySpend > totalSpend * 0.6f -> "Coasting"
    else -> "Coasting"
}

private fun financialPersonalitySubtext(
    dailyMood: String, healthScore: Float, streakDays: Int,
    needWantRatio: Float, totalSpend: Int, isOverLimit: Boolean,
    insight: String
): String = when {
    isOverLimit && streakDays > 0 -> "Your streak is under pressure."
    isOverLimit -> "Too many impulse hits today."
    totalSpend == 0 && streakDays >= 7 -> "You stayed in control."
    totalSpend == 0 && streakDays > 0 -> "Streak intact. Clean day."
    totalSpend == 0 -> "No activity today."
    healthScore >= 0.8f || needWantRatio >= 3f -> "Mostly fixed expenses today."
    needWantRatio >= 1.5f -> "Your spending stayed balanced."
    streakDays >= 14 -> "Your streak is building momentum."
    else -> insight.takeIf { it.isNotBlank() } ?: "Keep going."
}

private data class FinancialEmotionState(
    val title: String,
    val subtitle: String,
    val stressLevel: Float,
    val waveformSharpness: Float,
    val motionIntensity: Float,
    val chaosLevel: Float,
    val glowStrength: Float
)

private fun computeEmotion(
    usagePercent: Float,
    wantsRatio: Float,
    streakDays: Int,
    isOverLimit: Boolean
): FinancialEmotionState {
    val u = usagePercent.coerceIn(0f, 1.2f)
    val w = wantsRatio.coerceIn(0f, 1f)

    return when {
        isOverLimit || u > 1f -> FinancialEmotionState(
            title = "Chaotic", subtitle = "You've pushed past today's limit.",
            stressLevel = 1.2f, waveformSharpness = 1f, motionIntensity = 2.5f,
            chaosLevel = 1f, glowStrength = 1.5f
        )
        u > 0.9f -> FinancialEmotionState(
            title = "Restless", subtitle = "Your spending energy is spiking.",
            stressLevel = 1f, waveformSharpness = 0.85f, motionIntensity = 2f,
            chaosLevel = 0.7f, glowStrength = 1.4f
        )
        u > 0.75f -> FinancialEmotionState(
            title = "Tense", subtitle = "Approaching today's edge.",
            stressLevel = 0.8f, waveformSharpness = 0.6f, motionIntensity = 1.6f,
            chaosLevel = 0.4f, glowStrength = 1.2f
        )
        u > 0.55f -> FinancialEmotionState(
            title = "Active", subtitle = "Spending picked up today.",
            stressLevel = 0.5f, waveformSharpness = 0.3f, motionIntensity = 1.3f,
            chaosLevel = 0.2f, glowStrength = 1.1f
        )
        u > 0.35f -> FinancialEmotionState(
            title = "Balanced", subtitle = "Healthy movement today.",
            stressLevel = 0.3f, waveformSharpness = 0.1f, motionIntensity = 1.1f,
            chaosLevel = 0.1f, glowStrength = 1f
        )
        u > 0.15f -> FinancialEmotionState(
            title = "Focused", subtitle = "Controlled spending rhythm.",
            stressLevel = 0.15f, waveformSharpness = 0f, motionIntensity = 1f,
            chaosLevel = 0f, glowStrength = 0.9f
        )
        else -> FinancialEmotionState(
            title = "Calm", subtitle = "Easy pace. Plenty of room today.",
            stressLevel = 0f, waveformSharpness = 0f, motionIntensity = 0.8f,
            chaosLevel = 0f, glowStrength = 0.8f
        )
    }
}

@Composable
fun InsightsScreen(
    innerPadding: PaddingValues,
    insightsState: InsightsUiState,
    spendTrendValues: List<Int>,
    trendLabels: List<String>,
    uiState: DashboardUiState,
    onRefresh: () -> Unit = {},
    avatarId: String = "pulse"
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

        item { HeroInsightCard(insightsState = insightsState, streakDays = uiState.streakDays) }

        if (hasData) {
            val usagePercent = if (uiState.dailyLimit > 0) {
                uiState.dailySpent.toFloat() / uiState.dailyLimit
            } else 0f
            item { SpendingPersonalityCard(
                insightsState = insightsState,
                streakDays = uiState.streakDays,
                avatarId = avatarId,
                usagePercent = usagePercent
            ) }

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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun triangleWave(t: Float): Float {
    val n = (t % (2f * PI.toFloat())) / (2f * PI.toFloat())
    return if (n < 0.5f) 4f * n - 1f else 3f - 4f * n
}

private fun sawWave(t: Float): Float {
    val n = (t % (2f * PI.toFloat())) / (2f * PI.toFloat())
    return 2f * n - 1f
}

private fun squareWave(t: Float): Float {
    val n = (t % (2f * PI.toFloat())) / (2f * PI.toFloat())
    return if (n < 0.5f) 1f else -1f
}

private fun morphWaveform(t: Float, morph: Float): Float {
    val sinW = sin(t)
    val triW = triangleWave(t)
    val sawW = sawWave(t)
    val sqrW = squareWave(t)
    return when {
        morph <= 0.25f -> lerp(sinW, triW, morph / 0.25f)
        morph <= 0.5f -> lerp(triW, sawW, (morph - 0.25f) / 0.25f)
        morph <= 0.75f -> lerp(sawW, sqrW, (morph - 0.5f) / 0.25f)
        else -> {
            val blend = (morph - 0.75f) / 0.25f
            lerp(sqrW, sqrW * 1.5f, blend)
        }
    }
}

private fun seamlessGlow(freq: Float, phase: Float): Float =
    0.55f + 0.15f * sin(phase * freq * 2f * PI.toFloat())

@Composable
private fun rememberContinuousPhase(speed: Float): Float {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            phase.floatValue += 0.016f * speed
        }
    }
    return phase.floatValue
}

@Composable
private fun HeroInsightCard(insightsState: InsightsUiState, streakDays: Int = 0) {
    val profileMorph = insightsState.personalityWaveformSharpness
    val profileSpeed = insightsState.personalityWaveformSpeed
    val morph = profileMorph
    val (primaryColor, _) = waveformAccentColors(morph)

    val phase = rememberContinuousPhase(profileSpeed)
    val glowAlpha = seamlessGlow(0.42f, phase)

    val personalityTitle = insightsState.personalityTitle
    val personalityDesc = insightsState.personalityDescription

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.08f),
                        Color(0xFF0F0F0F)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val waveColor = primaryColor
            val cy = size.height / 2f
            val morphClamped = morph.coerceIn(0f, 1f)
            val wavePhase = phase

            for (layer in 0 until 5) {
                val layerAlpha = (0.05f - layer * 0.008f).coerceAtLeast(0.01f)
                val ampBase = size.height * (0.08f + layer * 0.04f)
                val speedBase = 1f - layer * 0.1f
                val phaseOffset = layer * 1.8f
                val layerMorph = ((morphClamped + layer * 0.08f).coerceIn(0f, 1f))

                val f1 = 0.8f + layer * 0.2f
                val f2 = 1.4f + layer * 0.3f

                val path = Path()
                var first = true
                var x = 0f
                while (x <= size.width) {
                    val t1 = x / size.width * 2f * PI.toFloat() * f1 + wavePhase * speedBase + phaseOffset
                    val t2 = x / size.width * 2f * PI.toFloat() * f2 + wavePhase * speedBase * 0.6f - phaseOffset

                    val wave1 = morphWaveform(t1, layerMorph)
                    val wave2 = morphWaveform(t2, morphClamped) * 0.3f
                    val y = cy + ampBase * (wave1 * 0.65f + wave2 * 0.35f)

                    if (first) { path.moveTo(x, y); first = false }
                    else path.lineTo(x, y)
                    x += 1f
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }

                drawPath(
                    fillPath,
                    waveColor.copy(alpha = layerAlpha * 0.3f * (0.4f + 0.6f * glowAlpha))
                )

                drawPath(
                    path,
                    waveColor.copy(alpha = layerAlpha * (0.4f + 0.6f * glowAlpha)),
                    style = Stroke(width = (1.5f - layer * 0.2f).dp.toPx())
                )
            }
        }

        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodOrnament(
                    mood = insightsState.dailyMood,
                    accent = primaryColor,
                    pulse = glowAlpha,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Your financial personality",
                        fontSize = 11.sp,
                        color = DimWhite,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = personalityTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = personalityDesc,
                fontSize = 14.sp,
                color = DimWhite,
                lineHeight = 19.sp
            )
        }
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
private fun SpendingPersonalityCard(
    insightsState: InsightsUiState,
    streakDays: Int = 0,
    avatarId: String = "pulse",
    usagePercent: Float = 0f
) {
    val wantsTotal = (insightsState.dailyWantSpend + insightsState.dailyNeedSpend).coerceAtLeast(1)
    val wantsRatio = insightsState.dailyWantSpend.toFloat() / wantsTotal
    val emotion = computeEmotion(usagePercent, wantsRatio, streakDays, insightsState.isOverLimit)
    var expanded by remember { mutableStateOf(false) }

    val stressAccent = when {
        emotion.stressLevel > 1f -> Color(0xFFE74C3C)
        emotion.stressLevel > 0.8f -> Color(0xFFFF6B35)
        emotion.stressLevel > 0.5f -> Color(0xFFFFB74D)
        emotion.stressLevel > 0.2f -> Color(0xFF4FC3F7)
        else -> Color(0xFF2ECC71)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardBg)
            .clickable { expanded = !expanded }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FinancialAuraAvatar(
                    avatarId = avatarId,
                    size = 80.dp,
                    usagePercent = usagePercent
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Today's vibe",
                        fontSize = 11.sp,
                        color = GrimeGrey,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = emotion.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = emotion.subtitle,
                        fontSize = 13.sp,
                        color = DimWhite,
                        lineHeight = 17.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                    expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(animationSpec = tween(200)) +
                    shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spending breakdown",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = formatCurrency(insightsState.dailyTotalSpend),
                            fontSize = 11.sp,
                            color = GrimeGrey
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    insightsState.dailyCategoryBreakdown.take(6).forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(categoryColor(cat.category).copy(alpha = 0.07f))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor(cat.category))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = cat.category,
                                fontSize = 13.sp,
                                color = DimWhite,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatCurrency(cat.total),
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            stressAccent.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

private fun categoryColor(name: String): Color = when (name) {
    "Food & Dining" -> Color(0xFFFF6B35)
    "Transport" -> Color(0xFF4FC3F7)
    "Shopping" -> Color(0xFFFFB74D)
    "Entertainment" -> Color(0xFFCE93D8)
    "Bills & Utilities" -> Color(0xFF81C784)
    else -> GrimeGrey
}

@Composable
private fun WeeklyTimelineSection(
    values: List<Int>,
    labels: List<String>,
    todayMood: String
) {
    val days = remember(values, labels) { computeDayMoods(values, labels) }
    val todayLabel = remember { LocalDate.now().dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() } }
    var expanded by remember { mutableStateOf(false) }

    val linePhase = rememberContinuousPhase(1.1f)
    val lineProgress = 0.6f + 0.4f * sin(linePhase * 2f * PI.toFloat())

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
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
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
                            val isToday = day.label == todayLabel
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
                    enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                        expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
                    exit = fadeOut(animationSpec = tween(150)) +
                        shrinkVertically(animationSpec = tween(150))
                ) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        days.forEach { day ->
                            val isToday = day.label == todayLabel
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
