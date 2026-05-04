package com.anantva.tether.ui_elements.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.data.model.AvatarCatalog
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.GrimeGrey
import com.anantva.tether.ui.theme.TetherWhite

@Composable
fun AvatarIcon(
    avatarId: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val avatar = remember(avatarId) { AvatarCatalog.getAvatarById(avatarId) }
    val gradientColors = remember(avatar.bgGradient) {
        avatar.bgGradient.map { Color(it) }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors = gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = avatar.emoji,
            fontSize = (size.value * 0.55).sp
        )
    }
}

@Composable
fun AvatarPickerGrid(
    selectedAvatarId: String,
    onAvatarSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(AvatarCatalog.defaultAvatars) { avatar ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAvatarSelected(avatar.id) }
            ) {
                AvatarIcon(
                    avatarId = avatar.id,
                    size = 64.dp,
                    modifier = Modifier.then(
                        if (avatar.id == selectedAvatarId) {
                            Modifier.border(2.dp, TetherRed, CircleShape)
                        } else {
                            Modifier
                        }
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = avatar.label,
                    color = if (avatar.id == selectedAvatarId) TetherWhite else GrimeGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
