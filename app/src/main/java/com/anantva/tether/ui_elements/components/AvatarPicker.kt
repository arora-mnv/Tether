package com.anantva.tether.ui_elements.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.data.model.AvatarCatalog
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun AvatarIcon(
    avatarId: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    personality: String = "",
    stressLevel: Float = 0f
) {
    val avatar = remember(avatarId) { AvatarCatalog.getAvatarById(avatarId) }
    val chaosInfluence = personalityChaos(personality)

    val floatPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            floatPhase.floatValue += 0.016f
        }
    }
    val floatY = sin(floatPhase.floatValue * avatar.motion.morphSpeed * 1.5f) * 1.5f

    Box(
        modifier = modifier
            .size(size)
            .offset(y = (floatY * 0.5f).dp)
    ) {
        ProceduralAvatar(
            colors = avatar.colors,
            motion = avatar.motion,
            avatarSize = size,
            chaosInfluence = chaosInfluence,
            glowIntensity = avatar.glowIntensity,
            stressLevel = stressLevel
        )
    }
}

@Composable
fun AvatarPickerGrid(
    selectedAvatarId: String,
    onAvatarSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AvatarCatalog.defaultAvatars) { avatar ->
            val isSelected = avatar.id == selectedAvatarId

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onAvatarSelected(avatar.id) }
                    .scale(if (isSelected) 1f else 0.85f)
                    .alpha(if (isSelected) 1f else 0.45f)
            ) {
                AvatarIcon(
                    avatarId = avatar.id,
                    size = if (isSelected) 56.dp else 44.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = avatar.label,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun personalityChaos(p: String): Float = when (p) {
    "Disciplined", "Elite", "Stable" -> 0f
    "Controlled", "Balanced", "Steady" -> 0.15f
    "Coasting", "Aware" -> 0.3f
    "Impulsive", "Reactive" -> 0.7f
    "Spiraling" -> 1f
    else -> 0.2f
}

@Composable
fun FinancialAuraAvatar(
    avatarId: String,
    usagePercent: Float = 0f,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    wantsRatio: Float = 0.5f
) {
    val wantsTotal = wantsRatio.coerceIn(0f, 1f)
    val emotion = computeFinancialEmotion(usagePercent, wantsTotal)
    AvatarIcon(
        avatarId = avatarId,
        modifier = modifier,
        size = size,
        stressLevel = emotion.stressLevel
    )
}

data class FinancialEmotion(
    val title: String = "Calm",
    val stressLevel: Float = 0f,
    val subtitle: String = "Easy pace."
)

private fun computeFinancialEmotion(usagePercent: Float, wantsRatio: Float): FinancialEmotion {
    val u = usagePercent.coerceIn(0f, 1.2f)
    return when {
        u > 1f || (usagePercent > 0.9f && wantsRatio > 0.6f) ->
            FinancialEmotion("Chaotic", 1.2f, "Past today's limit.")
        u > 0.9f -> FinancialEmotion("Restless", 1f, "Energy spiking.")
        u > 0.75f -> FinancialEmotion("Tense", 0.8f, "Approaching the edge.")
        u > 0.55f -> FinancialEmotion("Active", 0.5f, "Spending picked up.")
        u > 0.35f -> FinancialEmotion("Balanced", 0.3f, "Healthy movement.")
        u > 0.15f -> FinancialEmotion("Focused", 0.15f, "Controlled rhythm.")
        else -> FinancialEmotion("Calm", 0f, "Plenty of room.")
    }
}
