package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.ui.theme.Figtree
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val TetherRed = Color(0xFFE53935)
private const val BPM = 40f
private const val BEAT_MS = (60000f / BPM).toInt()
private val PI_F = 3.1415927f

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Particles()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Orb()

            Spacer(modifier = Modifier.height(28.dp))

            androidx.compose.material3.Text(
                text = "Tether",
                fontSize = 44.sp,
                fontWeight = FontWeight.W900,
                fontFamily = Figtree,
                color = Color.White,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.material3.Text(
                text = "Frictionless Money Growth",
                fontSize = 14.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center,
                fontFamily = Figtree,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
private fun Orb() {
    val t = rememberInfiniteTransition(label = "orb")

    val raw by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(BEAT_MS, easing = LinearEasing), RepeatMode.Restart),
        "raw"
    )

    val breath by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        "breath"
    )

    Canvas(modifier = Modifier.size(100.dp)) {
        val p = raw
        val br = breath * 2f * PI_F
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f

        val b = when {
            p < 0.12f -> p / 0.12f
            p < 0.22f -> 1f
            else -> {
                val rec = (p - 0.22f) / 0.78f
                1f - rec * rec
            }
        }

        val membPhase = ((b * 1.4f).coerceAtMost(1f) - 0.2f).coerceAtLeast(0f) / 0.8f
        val membR = r + membPhase * 2.5f
        val core = 1f - b * 0.3f
        val glow = (1f - b * 1.8f).coerceIn(0.1f, 1f)
        val brMod = sin(br) * 0.008f
        val brGlow = 0.5f + 0.5f * sin(br + PI_F)
        val gx = sin(br * 0.7f) * 0.03f
        val gy = cos(br * 0.5f) * 0.03f

        drawCircle(
            Brush.radialGradient(
                listOf(TetherRed.copy(alpha = (0.12f + glow * 0.25f + brGlow * 0.08f) * 0.45f), Color.Transparent),
                c, r * 2.5f
            ), r * 2.5f, c
        )

        if (b < 0.35f) {
            val rp = b / 0.35f; val ra = (1f - rp) * 0.18f; val rr = r * (1f + rp * 1.6f)
            drawCircle(TetherRed.copy(alpha = ra), rr, c, style = Stroke(2.5f * ra * 4f))
            drawCircle(TetherRed.copy(alpha = ra * 0.4f), rr * 1.08f, c, style = Stroke(1.5f * ra * 4f))
        }

        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFF6B6B), TetherRed, Color(0xFFC62828), Color(0xFFB71C1C)),
                Offset(c.x + gx * r * 2f, c.y + gy * r * 2f), membR
            ), membR, Offset(c.x + brMod * r * 3f, c.y + brMod * r * 2f)
        )

        val innerR = r * 0.55f * core
        val ic = Offset(c.x - r * 0.08f, c.y - r * 0.12f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.35f * core), Color.White.copy(alpha = 0.08f * core), Color.Transparent),
                ic, innerR
            ), innerR, ic
        )

        val hc = Offset(c.x - r * 0.25f, c.y - r * 0.3f)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                hc, r * 0.18f
            ), r * 0.18f, hc
        )

        drawCircle(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent),
                c, membR * 0.98f
            ), membR * 0.98f, c
        )
    }
}

@Composable
private fun Particles() {
    var ps by remember { mutableStateOf<List<Particle>>(emptyList()) }

    LaunchedEffect(Unit) {
        ps = List(24) {
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speed = Random.nextFloat() * 0.15f + 0.04f,
                phase = Random.nextFloat() * 360f,
                wobbleFreq = Random.nextFloat() * 1.5f + 0.5f,
                wobbleAmp = Random.nextFloat() * 0.015f + 0.005f,
                alpha = Random.nextFloat() * 0.25f + 0.04f,
                size = Random.nextFloat() * 2f + 0.6f
            )
        }
    }

    val t by rememberInfiniteTransition(label = "pt").animateFloat(
        0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Restart), "t"
    )

    Canvas(Modifier.fillMaxSize()) {
        ps.forEach { p ->
            val drift = (p.y + t * p.speed / 100f * size.height / 20f) % 1f
            val wobble = sin(t * p.wobbleFreq * 4f + p.phase) * p.wobbleAmp
            val px = (p.x + wobble) * size.width
            val py = drift * size.height
            val fade = (1f - kotlin.math.abs(drift - 0.5f) * 2f).coerceAtLeast(0f)
            drawCircle(Color.White.copy(alpha = p.alpha * fade), p.size, Offset(px, py))
        }
    }
}

private data class Particle(
    val x: Float, val y: Float, val speed: Float, val phase: Float,
    val wobbleFreq: Float, val wobbleAmp: Float, val alpha: Float, val size: Float
)
