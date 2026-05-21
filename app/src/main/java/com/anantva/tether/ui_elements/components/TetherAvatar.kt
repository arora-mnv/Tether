package com.anantva.tether.ui_elements.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun TetherAvatar(
    userUiState: UserUiState,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (userUiState.isCloudSyncEnabled && !userUiState.profileImageUrl.isNullOrBlank()) {
        ProfileImage(url = userUiState.profileImageUrl, size = size, modifier = modifier)
    } else {
        FallbackOrb(userUiState = userUiState, size = size, modifier = modifier)
    }
}

@Composable
private fun ProfileImage(url: String, size: Dp, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url) {
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
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Profile Picture",
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        FallbackOrb(userUiState = UserUiState(), size = size, modifier = modifier)
    }
}

@Composable
private fun FallbackOrb(
    userUiState: UserUiState,
    size: Dp,
    modifier: Modifier
) {
    TetherOrb(
        stressLevel = userUiState.stressLevel,
        streakDays = userUiState.streak,
        size = size,
        modifier = modifier,
        showText = false
    )
}
