package com.anantva.tether.ui_elements.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// ──────────────────────────────────────────
// TIER SYSTEM
// ──────────────────────────────────────────

enum class OrbTier(val label: String) {
    BRONZE("I"), SILVER("II"), GOLD("III"), PURPLE("IV"),
    DEEP_GOLD("V"), ORANGE("VI"), RED("VII");

    companion object {
        fun fromStreak(days: Int): OrbTier = when {
            days < 7    -> BRONZE
            days < 21   -> SILVER
            days < 60   -> GOLD
            days < 120  -> PURPLE
            days < 250  -> DEEP_GOLD
            days < 365  -> ORANGE
            else        -> RED
        }
    }
}

enum class OrbMomentumState { ALIVE, COLLAPSING, DEAD, RECOVERING }

enum class OrbEmotionalState { CALM, ACTIVE, TENSE, PANIC }

data class OrbVitals(
    val stressLevel: Float,
    val bpm: Float,
    val emotionalState: OrbEmotionalState,
    val panicIntensity: Float
)

data class TierColors(
    val light1: Color, val light2: Color, val deep1: Color, val deep2: Color
)

private val tierColorMap: Map<OrbTier, TierColors> = mapOf(
    OrbTier.BRONZE to TierColors(
        light1 = Color(0xFFFFB36B), light2 = Color(0xFFFF8C42),
        deep1  = Color(0xFFE05A2A), deep2  = Color(0xFFB63A16)
    ),
    OrbTier.SILVER to TierColors(
        light1 = Color(0xFFDCE6F2), light2 = Color(0xFFB8C7D9),
        deep1  = Color(0xFF7E8FA6), deep2  = Color(0xFF58677D)
    ),
    OrbTier.GOLD to TierColors(
        light1 = Color(0xFFFFE27A), light2 = Color(0xFFFFC93D),
        deep1  = Color(0xFFF5A300), deep2  = Color(0xFFD97B00)
    ),
    OrbTier.PURPLE to TierColors(
        light1 = Color(0xFFC084FF), light2 = Color(0xFF9B5CFF),
        deep1  = Color(0xFF6B2DFF), deep2  = Color(0xFF4A00E0)
    ),
    OrbTier.DEEP_GOLD to TierColors(
        light1 = Color(0xFFFFD95A), light2 = Color(0xFFFFB800),
        deep1  = Color(0xFFFF8A00), deep2  = Color(0xFFD96A00)
    ),
    OrbTier.ORANGE to TierColors(
        light1 = Color(0xFFFF9A4D), light2 = Color(0xFFFF6B1A),
        deep1  = Color(0xFFE63E00), deep2  = Color(0xFFB82500)
    ),
    OrbTier.RED to TierColors(
        light1 = Color(0xFFFF6B6B), light2 = Color(0xFFFF3B3B),
        deep1  = Color(0xFFD10000), deep2  = Color(0xFF7A0000)
    )
)

private val dangerLight1 = Color(0xFFFF6B6B)
private val dangerLight2 = Color(0xFFFF3B3B)
private val dangerDeep1  = Color(0xFFD10000)
private val dangerDeep2  = Color(0xFF7A0000)

private val PI_F = PI.toFloat()

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red   = a.red   + (b.red   - a.red)   * t,
    green = a.green + (b.green - a.green) * t,
    blue  = a.blue  + (b.blue  - a.blue)  * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

// ──────────────────────────────────────────
// HEART RATE MODEL
// ──────────────────────────────────────────

// ──────────────────────────────────────────
// HEART RATE MODEL
// ──────────────────────────────────────────

fun tetherOrbVitals(stressLevel: Float): OrbVitals {
    val stress = stressLevel.coerceIn(0f, 1f)
    val bpm = spendingRatioToBPM(stress)
    val panic = ((stress - 0.90f) / 0.10f).coerceIn(0f, 1f)
    val state = when {
        stress >= 0.90f -> OrbEmotionalState.PANIC
        stress >= 0.70f -> OrbEmotionalState.TENSE
        stress >= 0.30f -> OrbEmotionalState.ACTIVE
        else -> OrbEmotionalState.CALM
    }
    return OrbVitals(
        stressLevel = stress,
        bpm = bpm,
        emotionalState = state,
        panicIntensity = panic
    )
}

private fun spendingRatioToBPM(ratio: Float): Float {
    val r = ratio.coerceIn(0f, 1f)
    return when {
        r <= 0f    -> 60f
        r < 0.25f  -> 60f + r / 0.25f * 30f
        r < 0.50f  -> 90f + (r - 0.25f) / 0.25f * 35f
        r < 0.75f  -> 125f + (r - 0.50f) / 0.25f * 30f
        else       -> 155f + (r - 0.75f) / 0.25f * 25f
    }
}

// ──────────────────────────────────────────
// MAIN COMPOSABLE
// ──────────────────────────────────────────

@Composable
fun TetherOrb(
    stressLevel: Float,
    streakDays: Int,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    momentumState: OrbMomentumState = OrbMomentumState.ALIVE,
    onCollapseComplete: () -> Unit = {},
    showText: Boolean = true
) {
    val vitals = remember(stressLevel) { tetherOrbVitals(stressLevel) }
    val s = vitals.stressLevel
    val tier = remember(streakDays) { OrbTier.fromStreak(streakDays) }
    val tc = tierColorMap[tier] ?: tierColorMap[OrbTier.BRONZE]!!

    // ── COLOR EVOLUTION — lerp tier → danger red by spending ratio ──
    val dangerFactor = s.coerceIn(0f, 1f)
    val finalTC = remember(tc, dangerFactor) {
        TierColors(
            light1 = lerpColor(tc.light1, dangerLight1, dangerFactor),
            light2 = lerpColor(tc.light2, dangerLight2, dangerFactor),
            deep1  = lerpColor(tc.deep1,  dangerDeep1,  dangerFactor),
            deep2  = lerpColor(tc.deep2,  dangerDeep2,  dangerFactor)
        )
    }

    // ── HEART RATE ──
    val bpm = vitals.bpm
    val pulseMs = (60000f / bpm).toInt().coerceIn(150, 2000)

    val trans = rememberInfiniteTransition(label = "orb")

    val rawPhase by trans.animateFloat(
        initialValue = 0f, targetValue = 2f * PI_F,
        animationSpec = infiniteRepeatable(tween(pulseMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    val beatPhase = (rawPhase / (2f * PI_F)) % 1f

    // ── HEARTBEAT CURVE ──
    val contractDepth = 0.15f + s * 0.25f
    val systoleEnd = 0.08f
    val relaxEnd = 0.30f

    val heartbeatScale = when {
        beatPhase < systoleEnd -> {
            val t = beatPhase / systoleEnd
            1f - t * contractDepth
        }
        beatPhase < relaxEnd -> {
            val t = (beatPhase - systoleEnd) / (relaxEnd - systoleEnd)
            1f - contractDepth + t * contractDepth * 0.85f
        }
        else -> {
            val t = (beatPhase - relaxEnd) / (1f - relaxEnd)
            1f - contractDepth * 0.15f + sin(t * PI_F * 0.5f) * contractDepth * 0.15f
        }
    }

    // ── BREATH OVERLAY (12 breaths/min) ──
    val breathMs = 5000
    val breathPhase = rawPhase * (pulseMs.toFloat() / breathMs)
    val breathMod = 1f + sin(breathPhase * 2f * PI_F) * 0.03f

    val orbScale = heartbeatScale * breathMod

    // ── GLOW ──
    val glowBase = 0.15f + s * 0.40f
    val glowFlash = if (beatPhase < 0.15f) {
        sin(beatPhase / 0.15f * PI_F * 0.5f) * 0.50f
    } else 0f
    val glowAlpha = (glowBase + glowFlash).coerceIn(0f, 1f)

    // ── MEMBRANE TENSION ──
    val tensionBase = 0.02f + s * 0.06f
    val tensionBeat = if (beatPhase < 0.20f) {
        sin(beatPhase / 0.20f * PI_F) * 0.04f
    } else 0f
    val membraneTension = tensionBase + tensionBeat

    // ── DRIFT — Lissajous pattern ──
    val driftIntensity = s * 2f + s * s * 3f
    val driftX = sin(rawPhase * 0.30f) * driftIntensity
    val driftY = sin(rawPhase * 0.42f + 1.7f) * driftIntensity * 0.60f

    // ── PANIC LAYER (≥90% stress) ──
    val panicIntensity = vitals.panicIntensity

    val panicHb by trans.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
        label = "panic_hb"
    )
    val panicSpasm by trans.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(75, easing = LinearEasing), RepeatMode.Reverse),
        label = "panic_spasm"
    )
    val panicFlicker by trans.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(105, easing = LinearEasing), RepeatMode.Reverse),
        label = "panic_flicker"
    )
    val panicSecondHb by trans.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(190, easing = LinearEasing), RepeatMode.Reverse),
        label = "panic_hb2"
    )

    val hb  = sin(panicHb * PI_F * 0.5f) * panicIntensity
    val hb2 = sin(panicSecondHb * PI_F * 0.5f) * panicIntensity
    val spasm = sin(panicSpasm * PI_F * 0.5f) * panicIntensity
    val flicker = sin(panicFlicker * PI_F * 0.5f) * panicIntensity

    val panicBreathJitter = orbScale + hb * 0.04f + hb2 * 0.025f
    val panicGlow = (glowAlpha + flicker * 0.28f).coerceIn(0f, 1f)
    val panicTension = (membraneTension + spasm * 0.055f).coerceAtLeast(0.01f)

    val aScale    = if (panicIntensity > 0f) panicBreathJitter else orbScale
    val aGlow     = if (panicIntensity > 0f) panicGlow else glowAlpha
    val aTension  = if (panicIntensity > 0f) panicTension else membraneTension

    // ──────────────────────────────────────────────
    // STATE MACHINE — updateTransition + Animatable
    // ──────────────────────────────────────────────

    val transition = updateTransition(momentumState, label = "orb_momentum")

    // Orb visual scale (1 = full, 0 = fully collapsed)
    val orbVisualScale by transition.animateFloat(
        transitionSpec = {
            when {
                targetState == OrbMomentumState.COLLAPSING ->
                    tween(800, easing = FastOutLinearInEasing)
                targetState == OrbMomentumState.RECOVERING ->
                    tween(2200, easing = FastOutSlowInEasing)
                else -> snap()
            }
        },
        label = "orb_visual_scale"
    ) { state ->
        when (state) {
            OrbMomentumState.ALIVE -> 1f
            OrbMomentumState.COLLAPSING -> 0f
            OrbMomentumState.DEAD -> 0f
            OrbMomentumState.RECOVERING -> 1f
        }
    }

    // Internal collapse progress for multi-phase feel
    val collapseProgress = remember { Animatable(0f) }

    LaunchedEffect(momentumState) {
        if (momentumState == OrbMomentumState.COLLAPSING) {
            collapseProgress.snapTo(0f)
            collapseProgress.animateTo(1f, tween(800, easing = FastOutLinearInEasing))
            onCollapseComplete()
        }
        if (momentumState == OrbMomentumState.RECOVERING) {
            collapseProgress.snapTo(0f)
            collapseProgress.animateTo(1f, tween(2200, easing = FastOutSlowInEasing))
        }
    }

    val cp = collapseProgress.value

    val isDead = momentumState == OrbMomentumState.DEAD

    // Empty anchor residue alpha — visible only in DEAD and early RECOVERING
    val residueAlpha by transition.animateFloat(
        transitionSpec = {
            when {
                targetState == OrbMomentumState.DEAD -> tween(400, easing = FastOutSlowInEasing)
                targetState == OrbMomentumState.RECOVERING -> tween(600, easing = FastOutSlowInEasing)
                else -> snap()
            }
        },
        label = "residue_alpha"
    ) { state ->
        when (state) {
            OrbMomentumState.ALIVE -> 0f
            OrbMomentumState.COLLAPSING -> 0f
            OrbMomentumState.DEAD -> 1f
            OrbMomentumState.RECOVERING -> 0f
        }
    }

    // Recovery multiplier
    val recoveryActive = momentumState == OrbMomentumState.RECOVERING
    val recoveryMul = if (recoveryActive) {
        when {
            cp < 0.20f -> (cp / 0.20f) * 0.10f
            cp < 0.50f -> 0.10f + ((cp - 0.20f) / 0.30f) * 0.40f
            cp < 0.75f -> 0.50f + ((cp - 0.50f) / 0.25f) * 0.30f
            cp < 0.90f -> 0.80f + ((cp - 0.75f) / 0.15f) * 0.15f
            else       -> 0.95f + ((cp - 0.90f) / 0.10f) * 0.05f
        }
    } else 1f

    // Collapse breath multiplier — simple single-phase collapse inward
    val collapseBreathMul = if (momentumState == OrbMomentumState.COLLAPSING) {
        when {
            cp < 0.4f -> {
                val pp = cp / 0.4f
                (1f + sin(pp * PI_F * 6f) * 0.2f * (1f - pp * 0.5f)).coerceAtLeast(0.3f)
            }
            cp < 0.7f -> {
                val pp = (cp - 0.4f) / 0.3f
                0.3f - pp * 0.2f
            }
            else -> {
                val pp = (cp - 0.7f) / 0.3f
                0.1f * (1f - pp)
            }
        }
    } else 1f

    val collapseGlowMul = if (momentumState == OrbMomentumState.COLLAPSING) {
        when {
            cp < 0.4f -> 1f + sin(cp / 0.4f * PI_F * 5f) * 0.5f
            cp < 0.6f -> 2f - ((cp - 0.4f) / 0.2f) * 1.9f
            else -> 0.05f * (1f - (cp - 0.6f) / 0.4f)
        }
    } else 1f

    val effScale   = orbVisualScale * aScale * collapseBreathMul * recoveryMul
    val effGlow    = (aGlow * collapseGlowMul * recoveryMul).coerceIn(0f, 1f)
    val effTension = (aTension * recoveryMul).coerceAtLeast(0.001f)

    val showContent = (momentumState == OrbMomentumState.ALIVE) || recoveryActive
    val contentAlpha = if (showContent) 1f else 0f

    val anchorColor = Color(0xFF1A0000)

    // ── RENDER ──
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // ── EMPTY ANCHOR (residue) ──
        if (residueAlpha > 0.001f) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.4f)
                    .alpha(residueAlpha * 0.12f)
            ) {
                val px = size.toPx()
                val cx = px / 2f
                val cy = px / 2f
                val br = px / 2f * 0.85f

                drawCircle(
                    color = anchorColor.copy(alpha = 0.5f),
                    radius = br * 0.85f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.8.dp.toPx())
                )
                drawCircle(
                    color = anchorColor.copy(alpha = 0.3f),
                    radius = br * 0.55f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 0.4.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            anchorColor.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = br * 0.9f
                    ),
                    radius = br * 0.9f,
                    center = Offset(cx, cy)
                )
            }
        }

        // ── AURA ──
        Box(
            modifier = Modifier
                .size(size * 1.3f)
                .graphicsLayer {
                    alpha = effGlow * 0.15f
                    scaleX = orbVisualScale * 1.2f
                    scaleY = orbVisualScale * 1.2f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            finalTC.deep1.copy(alpha = effGlow * 0.25f),
                            finalTC.light2.copy(alpha = effGlow * 0.08f),
                            Color.Transparent
                        ),
                    )
                )
        )

        // ── MEMBRANE ──
        if (effScale > 0.001f) {
            Canvas(
                modifier = Modifier
                    .size(size * 1.4f)
                    .scale(effScale)
                    .offset(
                        x = driftX.dp * (1f + s * 0.5f * panicIntensity),
                        y = driftY.dp * (1f + s * 0.5f * panicIntensity)
                    )
            ) {
                val px = size.toPx()
                val cx = px / 2f
                val cy = px / 2f
                val baseRadius = px / 2f * 0.85f
                val t = rawPhase * 0.5f
                val morph = effTension
                val segments = 60

                val membranePath = Path()
                for (i in 0..segments) {
                    val angle = (i.toFloat() / segments) * 2f * PI_F + t * 0.15f
                    val wave1 = sin(angle * 3f + t * 0.5f) * morph * baseRadius
                    val wave2 = sin(angle * 5f + t * 0.3f) * morph * baseRadius * 0.5f
                    val wave3 = sin(angle * 7f + t * 0.2f + sin(rawPhase * 1.25f)) * morph * baseRadius * 0.25f
                    val r = baseRadius * (1f + wave1 / baseRadius + wave2 / baseRadius + wave3 / baseRadius)
                    val x = cx + r * cos(angle)
                    val y = cy + r * sin(angle)
                    if (i == 0) membranePath.moveTo(x, y) else membranePath.lineTo(x, y)
                }
                membranePath.close()

                if (morph > 0.001f) {
                    drawPath(
                        path = membranePath,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                finalTC.light1.copy(alpha = 0.9f),
                                finalTC.light2.copy(alpha = 0.85f),
                                finalTC.deep1.copy(alpha = 0.9f)
                            ),
                            center = Offset(cx - baseRadius * 0.15f, cy - baseRadius * 0.2f),
                            radius = baseRadius * 1.1f
                        )
                    )

                    drawPath(
                        path = membranePath,
                        color = finalTC.deep2.copy(alpha = 0.3f * (1f + s * 0.3f)),
                        style = Stroke(width = 1.5f)
                    )
                }

                // ── CORE GLOW ──
                val heartSkip = (hb * 0.5f + 0.5f)
                val coreRadiusMul = (0.45f + 0.1f * effGlow) - heartSkip * 0.14f * panicIntensity
                val iRadius = baseRadius * coreRadiusMul.coerceIn(0.15f, 0.65f)

                if (iRadius > 0.5f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                finalTC.light1.copy(alpha = effGlow * 0.5f),
                                finalTC.light2.copy(alpha = effGlow * 0.15f),
                                Color.Transparent
                            ),
                            center = Offset(cx - baseRadius * 0.1f, cy - baseRadius * 0.15f),
                            radius = iRadius
                        ),
                        radius = iRadius
                    )

                    val constrictPhase = sin(rawPhase * 1.25f)
                    val hPhase = constrictPhase * 0.5f + 0.5f
                    val hx = cx - baseRadius * 0.25f + hPhase * baseRadius * 0.04f
                    val hy = cy - baseRadius * 0.3f + hPhase * baseRadius * 0.03f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f * (1f - s * 0.3f)),
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(hx, hy),
                            radius = baseRadius * 0.35f
                        ),
                        radius = baseRadius * 0.35f
                    )
                }
            }
        }

        // ── STREAK CONTENT OVERLAY ──
        if (showContent && showText) {
            val fitSize = size * 0.70f
            val maxCountSp = (fitSize.value * 0.55f).coerceIn(28f, 60f)
            val maxLabelSp = (fitSize.value * 0.13f).coerceIn(7f, 12f)
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .alpha(contentAlpha)
                    .widthIn(max = fitSize),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val streakTxt = streakDays.toString()
                val countFloat = (44f + 16f * (streakDays.toFloat() / 60f).coerceIn(0f, 1f))
                    .coerceIn(28f, maxCountSp)
                val countSize = countFloat.sp
                Text(
                    text = streakTxt,
                    fontSize = countSize,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = com.anantva.tether.ui.theme.Figtree,
                        lineHeight = countSize,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
                Spacer(Modifier.height(2.dp))
                val labelFloat = (8f + 2f * (streakDays.toFloat() / 60f).coerceIn(0f, 1f))
                    .coerceIn(7f, maxLabelSp)
                val labelSize = labelFloat.sp
                Text(
                    text = "Day Streak",
                    fontSize = labelSize,
                    color = Color.White.copy(alpha = 0.8f),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = com.anantva.tether.ui.theme.Figtree,
                        lineHeight = labelSize,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = tier.label,
                    fontSize = (labelSize.value * 0.7f + 3f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.55f),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = com.anantva.tether.ui.theme.Figtree,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
        }
    }
}
