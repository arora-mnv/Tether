package com.anantva.tether.ui_elements.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val AvatarBorderColor = Color.White.copy(alpha = 0.15f)

@Composable
fun TetherAvatar(
    userUiState: UserUiState,
    size: Dp,
    isLoggedIn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isLoggedIn) {
        DefaultAvatarIcon(size = size, onClick = onClick, modifier = modifier)
    } else if (userUiState.profileImageUrl.isNullOrBlank()) {
        InitialsAvatar(
            displayName = userUiState.displayName,
            size = size,
            onClick = onClick,
            modifier = modifier
        )
    } else {
        ProfileImage(
            url = userUiState.profileImageUrl,
            displayName = userUiState.displayName,
            size = size,
            onClick = onClick,
            modifier = modifier
        )
    }
}

@Composable
private fun DefaultAvatarIcon(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, AvatarBorderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = "Profile",
            modifier = Modifier.size(size * 0.7f),
            tint = Color.White.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun InitialsAvatar(
    displayName: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val initial = displayName
        .takeIf { it.isNotBlank() && it != "there" }
        ?.first()
        ?.uppercaseChar()
        ?: '?'

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, AvatarBorderColor, CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.toString(),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProfileImage(
    url: String,
    displayName: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier
) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        hasError = false
        bitmap = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val input = connection.getInputStream()
                val loaded = BitmapFactory.decodeStream(input)
                input.close()
                loaded
            } catch (_: Exception) {
                null
            }
        }
        isLoading = false
        if (bitmap == null) hasError = true
    }

    val containerModifier = modifier
        .size(size)
        .clip(CircleShape)
        .border(1.dp, AvatarBorderColor, CircleShape)
        .clickable(onClick = onClick)

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        when {
            hasError -> DefaultAvatarIcon(size = size, onClick = onClick, modifier = Modifier)
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Profile Picture",
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            else -> LoadingPlaceholder(size = size)
        }
    }
}

@Composable
private fun LoadingPlaceholder(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .graphicsLayer { this.alpha = alpha }
    )
}
