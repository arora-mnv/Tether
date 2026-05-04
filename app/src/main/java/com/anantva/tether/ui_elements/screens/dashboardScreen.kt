package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.model.AvatarCatalog
import com.anantva.tether.data.repository.UserData
import com.anantva.tether.ui_elements.components.AvatarIcon
import com.anantva.tether.ui_elements.components.AvatarPickerGrid
import com.anantva.tether.ui_elements.components.TetherBottomNavBar
import com.anantva.tether.ui.theme.VintageCream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.anantva.tether.ui_elements.components.NavDestination as TetherNav

private val TetherRed = Color(0xFFE53935)
private val SafeGreen = Color(0xFF7FAE65)
private val DarkBg    = Color(0xFF0F0F0F)
private val CardBg    = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)
private const val STREAK_CAP = 60

// ✅ NEW: Darker, more vibrant colors to ensure high contrast with white text
private val StreakRed    = Color(0xFFD32F2F)
private val StreakOrange = Color(0xFFE65100)
private val StreakPurple = Color(0xFF6A1B9A)

enum class BalloonState { NORMAL, BURSTING, POPPED }

private data class BurstParticle(
    val angle: Float,
    val maxDistanceDp: Float,
    val color: Color,
    val radiusDp: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel:        DashboardViewModel        = hiltViewModel(),
    pendingViewModel: PendingTransactionViewModel = hiltViewModel(),
    manualTxnViewModel: ManualTransactionViewModel = hiltViewModel(),
    pendingListViewModel: PendingTransactionsViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsState()
    val pendingState    by pendingViewModel.uiState.collectAsState()
    val pendingListState by pendingListViewModel.uiState.collectAsState()
    val insightsState   by insightsViewModel.uiState.collectAsState()
    val spendTrendValues by insightsViewModel.spendTrendValues.collectAsState()
    val trendLabels = insightsViewModel.trendLabels
    val user by viewModel.user.collectAsState()
    var selectedDestination by remember { mutableStateOf<TetherNav>(TetherNav.Home) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showPendingList by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val userAvatarId = user?.avatarId ?: "chill_cat"
    val userName = user?.displayName ?: "there"

    // ✅ Show confirmation sheet whenever a transaction is pending
        if (pendingState.isVisible) {
            TransactionConfirmationSheet(
                state            = pendingState,
                onAmountChange   = pendingViewModel::updateAmount,
                onMerchantChange = pendingViewModel::updateMerchant,
                onCategoryChange = pendingViewModel::updateCategory,
                onToggleRecurring = pendingViewModel::toggleRecurring,
                onToggleType     = pendingViewModel::toggleType,
                onConfirm        = pendingViewModel::confirm,
                onDelete         = pendingViewModel::deleteTransaction,
                onDismiss        = pendingViewModel::snooze
            )
        }

    if (showManualEntry) {
        TransactionEditSheet(
            title = "Add Transaction",
            initialAmount = 0.0,
            initialMerchant = "",
            initialIsDebit = true,
            initialCategory = com.anantva.tether.data.local.entity.SpendingCategories.OTHER,
            initialIsRecurring = false,
            onDismiss = { showManualEntry = false },
            onSave = { amount, merchant, isDebit, category, isRecurring ->
                manualTxnViewModel.addManualTransaction(amount, merchant, isDebit, category, isRecurring)
                showManualEntry = false
            }
        )
    }

    if (showPendingList) {
        PendingTransactionsScreen(
            innerPadding = PaddingValues(0.dp),
            onBack = { showPendingList = false }
        )
        return
    }

    if (showProfile) {
        ProfileSheet(onDismiss = { showProfile = false })
    }
    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            TetherBottomNavBar(
                currentDestination    = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
                // ✅ + tapped while on home
                onAddTransaction      = {
                    showManualEntry = true
                },
                // TODO: Wire real badge count from ViewModel
                vaultBadgeCount       = 0
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TetherRed)
            }
            return@Scaffold
        }

        // ✅ Crossfade between Home content and sub-screens
        Crossfade(
            targetState   = selectedDestination,
            animationSpec = tween(300),
            label         = "screen_content"
        ) { destination ->
            when (destination) {
                is TetherNav.Home -> HomeContent(
                    uiState     = uiState,
                    innerPadding = innerPadding,
                    pendingTransactions = pendingListState.transactions,
                    onSeeAllPending = { showPendingList = true },
                    onOpenProfile = { showProfile = true },
                    userName = userName,
                    userAvatarId = userAvatarId,
                    insightsState = insightsState
                )
                is TetherNav.Insights -> InsightsScreen(
                    innerPadding = innerPadding,
                    insightsState = insightsState,
                    spendTrendValues = spendTrendValues,
                    trendLabels = trendLabels,
                    uiState = uiState,
                    onRefresh = { insightsViewModel.refresh() }
                )
                is TetherNav.Settings -> SettingsScreen(innerPadding = innerPadding)
                is TetherNav.Vault    -> VaultScreen(innerPadding = innerPadding)
                is TetherNav.Tips     -> PlaceholderScreen("Tips", innerPadding)
                is TetherNav.Sync     -> PlaceholderScreen("Sync", innerPadding)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Home Content
// ─────────────────────────────────────────────

@Composable
fun HomeContent(
    uiState: DashboardUiState,
    innerPadding: PaddingValues,
    pendingTransactions: List<TransactionEntity>,
    onSeeAllPending: () -> Unit,
    onOpenProfile: () -> Unit,
    userName: String = "there",
    userAvatarId: String = "chill_cat",
    insightsState: com.anantva.tether.ui_elements.screens.InsightsUiState? = null
) {
    var debugStreak by remember { mutableStateOf<Int?>(null) }
    val effectiveUiState = uiState.copy(
        streakDays = debugStreak ?: uiState.streakDays
    )

    val plusInteractionSource = remember { MutableInteractionSource() }
    val isPlusPressed by plusInteractionSource.collectIsPressedAsState()

    val minusInteractionSource = remember { MutableInteractionSource() }
    val isMinusPressed by minusInteractionSource.collectIsPressedAsState()

    LaunchedEffect(isPlusPressed) {
        if (isPlusPressed) {
            var current = debugStreak ?: uiState.streakDays
            debugStreak = current + 1
            delay(400)
            while (true) {
                current = debugStreak ?: uiState.streakDays
                debugStreak = current + 1
                delay(60)
            }
        }
    }

    LaunchedEffect(isMinusPressed) {
        if (isMinusPressed) {
            var current = debugStreak ?: uiState.streakDays
            debugStreak = (current - 1).coerceAtLeast(0)
            delay(400)
            while (true) {
                current = debugStreak ?: uiState.streakDays
                debugStreak = (current - 1).coerceAtLeast(0)
                delay(60)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            DashboardTopBar(
                onOpenProfile = onOpenProfile,
                userName = userName,
                userAvatarId = userAvatarId
            )
        }

        item { Spacer(Modifier.height(16.dp)) }

        item { BalloonSection(uiState = effectiveUiState) }

        item { Spacer(Modifier.height(20.dp)) }

        item { CoreStatsRow(uiState = effectiveUiState, pendingCount = pendingTransactions.size, onSeeAllPending = onSeeAllPending) }

        item { Spacer(Modifier.height(18.dp)) }

        item { SmartInsightCard(
            uiState = uiState,
            insightsState = insightsState
        ) }

        item { Spacer(Modifier.height(24.dp)) }

        item { PendingSection(pendingTransactions = pendingTransactions, onSeeAll = onSeeAllPending) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = { },
                    interactionSource = plusInteractionSource
                ) { Text("+") }

                TextButton(
                    onClick = { },
                    interactionSource = minusInteractionSource
                ) { Text("-") }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

fun formatCurrency(amount: Int): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

@Composable
fun MilestoneOverlay(milestoneDays: Int, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(milestoneDays) {
        scale.animateTo(1.2f, tween(300))
        scale.animateTo(1f, tween(200))
        alpha.animateTo(1f, tween(400))
        delay(2500)
        alpha.animateTo(0f, tween(600))
        visible = false
        onDone()
    }

    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f * alpha.value))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(scale.value).alpha(alpha.value)
            ) {
                Text(
                    text = "🔥",
                    fontSize = 48.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "$milestoneDays-Day Streak!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (milestoneDays) {
                        3 -> Color(0xFFCD7F32)   // Bronze
                        7 -> Color(0xFFC0C0C0)   // Silver
                        14 -> Color(0xFFFFD700)  // Gold
                        else -> Color(0xFFE5E4E2) // Platinum
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (milestoneDays) {
                        3 -> "You're getting the hang of this."
                        7 -> "A full week. Momentum is real."
                        14 -> "Two weeks strong. Discipline = identity."
                        else -> "A month of control. You're elite."
                    },
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    onOpenProfile: () -> Unit,
    userName: String = "there",
    userAvatarId: String = "chill_cat"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hi, $userName",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Let's keep that streak going",
                color = GrimeGrey,
                fontSize = 13.sp
            )
        }

        AvatarIcon(
            avatarId = userAvatarId,
            size = 42.dp,
            modifier = Modifier.clickable(onClick = onOpenProfile)
        )
    }
}

@Composable
private fun CoreStatsRow(uiState: DashboardUiState, pendingCount: Int, onSeeAllPending: () -> Unit) {
    val safeToSpend = uiState.dailyLimitRemaining.coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "₹ Left today", fontSize = 12.sp, color = GrimeGrey, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatCurrency(safeToSpend),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isOverLimit) TetherRed else SafeGreen
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg)
                .clickable(onClick = onSeeAllPending)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Pending", fontSize = 12.sp, color = GrimeGrey, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(text = "$pendingCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SmartInsightCard(
    uiState: DashboardUiState,
    insightsState: com.anantva.tether.ui_elements.screens.InsightsUiState? = null
) {
    val insight = insightsState?.dailyInsightMessage?.takeIf { it.isNotBlank() }
        ?: smartInsightMessage(uiState)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(TetherRed.copy(alpha = 0.12f), CardBg)
                )
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "💡", fontSize = 22.sp)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(text = "Insight", fontSize = 11.sp, color = TetherRed, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(text = insight, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
            }
        }
    }
}

private fun smartInsightMessage(uiState: DashboardUiState): String {
    val hour = LocalTime.now().hour
    val overBy = uiState.dailySpent - uiState.dailyLimit
    val streak = uiState.streakDays
    return when {
        uiState.isOverLimit && streak > 7 -> "Even legends slip. Tomorrow's a reset."
        uiState.isOverLimit && overBy > uiState.dailyLimit -> "That's wild. Tomorrow lock in fr."
        uiState.isOverLimit -> "Limit breached. Not the vibe."
        streak >= 30 -> "A whole month of discipline. You're built different."
        streak >= 14 -> "Two weeks strong. The streak is watching 👀"
        streak >= 7 -> "A week of staying in lane. Keep it up."
        streak >= 3 -> "Streak's alive. Don't fumble it."
        hour in 0..4 -> "It's 4am and you're checking this. Iconic."
        hour in 5..11 -> "Morning discipline. Rare energy."
        hour in 12..17 -> "Afternoon check-in. Still behaving."
        uiState.dailySpent == 0 -> "Zero spent so far. Suspicious but respected."
        else -> "You're under limit. Keep that energy."
    }
}

@Composable
fun BalloonSection(uiState: DashboardUiState) {
    var balloonState by remember {
        mutableStateOf(if (uiState.streakDays == 0) BalloonState.POPPED else BalloonState.NORMAL)
    }
    val burstScale        = remember { Animatable(1f) }
    val burstAlpha        = remember { Animatable(1f) }
    val particleProgress  = remember { Animatable(0f) }
    val shakeOffset       = remember { Animatable(0f) }
    var milestoneTrigger  by remember { mutableStateOf(0) }

    LaunchedEffect(uiState.isOverLimit, uiState.streakDays) {
        val shouldShowEmpty = uiState.isOverLimit || uiState.streakDays == 0

        when {
            uiState.streakMilestoneReached > 0 -> {
                milestoneTrigger = uiState.streakMilestoneReached
            }
            shouldShowEmpty && balloonState == BalloonState.NORMAL -> {
                balloonState = BalloonState.BURSTING
                burstScale.snapTo(1f)
                burstAlpha.snapTo(1f)
                particleProgress.snapTo(0f)
                shakeOffset.snapTo(0f)

                coroutineScope {
                    launch {
                        burstScale.animateTo(1.2f, tween(110, easing = FastOutSlowInEasing))
                        burstScale.animateTo(0.6f, tween(120, easing = FastOutSlowInEasing))
                    }
                    launch {
                        delay(135)
                        burstAlpha.animateTo(0f, tween(170, easing = LinearEasing))
                    }
                    launch {
                        delay(70)
                        particleProgress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    }
                    launch {
                        delay(70)
                        shakeOffset.animateTo(3f, tween(45, easing = LinearEasing))
                        shakeOffset.animateTo(-2f, tween(55, easing = LinearEasing))
                        shakeOffset.animateTo(1f, tween(55, easing = LinearEasing))
                        shakeOffset.animateTo(0f, tween(75, easing = FastOutSlowInEasing))
                    }
                }
                balloonState = BalloonState.POPPED
            }

            shouldShowEmpty -> {
                burstScale.snapTo(0.6f)
                burstAlpha.snapTo(0f)
                particleProgress.snapTo(1f)
                shakeOffset.snapTo(0f)
                balloonState = BalloonState.POPPED
            }

            else -> {
                burstScale.snapTo(1f)
                burstAlpha.snapTo(1f)
                particleProgress.snapTo(0f)
                shakeOffset.snapTo(0f)
                balloonState = BalloonState.NORMAL
            }
        }
    }

    val streakPct     = (uiState.streakDays.toFloat() / STREAK_CAP).coerceIn(0f, 1f)
    val balloonSizeDp = 100f + 100f * (uiState.streakDays.toFloat() / 100f).coerceIn(0f, 1f)
    val countFontSize = (22f + 8f * streakPct).sp
    val labelFontSize = (8f + 2f * streakPct).sp
    val feedbackMessage = dashboardFeedbackMessage(uiState)

    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue  = -12f,
        targetValue   = 12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val webAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "web_alpha"
    )

    val burstParticles = remember {
        List(12) { i ->
            val base   = (i * 30f * PI / 180f).toFloat()
            val jitter = ((i * 7 % 20) - 10f) * (PI / 180f).toFloat()
            BurstParticle(
                angle         = base + jitter,
                maxDistanceDp = 44f + (i * 7 % 36).toFloat(),
                color         = listOf(
                    Color(0xFFE53935), Color(0xFFFF5252), Color(0xFFB71C1C),
                    Color(0xFFFF8A80), Color(0xFFFFCDD2), Color(0xFFFF1744)
                )[i % 6],
                radiusDp = 3f + (i % 5).toFloat()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.TopCenter)
                .offset(x = shakeOffset.value.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = balloonState != BalloonState.NORMAL,
                animationSpec = tween(360, easing = FastOutSlowInEasing),
                label = "empty_state_crossfade"
            ) { showEmptyState ->
                if (showEmptyState) {
                    EmptyStreakState(webAlpha = webAlpha)
                } else {
                    Spacer(Modifier.size(180.dp))
                }
            }

            if (balloonState != BalloonState.POPPED) {
                StreakBall(
                    uiState = uiState,
                    ballSizeDp = balloonSizeDp,
                    countFontSize = countFontSize,
                    labelFontSize = labelFontSize,
                    floatOffset = if (balloonState == BalloonState.NORMAL) floatOffset else 0f,
                    scale = if (balloonState == BalloonState.BURSTING) burstScale.value else 1f,
                    alpha = if (balloonState == BalloonState.BURSTING) burstAlpha.value else 1f
                )
            }

            if (balloonState == BalloonState.BURSTING) {
                val progress = particleProgress.value
                Canvas(modifier = Modifier.fillMaxSize()) {
                    burstParticles.forEach { particle ->
                        val distance = particle.maxDistanceDp.dp.toPx() * progress
                        val px       = center.x + cos(particle.angle) * distance
                        val py       = center.y + sin(particle.angle) * distance
                        val pAlpha   = (1f - progress).coerceAtLeast(0f)
                        val pRadius  = (particle.radiusDp.dp.toPx() * (1f - progress * 0.4f)).coerceAtLeast(1f)
                        drawCircle(
                            color  = particle.color.copy(alpha = pAlpha),
                            radius = pRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        if (milestoneTrigger > 0) {
            MilestoneOverlay(milestoneDays = milestoneTrigger) { milestoneTrigger = 0 }
        }

        Text(
            feedbackMessage,
            modifier = Modifier.align(Alignment.BottomCenter),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isOverLimit) TetherRed else GrimeGrey,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StreakBall(
    uiState: DashboardUiState,
    ballSizeDp: Float,
    countFontSize: androidx.compose.ui.unit.TextUnit,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    floatOffset: Float,
    scale: Float,
    alpha: Float
) {
    val streak = uiState.streakDays
    val levelColor = when (uiState.streakLevel) {
        "SILVER" -> Color(0xFFC0C0C0)
        "GOLD" -> Color(0xFFFFD700)
        "PLATINUM" -> Color(0xFFE5E4E2)
        else -> StreakRed
    }

    Box(
        modifier = Modifier
            .offset(y = floatOffset.dp)
            .requiredSize(ballSizeDp.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        levelColor.copy(alpha = 0.9f),
                        levelColor,
                        levelColor.copy(alpha = 0.7f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (ballSizeDp * 0.18f).dp, y = (ballSizeDp * 0.16f).dp)
                .size((ballSizeDp * 0.22f).dp)
                .background(Color.White.copy(alpha = 0.13f), CircleShape)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = streak.toString(),
                fontSize   = countFontSize,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Text(
                text     = "Day Streak",
                fontSize = labelFontSize,
                color    = Color.White.copy(alpha = 0.85f)
            )
            if (uiState.streakLevel != "BRONZE") {
                Text(
                    text     = uiState.streakLevel,
                    fontSize = (labelFontSize.value * 0.7).sp,
                    fontWeight = FontWeight.Bold,
                    color    = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyStreakState(webAlpha: Float) {
    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        SpiderWeb(
            modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 0.dp).size(42.dp),
            alpha = webAlpha * 0.9f, index = 0
        )
        SpiderWeb(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 10.dp).size(34.dp),
            alpha = webAlpha * 0.72f, index = 1
        )
        SpiderWeb(
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp).size(38.dp),
            alpha = webAlpha * 0.65f, index = 2
        )

        Box(modifier = Modifier.size(116.dp), contentAlignment = Alignment.Center) {
            repeat(7) { BuzzingFly(index = it) }
        }
    }
}

@Composable
fun SpiderWeb(modifier: Modifier = Modifier, alpha: Float, index: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "web_$index")
    val drift by infiniteTransition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900 + index * 320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "web_drift_$index"
    )

    Canvas(modifier = modifier.offset(x = drift.dp, y = (-drift * 0.6f).dp).alpha(alpha.coerceIn(0f, 0.24f))) {
        val webColor = Color(0xFF6F6F6F)
        val outerRadius = size.minDimension * 0.44f
        val centerOffset = center

        listOf(0.28f, 0.48f, 0.68f, 0.88f).forEach { radiusFraction ->
            drawCircle(
                color = webColor,
                radius = outerRadius * radiusFraction,
                center = centerOffset,
                style = Stroke(width = 0.8.dp.toPx())
            )
        }
        repeat(7) { spoke ->
            val angle = (spoke * 360f / 7f) * PI.toFloat() / 180f
            drawLine(
                color = webColor,
                start = centerOffset,
                end = Offset(centerOffset.x + cos(angle) * outerRadius, centerOffset.y + sin(angle) * outerRadius),
                strokeWidth = 0.8.dp.toPx()
            )
        }
    }
}

@Composable
fun BuzzingFly(index: Int, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "fly_$index")
    val baseX       = listOf(-48f, 34f, -18f, 52f, -36f, 16f, 2f)[index % 7]
    val baseY       = listOf(-26f, -12f, 30f, 22f, 10f, -40f, 44f)[index % 7]
    val orbitRadiusX = 10f + (index * 7 % 18)
    val orbitRadiusY = 7f + (index * 5 % 15)
    val orbitSpeed  = listOf(820, 1180, 960, 1430, 1090, 760, 1320)[index % 7]
    val startPhase  = index * 73f + 19f

    val angle by infiniteTransition.animateFloat(
        initialValue  = startPhase,
        targetValue   = startPhase + if (index % 2 == 0) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(orbitSpeed, easing = LinearEasing)),
        label         = "orbit_$index"
    )
    val wobbleX by infiniteTransition.animateFloat(
        initialValue  = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(animation  = tween(120 + index * 40, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wx_$index"
    )
    val wobbleY by infiniteTransition.animateFloat(
        initialValue  = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(animation  = tween(100 + index * 30, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "wy_$index"
    )
    val flyAlpha by infiniteTransition.animateFloat(
        initialValue = 0.26f + (index % 3) * 0.05f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(animation = tween(900 + index * 110, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "fly_alpha_$index"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -18f + index * 3f,
        targetValue = 18f - index * 2f,
        animationSpec = infiniteRepeatable(animation = tween(360 + index * 45, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "fly_rotation_$index"
    )

    val rad = angle * PI.toFloat() / 180f
    val x   = baseX + cos(rad) * orbitRadiusX + wobbleX
    val y   = baseY + sin(rad * 1.27f) * orbitRadiusY + wobbleY

    Box(modifier = modifier.size(14.dp, 8.dp).offset(x = x.dp, y = y.dp).rotate(rotation).alpha(flyAlpha)) {
        Box(modifier = Modifier.align(Alignment.TopStart).size(6.dp, 4.dp).background(Color(0xFF8A8A8A).copy(alpha = 0.44f), CircleShape))
        Box(modifier = Modifier.align(Alignment.TopEnd).size(6.dp, 4.dp).background(Color(0xFF8A8A8A).copy(alpha = 0.38f), CircleShape))
        Box(modifier = Modifier.align(Alignment.Center).size(9.dp, 5.dp).background(Color(0xFF5A5A5A), CircleShape))
    }
}

@Composable
fun PlaceholderScreen(title: String, innerPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding).background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(text = "Coming soon", style = MaterialTheme.typography.bodyMedium, color = GrimeGrey)
        }
    }
}

@Composable
fun PendingSection(pendingTransactions: List<TransactionEntity>, onSeeAll: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("Pending", style = MaterialTheme.typography.titleLarge, color = Color.White)
        TextButton(onClick = onSeeAll) { Text("See all", color = TetherRed) }
    }
    Spacer(Modifier.height(14.dp))

    if (pendingTransactions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp)).background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Text("No pending transactions", style = MaterialTheme.typography.bodyMedium, color = GrimeGrey)
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        pendingTransactions.take(3).forEach { txn ->
            MiniPendingRow(transaction = txn, onClick = onSeeAll)
        }
    }
}

@Composable
private fun MiniPendingRow(transaction: TransactionEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.merchant, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(text = "Tap to review", color = GrimeGrey, fontSize = 11.sp)
        }
        Text(text = formatCurrency(transaction.amount), color = TetherRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun dashboardFeedbackMessage(uiState: DashboardUiState): String {
    val hour = LocalTime.now().hour
    val overBy = uiState.dailySpent - uiState.dailyLimit
    return when {
        uiState.isOverLimit && hour in 0..4 -> "Midnight damage, huh?"
        uiState.isOverLimit && overBy > uiState.dailyLimit * 0.5 -> "That escalated quickly."
        uiState.isOverLimit && hour in 19..23 -> "Dinner did numbers."
        uiState.isOverLimit -> "Bro... again?"
        hour in 0..4 -> "Late-night discipline arc."
        hour in 5..11 -> "Clean start. Rare."
        hour in 12..17 -> "Still behaving. Suspicious."
        else -> "Aaj control ho gaya."
    }
}