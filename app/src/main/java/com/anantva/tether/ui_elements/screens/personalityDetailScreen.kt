package com.anantva.tether.ui_elements.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.insights.MonthlyTrend
import com.anantva.tether.insights.PersonalityAnalytics
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.VintageCream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

private val CardBg = Color(0xFF1A1A1A)
private val DimWhite = Color.White.copy(alpha = 0.7f)
private val SubtextGrey = Color(0xFF6F6F6F)
private val GrimeGrey = Color(0xFFA0A0A0)

@Composable
fun PersonalityDetailScreen(
    onBack: () -> Unit,
    viewModel: PersonalityDetailViewModel = hiltViewModel()
) {
    val analytics by viewModel.uiState.collectAsState()
    val ready = analytics.isReady

    BackHandler(onBack = onBack)

    var headerVisible by remember { mutableStateOf(false) }
    var identityVisible by remember { mutableStateOf(false) }
    var behaviorVisible by remember { mutableStateOf(false) }
    var emotionalVisible by remember { mutableStateOf(false) }
    var trendsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (ready) {
            headerVisible = true
            kotlinx.coroutines.delay(200)
            identityVisible = true
            kotlinx.coroutines.delay(120)
            behaviorVisible = true
            kotlinx.coroutines.delay(120)
            emotionalVisible = true
            kotlinx.coroutines.delay(120)
            trendsVisible = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "detailGradient")
    val hueShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(12000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "hueShift"
    )

    val orbPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeroSection(
                analytics = analytics,
                hueShift = hueShift,
                orbPhase = orbPhase,
                visible = headerVisible,
                onBack = onBack
            )

            Spacer(Modifier.height(28.dp))

            if (ready) {
                StaggerSection(visible = identityVisible, delay = 0) {
                    CoreIdentityCard(analytics)
                }

                Spacer(Modifier.height(16.dp))

                StaggerSection(visible = behaviorVisible, delay = 0) {
                    BehaviorPatternsCard(analytics)
                }

                Spacer(Modifier.height(16.dp))

                StaggerSection(visible = emotionalVisible, delay = 0) {
                    EmotionalSpendingCard(analytics)
                }

                Spacer(Modifier.height(16.dp))

                StaggerSection(visible = trendsVisible, delay = 0) {
                    LongTermTrendsCard(analytics)
                }
            } else {
                LoadingState()
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun HeroSection(
    analytics: PersonalityAnalytics,
    hueShift: Float,
    orbPhase: Float,
    visible: Boolean,
    onBack: () -> Unit
) {
    val hue = hueShift / 360f
    val col1 = Color.hsv(hue * 360f, 0.5f, 0.15f)
    val col2 = Color.hsv((hue * 360f + 60f) % 360f, 0.4f, 0.08f)

    val pulse = (sin(orbPhase * 2f) + 1f) / 2f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(600)) +
            slideInVertically(animationSpec = tween(600), initialOffsetY = { -it / 3 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(col1, col2, Color(0xFF0D0D0D))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 60.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2190",
                    color = DimWhite,
                    fontSize = 20.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp)
                    .padding(horizontal = 28.dp)
            ) {
                Text(
                    text = "Financial Personality",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GrimeGrey,
                    letterSpacing = 1.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = analytics.personalityTitle.ifBlank { "Reading your rhythm\u2026" },
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 40.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = analytics.personalityDescription.ifBlank { "Analyzing your financial patterns..." },
                    fontSize = 15.sp,
                    color = DimWhite,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(vibeColor(analytics.personalityTitle))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Top: ${analytics.strongestCategories.firstOrNull() ?: analytics.categoryDistribution.firstOrNull()?.category ?: "—"}",
                            fontSize = 13.sp,
                            color = DimWhite
                        )
                    }
                    Text(
                        text = "${analytics.totalTransactions} txns",
                        fontSize = 12.sp,
                        color = SubtextGrey
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = "Explore your patterns",
                    color = VintageCream,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(VintageCream.copy(alpha = 0.08f))
                        .clickable { /* scroll hint - content below */ }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            PersonalityOrb(
                phase = orbPhase,
                pulse = pulse,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 50.dp, end = 24.dp)
                    .size(100.dp)
            )
        }
    }
}

@Composable
private fun PersonalityOrb(
    phase: Float,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.8f

        val waveR = r + sin(phase * 1.5f) * r * 0.04f + pulse * r * 0.02f
        val glowR = waveR + 12.dp.toPx() + pulse * 6.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VintageCream.copy(alpha = 0.12f + pulse * 0.06f),
                    VintageCream.copy(alpha = 0.03f),
                    Color.Transparent
                )
            ),
            radius = glowR,
            center = Offset(cx, cy)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VintageCream.copy(alpha = 0.25f + pulse * 0.08f),
                    VintageCream.copy(alpha = 0.08f),
                    Color.Transparent
                )
            ),
            radius = waveR + 4.dp.toPx(),
            center = Offset(cx, cy)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VintageCream.copy(alpha = 0.6f + pulse * 0.15f),
                    VintageCream.copy(alpha = 0.15f)
                )
            ),
            radius = waveR,
            center = Offset(cx + sin(phase) * r * 0.05f, cy + cos(phase * 0.7f) * r * 0.05f)
        )

        drawCircle(
            color = VintageCream.copy(alpha = 0.9f),
            radius = waveR * 0.55f,
            center = Offset(cx, cy)
        )

        val iAngle = phase * 2f
        val ix = cx + cos(iAngle) * waveR * 0.3f
        val iy = cy + sin(iAngle) * waveR * 0.3f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.White.copy(alpha = 0.02f),
                    Color.Transparent
                )
            ),
            radius = waveR * 0.3f,
            center = Offset(ix, iy)
        )
    }
}

@Composable
private fun StaggerSection(
    visible: Boolean,
    delay: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                initialOffsetY = { it / 4 }
            )
    ) {
        content()
    }
}

@Composable
private fun CoreIdentityCard(analytics: PersonalityAnalytics) {
    CollapsibleCard(title = "Core Identity", subtitle = "Your financial baseline", defaultExpanded = true) {
        Spacer(Modifier.height(8.dp))

        val consistencyColor = when {
            analytics.consistencyScore >= 0.7f -> Color(0xFF7FAE65)
            analytics.consistencyScore >= 0.4f -> Color(0xFFFFB74D)
            else -> Color(0xFFE53935)
        }

        MetricDetail(label = "Consistency Score", value = "${(analytics.consistencyScore * 100).toInt()}%")
        Spacer(Modifier.height(6.dp))
        SimpleProgressBar(
            progress = { analytics.consistencyScore.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = consistencyColor,
            trackColor = Color(0xFF2A2A2A)
        )

        Spacer(Modifier.height(16.dp))

        val topCat = analytics.strongestCategories.firstOrNull() ?: analytics.categoryDistribution.firstOrNull()?.category ?: "—"
        MetricDetail(label = "Top Category", value = topCat)
        Spacer(Modifier.height(10.dp))
        MetricDetail(label = "Total Spend", value = formatCurrency(analytics.totalSpend))
        Spacer(Modifier.height(10.dp))
        MetricDetail(label = "Avg Transaction", value = formatCurrency(analytics.averageTransactionSize))
        Spacer(Modifier.height(10.dp))
        MetricDetail(label = "Wants / Needs", value = "${(analytics.wantsRatio * 100).toInt()}% / ${(analytics.needsRatio * 100).toInt()}%")
    }
}

@Composable
private fun BehaviorPatternsCard(analytics: PersonalityAnalytics) {
    CollapsibleCard(title = "Behavior Patterns", subtitle = "How you spend") {
        Spacer(Modifier.height(8.dp))

        if (analytics.dominantTraits.isNotEmpty()) {
            Text(
                text = "Dominant Traits",
                fontSize = 12.sp,
                color = SubtextGrey,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            analytics.dominantTraits.forEach { trait ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(TetherRed.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(trait, fontSize = 13.sp, color = DimWhite)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Adaptive", fontSize = 12.sp, color = Color(0xFF7FAE65), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("${(analytics.adaptiveScore * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(50.dp)
                    .background(Color(0xFF2A2A2A))
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Impulsive", fontSize = 12.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("${(analytics.impulsiveScore * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EmotionalSpendingCard(analytics: PersonalityAnalytics) {
    CollapsibleCard(title = "Emotional Spending", subtitle = "Feelings & finances") {
        Spacer(Modifier.height(8.dp))
        EmotionRow("Emotional Score", analytics.emotionalSpendingScore)
        Spacer(Modifier.height(12.dp))
        EmotionRow("Late-Night", analytics.lateNightTransactionRatio)
        Spacer(Modifier.height(12.dp))
        EmotionRow("Weekend Spend", analytics.weekendSpendRatio)
    }
}

@Composable
private fun EmotionRow(label: String, score: Float) {
    val color = if (score <= 0.3f) Color(0xFF7FAE65) else if (score <= 0.6f) Color(0xFFFFB74D) else Color(0xFFE53935)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = DimWhite)
            Text("${(score * 100).toInt()}%", fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        SimpleProgressBar(
            progress = { score.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = Color(0xFF2A2A2A)
        )
    }
}

@Composable
private fun LongTermTrendsCard(analytics: PersonalityAnalytics) {
    CollapsibleCard(title = "Long-term Trends", subtitle = "Last 12 months") {
        Spacer(Modifier.height(8.dp))
        analytics.monthlyTrends.takeLast(6).forEach { trend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(trend.month, fontSize = 13.sp, color = DimWhite)
                    Text(trend.dominantCategory, fontSize = 10.sp, color = SubtextGrey)
                }
                Text(formatCurrency(trend.totalSpend), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    subtitle: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .clickable { expanded = !expanded }
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(subtitle, fontSize = 11.sp, color = SubtextGrey)
                }
                Text(
                    text = if (expanded) "\u25BC" else "\u25B6",
                    fontSize = 12.sp,
                    color = SubtextGrey
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)) +
                    expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f)),
                exit = fadeOut(animationSpec = tween(200)) +
                    shrinkVertically(animationSpec = tween(200))
            ) {
                Column { content() }
            }
        }
    }
}

@Composable
private fun MetricDetail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = SubtextGrey)
        Text(value, fontSize = 13.sp, color = DimWhite, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Analyzing your patterns\u2026",
            color = SubtextGrey,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SimpleProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = VintageCream,
    trackColor: Color = Color(0xFF2A2A2A)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = progress().coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

private fun vibeColor(personality: String): Color = when (personality) {
    "Elite", "Consistent", "Controlled", "Balanced" -> Color(0xFF4FC3F7)
    "Clean", "Steady" -> Color(0xFF7FAE65)
    "Impulsive", "Reactive", "Chaotic" -> Color(0xFFE53935)
    "Survival mode", "Rebounding" -> Color(0xFFFFB74D)
    else -> GrimeGrey
}
