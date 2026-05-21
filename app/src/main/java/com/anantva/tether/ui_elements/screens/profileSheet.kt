package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.ui_elements.components.TetherAvatar
import com.anantva.tether.ui_elements.components.UserUiState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import com.anantva.tether.ui.theme.TetherRed

private val CardBg = Color(0xFF1A1A1A)
private val DarkBg = Color(0xFF0F0F0F)
private val GrimeGrey = Color(0xFFA0A0A0)
private val SubtextGrey = Color(0xFF6F6F6F)
private val DimWhite = Color.White.copy(alpha = 0.7f)

private val MoodGreat = Color(0xFF81C784)
private val MoodGood = Color(0xFF4FC3F7)
private val MoodMixed = Color(0xFFFFB74D)
private val MoodBad = Color(0xFFE53935)

private fun personalityColor(p: String): Color = when (p) {
    "Disciplined", "Elite", "Stable", "Controlled" -> MoodGreat
    "Balanced", "Steady", "Coasting" -> MoodGood
    "Aware", "Forming" -> MoodMixed
    else -> MoodBad
}

private fun personalitySubtext(p: String, streakDays: Int): String = when {
    streakDays >= 30 -> "Streak master"
    streakDays >= 14 -> "Building momentum"
    streakDays >= 7 -> "Consistent week"
    streakDays > 0 -> "Streak active"
    else -> when (p) {
        "Disciplined", "Elite" -> "Controlled energy"
        "Stable" -> "Steady spender"
        "Controlled" -> "Intentional choices"
        "Balanced" -> "Healthy rhythm"
        "Coasting" -> "Light touch today"
        "Impulsive" -> "Impulse-heavy"
        "Spiraling" -> "Scattered patterns"
        "Reactive" -> "Reacting, not planning"
        "Aware" -> "Building awareness"
        else -> "Finding your rhythm"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    personality: String = "Forming",
    userUiState: UserUiState = UserUiState(),
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val statePersonality = if (personality != "Forming") personality else uiState.personality

    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var email by remember(uiState.email) { mutableStateOf(uiState.email) }
    var phone by remember(uiState.phone) { mutableStateOf(uiState.phone) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .animateContentSize(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Identity section ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val phase = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(16)
                        phase.floatValue += 0.016f
                    }
                }
                val ambientColor = personalityColor(statePersonality)
                Canvas(modifier = Modifier.matchParentSize()) {
                    val t = phase.floatValue
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val d = size.minDimension
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ambientColor.copy(alpha = 0.08f), ambientColor.copy(alpha = 0.02f), Color.Transparent),
                            center = Offset(cx, cy * 0.5f),
                            radius = d * 0.6f
                        ),
                        radius = d * 0.6f
                    )
                    for (i in 0..11) {
                        val a = t * 0.2f + i * 0.524f + sin(t * 0.15f + i) * 0.3f
                        val dist = d * (0.3f + 0.2f * (0.5f + 0.5f * sin(t * 0.35f + i * 1.3f)))
                        val px = cx + dist * cos(a)
                        val py = cy + dist * sin(a * 0.6f) * 0.35f
                        val pulse = 0.5f + 0.5f * sin(t * 0.5f + i * 2.1f)
                        drawCircle(
                            ambientColor.copy(alpha = 0.035f * pulse),
                            radius = d * (0.035f + 0.025f * pulse),
                            center = Offset(px, py)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = GrimeGrey)
                        }
                    }

                    TetherAvatar(
                        userUiState = userUiState,
                        size = 88.dp
                    )

                    Spacer(Modifier.height(14.dp))

                    val displayName = (name.takeIf { it.isNotBlank() } ?: uiState.name)
                        .takeIf { it.isNotBlank() } ?: "Tether User"
                    Text(
                        text = displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(4.dp))

                    val subtext = personalitySubtext(statePersonality, uiState.streakDays)
                    Text(
                        text = subtext,
                        fontSize = 14.sp,
                        color = ambientColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    if (uiState.streakDays > 0) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\uD83D\uDD25", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${uiState.streakDays}-day streak",
                                fontSize = 12.sp,
                                color = SubtextGrey
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Identity card ──
            SectionCard(title = "Identity") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = GrimeGrey) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = GrimeGrey) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone", color = GrimeGrey) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(12.dp))

            // ── Sync card ──
            SectionCard(title = "Sync") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloud sync", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (uiState.isCloudStorage) "Synced across devices" else "Local-only mode",
                            color = GrimeGrey,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = uiState.isCloudStorage,
                        onCheckedChange = viewModel::setCloudStorage
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Save ──
            Button(
                onClick = {
                    viewModel.save(name.trim(), email.trim(), phone.trim())
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Text("Save changes", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            phase.floatValue += 0.016f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x18FFFFFF))
            .padding(1.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val t = phase.floatValue
            val sweepX = size.width * (0.5f + 0.5f * sin(t * 0.3f))
            val sweepY = size.height * (0.3f + 0.2f * sin(t * 0.4f + 1f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(sweepX, sweepY),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x0A000000))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x0FFFFFFF))
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.35f),
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
