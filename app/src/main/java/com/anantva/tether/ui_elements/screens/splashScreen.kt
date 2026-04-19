package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.ui.theme.Figtree
import kotlinx.coroutines.delay

private val TetherRed     = Color(0xFFE53935)
private val DarkBg        = Color(0xFF0F0F0F)

@Composable
fun SplashScreen(
    hasCompletedSetup: Boolean,
    onNavigateToDashboard: () -> Unit,
    onNavigateToSetup: () -> Unit
) {
    // --- Animations ---
    val balloonScale = remember { Animatable(0f) }
    val textAlpha   = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue  = 14f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "balloonFloat"
    )

    LaunchedEffect(Unit) {
        // 1. Balloon pops in
        balloonScale.animateTo(
            targetValue  = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessLow
            )
        )

        // 2. Text fades in
        textAlpha.animateTo(
            targetValue  = 1f,
            animationSpec = tween(600)
        )

        // 3. Hold for cinematic effect
        delay(1800L)

        // 4. Route — no null check needed, value is already resolved
        if (hasCompletedSetup) onNavigateToDashboard() else onNavigateToSetup()
    }

    // --- UI ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Floating Balloon
            Box(
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .scale(balloonScale.value)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF5252),
                                TetherRed,
                                Color(0xFFB71C1C)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
            }

            Spacer(modifier = Modifier.height(48.dp))

            // App name
            Text(
                text     = "TETHER",
                fontSize = 40.sp,
                fontWeight = FontWeight.W900,
                fontFamily = Figtree,
                color    = Color.White,
                letterSpacing = 8.sp,
                modifier = Modifier.scale(balloonScale.value)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tagline
            Text(
                text      = "Don't let your money float away!",
                style     = MaterialTheme.typography.bodySmall,
                color     = Color(0xFFA0A0A0),
                textAlign = TextAlign.Center,
                modifier  = Modifier
                    .padding(horizontal = 32.dp)
                    // Reuse textAlpha for fade-in
                    .scale(textAlpha.value.coerceAtLeast(0.85f))
                    .let { mod ->
                        // Manual alpha via graphicsLayer
                        mod.then(
                            Modifier.graphicsLayer { alpha = textAlpha.value }
                        )
                    }
            )
        }
    }
}