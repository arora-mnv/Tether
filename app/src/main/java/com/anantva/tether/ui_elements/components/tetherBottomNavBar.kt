package com.anantva.tether.ui_elements.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
// Destinations
// ─────────────────────────────────────────────

sealed class NavDestination(val route: String) {
    // ✅ Home is now a real destination — the default state
    object Home     : NavDestination("home")
    object Settings : NavDestination("settings")
    object Vault    : NavDestination("vault")
    object Tips     : NavDestination("tips")
    object Sync     : NavDestination("sync")
}

private data class NavItem(
    val destination:  NavDestination,
    val label:        String,
    val outlinedIcon: ImageVector,
    val filledIcon:   ImageVector,
    val badgeCount:   Int = 0
)

private val navItems = listOf(
    NavItem(NavDestination.Settings, "Settings", Icons.Outlined.Settings,            Icons.Filled.Settings),
    NavItem(NavDestination.Vault,    "Vault",    Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    NavItem(NavDestination.Tips,     "Tips",     Icons.Outlined.Lightbulb,            Icons.Filled.Lightbulb),
    NavItem(NavDestination.Sync,     "Sync",     Icons.Outlined.Backup,               Icons.Filled.Backup)
)

private val TetherRed    = Color(0xFFE53935)
private val NavBg        = Color(0xFF1A1A1A)
private val InactiveGrey = Color(0xFF606060)

// ─────────────────────────────────────────────
// Nav Bar
// ─────────────────────────────────────────────

@Composable
fun TetherBottomNavBar(
    currentDestination:    NavDestination,
    onDestinationSelected: (NavDestination) -> Unit,
    onAddTransaction:      () -> Unit = {},             // ✅ Separate callback for +
    vaultBadgeCount:       Int = 0                      // ✅ Badge for pending transactions
) {
    val view = LocalView.current
    val isOnHome = currentDestination == NavDestination.Home

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // ── Pill ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(NavBg),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Left two items
            navItems.take(2).forEach { item ->
                NavIconItem(
                    item       = item,
                    // ✅ On Home nothing is selected
                    isSelected = !isOnHome && currentDestination == item.destination,
                    badge      = if (item.destination == NavDestination.Vault) vaultBadgeCount else 0,
                    onClick    = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDestinationSelected(item.destination)
                    }
                )
            }

            // Center FAB space
            Spacer(modifier = Modifier.width(68.dp))

            // Right two items
            navItems.takeLast(2).forEach { item ->
                NavIconItem(
                    item       = item,
                    isSelected = !isOnHome && currentDestination == item.destination,
                    badge      = 0,
                    onClick    = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDestinationSelected(item.destination)
                    }
                )
            }
        }

        // ── Center FAB — changes based on context ────────────────────────
        Box(
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .redGlow(
                    glowRadius = if (isOnHome) 20.dp else 12.dp,
                    alpha      = if (isOnHome) 0.55f else 0.3f
                )
                .clip(CircleShape)
                .background(TetherRed)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (isOnHome) {
                        onAddTransaction()
                    } else {
                        onDestinationSelected(NavDestination.Home)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // ✅ Crossfade between + and Home icon
            AnimatedContent(
                targetState   = isOnHome,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                },
                label = "fab_icon"
            ) { onHome ->
                Icon(
                    imageVector        = if (onHome) Icons.Filled.Add else Icons.Filled.Home,
                    contentDescription = if (onHome) "Add Transaction" else "Go Home",
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp)  // ✅ same size always — no jump
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Nav Icon Item — label only on selected
// ─────────────────────────────────────────────

@Composable
private fun NavIconItem(
    item:       NavItem,
    isSelected: Boolean,
    badge:      Int,
    onClick:    () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue   = if (isSelected) TetherRed else InactiveGrey,
        animationSpec = tween(250),
        label         = "iconColor_${item.label}"
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (badge > 0) {
                    Badge(containerColor = TetherRed) {
                        Text(text = badge.toString(), fontSize = 9.sp, color = Color.White)
                    }
                }
            }
        ) {
            Box(
                modifier = if (isSelected)
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(TetherRed.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .redGlow(glowRadius = 10.dp, alpha = 0.25f)
                else
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = if (isSelected) item.filledIcon else item.outlinedIcon,
                    contentDescription = item.label,
                    tint               = iconColor,
                    modifier           = Modifier.size(22.dp)
                )
            }
        }

        // ✅ Label only shows when selected — saves space, cleaner look
        AnimatedVisibility(
            visible = isSelected,
            enter   = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit    = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Text(
                text     = item.label,
                fontSize = 10.sp,
                color    = TetherRed
            )
        }
    }
}

// ─────────────────────────────────────────────
// Glow Effect
// ─────────────────────────────────────────────

fun Modifier.redGlow(glowRadius: Dp, alpha: Float = 0.4f): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color       = android.graphics.Color.TRANSPARENT
                setShadowLayer(
                    glowRadius.toPx(), 0f, 0f,
                    Color(0xFFE53935).copy(alpha = alpha).toArgb()
                )
            }
        }
        canvas.drawCircle(
            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension / 2f,
            paint  = paint
        )
    }
}