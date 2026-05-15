package com.anantva.tether.ui_elements.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anantva.tether.data.model.AvatarMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

private fun Long.toColor() = Color(this)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private val PI_F = PI.toFloat()

private fun triangleWave(t: Float): Float {
    val n = (t % (2f * PI_F)) / (2f * PI_F)
    return if (n < 0.5f) 4f * n - 1f else 3f - 4f * n
}

private fun sawWave(t: Float): Float {
    val n = (t % (2f * PI_F)) / (2f * PI_F)
    return 2f * n - 1f
}

private fun squareWave(t: Float): Float {
    val n = (t % (2f * PI_F)) / (2f * PI_F)
    return if (n < 0.5f) 1f else -1f
}

private fun morphWaveform(t: Float, sharpness: Float): Float {
    val sinW = sin(t)
    return when {
        sharpness <= 0.33f -> lerp(sinW, triangleWave(t), sharpness / 0.33f)
        sharpness <= 0.66f -> lerp(triangleWave(t), sawWave(t), (sharpness - 0.33f) / 0.33f)
        else -> lerp(sawWave(t), squareWave(t), (sharpness - 0.66f) / 0.34f)
    }
}

@Composable
fun ProceduralAvatar(
    colors: List<Long>,
    motion: AvatarMotion,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 40.dp,
    chaosInfluence: Float = 0f,
    glowIntensity: Float = 1f,
    stressLevel: Float = 0f
) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            phase.floatValue += 0.016f
        }
    }
    val c = colors.map { it.toColor() }
    val stress = stressLevel.coerceIn(0f, 1f)

    val speedMul = lerp(1f, 2.5f, stress)
    val ampMul = lerp(1f, 2f, stress)
    val sharpness = stress
    val glowMul = lerp(1f, 1.5f, stress)
    val flicker = stress * 0.3f

    Canvas(modifier = modifier.size(avatarSize)) {
        val px = avatarSize.toPx()
        val cx = px / 2f
        val cy = px / 2f
        val r = px / 2f
        val t = phase.floatValue * motion.morphSpeed * speedMul

        val segments = 48
        val chaos = chaosInfluence + stress * 0.5f
        val morph = 1f + chaos * 1.5f
        val path = Path()
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * 2f * PI_F
            val baseWobble = motion.wobbleAmp * ampMul * morph
            val wave1 = morphWaveform(angle * motion.wobbleFreq + t * motion.driftSpeed, sharpness) * baseWobble
            val wave2 = morphWaveform(angle * (motion.wobbleFreq * 1.7f) - t * motion.driftSpeed * 0.7f, sharpness) * baseWobble * 0.5f
            val wave3 = morphWaveform(t * 0.5f * motion.morphSpeed, sharpness) * baseWobble * 0.3f * chaos
            val radius = r * (0.82f + wave1 + wave2 + wave3)
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val fillAlpha = lerp(1f, 0.85f, stress)
        drawPath(path, Brush.radialGradient(
            colors = listOf(
                c.getOrElse(2) { c[0] }.copy(alpha = fillAlpha),
                c[0].copy(alpha = fillAlpha * 0.8f),
                c.getOrElse(1) { c[0] }.copy(alpha = fillAlpha * 0.45f)
            ),
            center = Offset(cx - r * 0.2f, cy - r * 0.25f),
            radius = r * 1.1f
        ))
        drawPath(path, c[0].copy(alpha = 0.35f * glowIntensity * glowMul), style = Stroke(width = 1.6f))

        val auraAlpha = 0.08f * glowIntensity * glowMul * (1f - flicker * 0.5f * sin(t * 8f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(c[0].copy(alpha = auraAlpha), c[0].copy(alpha = auraAlpha * 0.25f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 1.15f
            ),
            radius = r * 1.15f
        )

        val highlightShift = sin(t * 0.5f) * r * 0.04f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(cx - r * 0.25f + highlightShift, cy - r * 0.3f),
                radius = r * 0.45f
            ),
            radius = r * 0.45f
        )
        if (stress < 0.95f) {
            drawCircle(
                color = c[0].copy(alpha = 0.1f * glowIntensity * glowMul),
                radius = r * 0.95f,
                style = Stroke(width = 1.2f)
            )
        }
    }
}
