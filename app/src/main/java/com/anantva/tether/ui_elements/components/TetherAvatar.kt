package com.anantva.tether.ui_elements.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private val TetherRed = Color(0xFFE53935)
private val PlaceholderBg = Color(0xFF2A2A2A)

@Composable
fun TetherAvatar(
    imageUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (!imageUrl.isNullOrBlank()) {
        ProfileImage(url = imageUrl, size = size, modifier = modifier)
    } else {
        Placeholder(size = size, modifier = modifier)
    }
}

@Composable
private fun ProfileImage(url: String, size: Dp, modifier: Modifier) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val input = connection.getInputStream()
                bitmap = BitmapFactory.decodeStream(input)
                input.close()
            } catch (_: Exception) { }
        }
    }

    val imageBitmap = bitmap?.asImageBitmap()

    Box(modifier = modifier.size(size)) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Placeholder(size = size)
        }
    }
}

@Composable
private fun Placeholder(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(PlaceholderBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = TetherRed,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}
