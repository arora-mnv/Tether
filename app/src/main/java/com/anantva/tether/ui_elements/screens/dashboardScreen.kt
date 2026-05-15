package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.BuildConfig
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.data.model.AvatarCatalog
import com.anantva.tether.data.repository.UserData
import com.anantva.tether.ui_elements.components.AvatarIcon
import com.anantva.tether.ui_elements.components.AvatarPickerGrid
import com.anantva.tether.ui_elements.components.FinancialAuraAvatar
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

// ===== EXACT COLOR SYSTEM FOR STREAK ORB =====
// 0-7 DAYS — BRONZE
private val BronzeLight1 = Color(0xFFFFB36B) // #FFB36B
private val BronzeLight2 = Color(0xFFFF8C42) // #FF8C42
private val BronzeDeep1 = Color(0xFFE05A2A)  // #E05A2A
private val BronzeDeep2 = Color(0xFFB63A16)  // #B63A16

// 7-21 DAYS — SILVER
private val SilverLight1 = Color(0xFFDCE6F2) // #DCE6F2
private val SilverLight2 = Color(0xFFB8C7D9) // #B8C7D9
private val SilverDeep1 = Color(0xFF7E8FA6)  // #7E8FA6
private val SilverDeep2 = Color(0xFF58677D)  // #58677D

// 21-60 DAYS — GOLD
private val GoldLight1 = Color(0xFFFFE27A)   // #FFE27A
private val GoldLight2 = Color(0xFFFFC93D)   // #FFC93D
private val GoldDeep1 = Color(0xFFF5A300)    // #F5A300
private val GoldDeep2 = Color(0xFFD97B00)    // #D97B00

// 60-120 DAYS — PURPLE
private val PurpleLight1 = Color(0xFFC084FF) // #C084FF
private val PurpleLight2 = Color(0xFF9B5CFF) // #9B5CFF
private val PurpleDeep1 = Color(0xFF6B2DFF)  // #6B2DFF
private val PurpleDeep2 = Color(0xFF4A00E0)  // #4A00E0

// 120-250 DAYS — DEEP GOLD
private val DeepGoldLight1 = Color(0xFFFFD95A) // #FFD95A
private val DeepGoldLight2 = Color(0xFFFFB800) // #FFB800
private val DeepGoldDeep1 = Color(0xFFFF8A00)  // #FF8A00
private val DeepGoldDeep2 = Color(0xFFD96A00)  // #D96A00

// 250-365 DAYS — ORANGE
private val OrangeLight1 = Color(0xFFFF9A4D)   // #FF9A4D
private val OrangeLight2 = Color(0xFFFF6B1A)   // #FF6B1A
private val OrangeDeep1 = Color(0xFFE63E00)    // #E63E00
private val OrangeDeep2 = Color(0xFFB82500)    // #B82500

// 365+ DAYS — RED
private val RedLight1 = Color(0xFFFF6B6B)      // #FF6B6B
private val RedLight2 = Color(0xFFFF3B3B)      // #FF3B3B
private val RedDeep1 = Color(0xFFD10000)       // #D10000
private val RedDeep2 = Color(0xFF7A0000)       // #7A0000

// Data class for streak colors
private data class StreakColors(
    val light1: Color,
    val light2: Color,
    val deep1: Color,
    val deep2: Color
)

// Tier palette definitions (per-channel min/max for each tier)
private enum class TierPalette(
    val light1: Color, val light2: Color, val deep1: Color, val deep2: Color
) {
    BRONZE(BronzeLight1, BronzeLight2, BronzeDeep1, BronzeDeep2),
    SILVER(SilverLight1, SilverLight2, SilverDeep1, SilverDeep2),
    GOLD(GoldLight1, GoldLight2, GoldDeep1, GoldDeep2),
    PURPLE(PurpleLight1, PurpleLight2, PurpleDeep1, PurpleDeep2),
    DEEP_GOLD(DeepGoldLight1, DeepGoldLight2, DeepGoldDeep1, DeepGoldDeep2),
    ORANGE(OrangeLight1, OrangeLight2, OrangeDeep1, OrangeDeep2),
    RED(RedLight1, RedLight2, RedDeep1, RedDeep2)
}

// 0..6 BRONZE, 7..20 SILVER, 21..59 GOLD, 60..119 PURPLE, 120..249 DEEP_GOLD, 250..364 ORANGE, 365+ RED
private fun tierAt(streak: Int): TierPalette = when {
    streak < 7    -> TierPalette.BRONZE
    streak < 21   -> TierPalette.SILVER
    streak < 60   -> TierPalette.GOLD
    streak < 120  -> TierPalette.PURPLE
    streak < 250  -> TierPalette.DEEP_GOLD
    streak < 365  -> TierPalette.ORANGE
    else          -> TierPalette.RED
}

private const val TIER_TRANSITION_DURATION_MS = 950

private fun TierPalette.toStreakColors(): StreakColors = StreakColors(
    light1 = light1,
    light2 = light2,
    deep1 = deep1,
    deep2 = deep2
)

private fun lerpStreakColors(from: StreakColors, to: StreakColors, progress: Float): StreakColors {
    val easedProgress = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    return StreakColors(
        light1 = lerp(from.light1, to.light1, easedProgress),
        light2 = lerp(from.light2, to.light2, easedProgress),
        deep1 = lerp(from.deep1, to.deep1, easedProgress),
        deep2 = lerp(from.deep2, to.deep2, easedProgress)
    )
}

private fun tierName(tier: TierPalette): String = when (tier) {
    TierPalette.BRONZE -> "I"
    TierPalette.SILVER -> "II"
    TierPalette.GOLD -> "III"
    TierPalette.PURPLE -> "IV"
    TierPalette.DEEP_GOLD -> "V"
    TierPalette.ORANGE -> "VI"
    TierPalette.RED -> "VII"
}

private fun tierGlowColor(streak: Int): Color = tierAt(streak).light1

private fun tierProgress(streak: Int): Float {
    val (start, end) = when (tierAt(streak)) {
        TierPalette.BRONZE -> 0 to 7
        TierPalette.SILVER -> 7 to 21
        TierPalette.GOLD -> 21 to 60
        TierPalette.PURPLE -> 60 to 120
        TierPalette.DEEP_GOLD -> 120 to 250
        TierPalette.ORANGE -> 250 to 365
        TierPalette.RED -> 365 to 465
    }
    return ((streak - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

enum class BalloonState { NORMAL, BURSTING, POPPED }

private data class BurstParticle(
    val angle: Float,
    val maxDistanceDp: Float,
    val color: Color,
    val radiusDp: Float
)

private data class TierConfettiParticle(
    val angle: Float,
    val maxDistanceDp: Float,
    val radiusDp: Float,
    val colorIndex: Int
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

    val context = LocalContext.current

    LaunchedEffect(manualTxnViewModel) {
        manualTxnViewModel.toastEvent.collect { event ->
            val message = when (event) {
                TransactionToastEvent.Success -> "Transaction saved"
                is TransactionToastEvent.Failure -> "Failed: ${event.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pendingViewModel) {
        pendingViewModel.toastEvent.collect { event ->
            val message = when (event) {
                TransactionToastEvent.Success -> "Transaction saved"
                is TransactionToastEvent.Failure -> "Failed: ${event.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ Show confirmation sheet whenever a transaction is pending
        if (pendingState.isVisible) {
            TransactionConfirmationSheet(
                state            = pendingState,
                onAmountChange   = pendingViewModel::updateAmount,
                onMerchantChange = pendingViewModel::updateMerchant,
                onCategoryChange = pendingViewModel::updateCategory,
                suggestTransactionDetails = pendingViewModel::suggestTransactionDetails,
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
            suggestTransactionDetails = manualTxnViewModel::suggestTransactionDetails,
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
        ProfileSheet(
            onDismiss = { showProfile = false },
            personality = insightsState?.spendingPersonality ?: "Forming"
        )
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
                    insightsState = insightsState,
                    onNavigateToInsights = { selectedDestination = TetherNav.Insights },
                    onNavigateToVault = { selectedDestination = TetherNav.Vault },
                    onDebugSetStreak = if (BuildConfig.DEBUG) { value -> viewModel.debugSetStreak(value) } else null
                )
                is TetherNav.Insights -> InsightsScreen(
                    innerPadding = innerPadding,
                    insightsState = insightsState,
                    spendTrendValues = spendTrendValues,
                    trendLabels = trendLabels,
                    uiState = uiState,
                    onRefresh = { insightsViewModel.refresh() },
                    avatarId = userAvatarId
                )
                is TetherNav.Settings -> SettingsScreen(innerPadding = innerPadding)
                is TetherNav.Vault    -> VaultScreen(innerPadding = innerPadding)
                is TetherNav.Tips     -> PlaceholderScreen("Tips", innerPadding)
                is TetherNav.Growth   -> GrowthPlaceholderScreen(innerPadding = innerPadding)
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
    insightsState: com.anantva.tether.ui_elements.screens.InsightsUiState? = null,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onDebugSetStreak: ((Int) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            val usagePercent = if (uiState.dailyLimit > 0)
                    uiState.dailySpent.toFloat() / uiState.dailyLimit else 0f
            DashboardTopBar(
                onOpenProfile = onOpenProfile,
                userName = userName,
                userAvatarId = userAvatarId,
                usagePercent = usagePercent
            )
        }

        item { Spacer(Modifier.height(16.dp)) }

        item { BalloonSection(uiState = uiState, insightsState = insightsState, onDebugSetStreak = onDebugSetStreak) }

        item { Spacer(Modifier.height(20.dp)) }

        item { CoreStatsRow(uiState = uiState, onNavigateToInsights = onNavigateToInsights, onNavigateToVault = onNavigateToVault) }

        item { Spacer(Modifier.height(18.dp)) }

        item { GoalProgressCard(uiState = uiState) }

        item { PendingSection(pendingTransactions = pendingTransactions, onSeeAll = onSeeAllPending) }

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
private fun DashboardTopBar(
    onOpenProfile: () -> Unit,
    userName: String = "there",
    userAvatarId: String = "chill_cat",
    usagePercent: Float = 0f
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

        FinancialAuraAvatar(
            avatarId = userAvatarId,
            size = 42.dp,
            usagePercent = usagePercent,
            modifier = Modifier.clickable(onClick = onOpenProfile)
        )
    }
}

@Composable
private fun CoreStatsRow(
    uiState: DashboardUiState,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToVault: () -> Unit = {}
) {
    val safeToSpend = uiState.dailyLimitRemaining.coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onNavigateToInsights)
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
                .clickable(onClick = onNavigateToVault)
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Total Spent", fontSize = 12.sp, color = GrimeGrey, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(text = formatCurrency(uiState.dailySpent), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BalloonSection(
    uiState: DashboardUiState,
    insightsState: InsightsUiState? = null,
    onDebugSetStreak: ((Int) -> Unit)? = null
) {
    var balloonState by remember {
        mutableStateOf(if (uiState.streakDays == 0) BalloonState.POPPED else BalloonState.NORMAL)
    }
    val burstScale        = remember { Animatable(1f) }
    val burstAlpha        = remember { Animatable(1f) }
    val particleProgress  = remember { Animatable(0f) }
    val shakeOffset       = remember { Animatable(0f) }
    var showMilestone by remember { mutableStateOf(false) }
    var milestoneDays by remember { mutableIntStateOf(0) }
    val milestoneGlow = remember { Animatable(0f) }

    LaunchedEffect(uiState.isOverLimit, uiState.streakDays) {
        val shouldShowEmpty = uiState.isOverLimit || uiState.streakDays == 0

        when {
            uiState.streakMilestoneReached > 0 -> {
                milestoneDays = uiState.streakMilestoneReached
                showMilestone = true
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

    LaunchedEffect(showMilestone) {
        if (showMilestone) {
            milestoneGlow.snapTo(0f)
            milestoneGlow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
            delay(3400)
            milestoneGlow.animateTo(0f, tween(600))
            showMilestone = false
        }
    }

    val streakPct     = (uiState.streakDays.toFloat() / STREAK_CAP).coerceIn(0f, 1f)
    val balloonSizeDp = 100f + tierAt(uiState.streakDays).ordinal * 10f
    val countFontSize = (44f + 16f * streakPct).sp
    val labelFontSize = (8f + 2f * streakPct).sp
    val feedbackMessage = insightsState?.dailyInsightMessage?.takeIf { it.isNotBlank() } ?: run {
        when {
            uiState.streakDays == 0 && uiState.isOverLimit -> "The streak slipped today."
            uiState.dailySpent == 0 && uiState.streakDays > 0 -> "Quiet day. Your streak appreciates it."
            uiState.dailySpent == 0 -> "No unnecessary hits today. Nice."
            uiState.isOverLimit && uiState.streakDays > 7 -> "A messy day, not a broken run."
            uiState.isOverLimit -> "Today ran hot. Tomorrow can be cleaner."
            else -> "Still behaving."
        }
    }

    val isBrokenStreak = uiState.streakDays == 0 && uiState.isOverLimit
    val floatPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            floatPhase.floatValue += 0.016f
        }
    }
    val floatOffset = 12f * sin(floatPhase.floatValue * 2.513f) * (if (isBrokenStreak) 0.5f else 1f)
    val webAlpha = 0.15f + 0.07f * sin(floatPhase.floatValue * 3.491f) * if (isBrokenStreak) 0.6f else 1f

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OrbView(
            balloonState = balloonState,
            shakeOffset = shakeOffset.value,
            webAlpha = webAlpha,
            uiState = uiState,
            balloonSizeDp = balloonSizeDp,
            countFontSize = countFontSize,
            labelFontSize = labelFontSize,
            floatOffset = floatOffset,
            burstScale = burstScale.value,
            burstAlpha = burstAlpha.value,
            particleProgress = particleProgress.value,
            burstParticles = burstParticles,
            milestoneGlow = milestoneGlow.value,
            streakDays = uiState.streakDays
        )

        OrbMilestoneText(
            showMilestone = showMilestone,
            milestoneDays = milestoneDays
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = feedbackMessage,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (uiState.isOverLimit) TetherRed else GrimeGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (BuildConfig.DEBUG) {
            OrbDebugControls(
                onDelta = { delta -> onDebugSetStreak?.invoke(uiState.streakDays + delta) }
            )
        }
    }
}

@Composable
private fun OrbView(
    balloonState: BalloonState,
    shakeOffset: Float,
    webAlpha: Float,
    uiState: DashboardUiState,
    balloonSizeDp: Float,
    countFontSize: androidx.compose.ui.unit.TextUnit,
    labelFontSize: androidx.compose.ui.unit.TextUnit,
    floatOffset: Float,
    burstScale: Float,
    burstAlpha: Float,
    particleProgress: Float,
    burstParticles: List<BurstParticle>,
    milestoneGlow: Float,
    streakDays: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = shakeOffset.dp),
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
                    scale = if (balloonState == BalloonState.BURSTING) burstScale else 1f,
                    alpha = if (balloonState == BalloonState.BURSTING) burstAlpha else 1f
                )
            }

            if (balloonState == BalloonState.BURSTING) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    burstParticles.forEach { particle ->
                        val distance = particle.maxDistanceDp.dp.toPx() * particleProgress
                        val px = center.x + cos(particle.angle) * distance
                        val py = center.y + sin(particle.angle) * distance
                        val pAlpha = (1f - particleProgress).coerceAtLeast(0f)
                        val pRadius = (particle.radiusDp.dp.toPx() * (1f - particleProgress * 0.4f)).coerceAtLeast(1f)
                        drawCircle(
                            color = particle.color.copy(alpha = pAlpha),
                            radius = pRadius,
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        OrbMilestoneGlow(
            glowValue = milestoneGlow,
            streakDays = streakDays
        )
    }
}

@Composable
private fun OrbMilestoneGlow(glowValue: Float, streakDays: Int) {
    if (glowValue > 0.01f) {
        val glowColor = tierGlowColor(streakDays.coerceAtLeast(1))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.7f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.3f * glowValue),
                        glowColor.copy(alpha = 0.05f * glowValue),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }
    }
}

@Composable
private fun OrbMilestoneText(showMilestone: Boolean, milestoneDays: Int) {
    AnimatedVisibility(
        visible = showMilestone,
        enter = fadeIn(animationSpec = tween(350)) +
            scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)),
        exit = fadeOut(animationSpec = tween(500))
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$milestoneDays-Day Streak!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when (milestoneDays) {
                    3 -> Color(0xFFCD7F32)
                    7 -> Color(0xFFC0C0C0)
                    14 -> Color(0xFFFFD700)
                    else -> Color(0xFFE5E4E2)
                }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (milestoneDays) {
                    3 -> "You're getting the hang of this."
                    7 -> "A full week. Momentum is real."
                    14 -> "Two weeks strong. Discipline = identity."
                    else -> "A month of control. You're elite."
                },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OrbDebugControls(onDelta: (Int) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape)
                .combinedClickable(
                    onClick = { onDelta(-1) },
                    onLongClick = { onDelta(-10) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("\u2212", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Light)
        }
        Text("debug", fontSize = 9.sp, color = Color.White.copy(alpha = 0.2f), fontWeight = FontWeight.Light)
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape)
                .combinedClickable(
                    onClick = { onDelta(1) },
                    onLongClick = { onDelta(10) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Light)
        }
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
    val actualTier = remember(streak) { tierAt(streak) }
    var displayedTier by remember { mutableStateOf(actualTier) }
    var transitionStartTier by remember { mutableStateOf(actualTier) }
    var transitionTargetTier by remember { mutableStateOf(actualTier) }
    val tierTransitionProgress = remember { Animatable(1f) }
    val confettiProgress = remember { Animatable(1f) }
    val animatedBallSize by animateDpAsState(
        targetValue = ballSizeDp.dp,
        animationSpec = tween(720, easing = FastOutSlowInEasing),
        label = "tier_orb_size"
    )
    val orbGlowTransition = rememberInfiniteTransition(label = "orb_glow")
    val glowPulse by orbGlowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_glow_pulse"
    )
    val tierEnergy = tierProgress(streak)

    LaunchedEffect(actualTier) {
        if (actualTier != transitionTargetTier) {
            transitionStartTier = transitionTargetTier
            transitionTargetTier = actualTier
            val colorJob = launch {
                tierTransitionProgress.snapTo(0f)
                tierTransitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(TIER_TRANSITION_DURATION_MS, easing = FastOutSlowInEasing)
                )
            }
            launch {
                confettiProgress.snapTo(0f)
                confettiProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing)
                )
            }
            colorJob.join()
            displayedTier = actualTier
        }
    }

    val streakColors = lerpStreakColors(
        from = transitionStartTier.toStreakColors(),
        to = transitionTargetTier.toStreakColors(),
        progress = tierTransitionProgress.value
    )
    val displayedTierName = tierName(displayedTier)
    val animatedBallSizeDp = animatedBallSize.value
    val glowAlpha = 0.24f + tierEnergy * 0.10f + glowPulse * 0.04f
    val glowRadiusMultiplier = 0.58f + tierEnergy * 0.07f + glowPulse * 0.04f
    val confettiParticles = remember {
        List(16) { index ->
            TierConfettiParticle(
                angle = ((index * 23f + 8f) * PI / 180f).toFloat(),
                maxDistanceDp = 26f + (index * 11 % 34).toFloat(),
                radiusDp = 1.8f + (index % 3) * 0.7f,
                colorIndex = index
            )
        }
    }
    
    val density = LocalDensity.current
    val center = with(density) { Offset((animatedBallSizeDp * 0.3f).dp.toPx(), (animatedBallSizeDp * 0.3f).dp.toPx()) }
    val radius = with(density) { (animatedBallSizeDp * 0.5f).dp.toPx() }

    Box(
        modifier = Modifier
            .offset(y = floatOffset.dp)
            .requiredSize(animatedBallSize)
            .scale(scale)
            .alpha(alpha)
    ) {
        // Outer glow layer
        Canvas(modifier = Modifier.matchParentSize()) {
            val glowCenter = this.center
            val glowRadius = size.minDimension * glowRadiusMultiplier
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(streakColors.light1.copy(alpha = glowAlpha), Color.Transparent),
                    center = glowCenter,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = glowCenter
            )
        }
        
        // Main orb with fluid gradient
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            streakColors.light1.copy(alpha = 0.95f),
                            lerp(streakColors.light1, streakColors.light2, 0.10f),
                            lerp(streakColors.light1, streakColors.light2, 0.20f),
                            lerp(streakColors.light1, streakColors.light2, 0.30f),
                            lerp(streakColors.light1, streakColors.light2, 0.40f),
                            lerp(streakColors.light1, streakColors.light2, 0.50f),
                            streakColors.light2,
                            lerp(streakColors.light2, streakColors.deep1, 0.10f),
                            lerp(streakColors.light2, streakColors.deep1, 0.20f),
                            lerp(streakColors.light2, streakColors.deep1, 0.30f),
                            lerp(streakColors.light2, streakColors.deep1, 0.40f),
                            lerp(streakColors.light2, streakColors.deep1, 0.50f),
                            lerp(streakColors.light2, streakColors.deep1, 0.60f),
                            lerp(streakColors.light2, streakColors.deep1, 0.70f),
                            lerp(streakColors.light2, streakColors.deep1, 0.80f),
                            lerp(streakColors.light2, streakColors.deep1, 0.90f),
                            streakColors.deep1,
                            lerp(streakColors.deep1, streakColors.deep2, 0.12f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.25f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.38f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.50f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.62f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.75f),
                            lerp(streakColors.deep1, streakColors.deep2, 0.88f),
                            streakColors.deep2.copy(alpha = 0.88f)
                        ),
                        center = center,
                        radius = radius
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = streak.toString(),
                fontSize = countFontSize,
                fontWeight = FontWeight.Black,
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.anantva.tether.ui.theme.Figtree,
                    lineHeight = countFontSize,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = "Day Streak",
                fontSize = labelFontSize,
                color = Color.White.copy(alpha = 0.8f),
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.anantva.tether.ui.theme.Figtree,
                    lineHeight = labelFontSize,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                    )
                )
            )
            
            Spacer(modifier = Modifier.height(3.dp))
            
            Crossfade(
                targetState = displayedTierName,
                animationSpec = tween(400, easing = LinearEasing),
                label = "tier_text_crossfade"
            ) { renderedTier ->
                Text(
                    text = renderedTier,
                    fontSize = (labelFontSize.value * 0.7f + 3f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.55f),
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = com.anantva.tether.ui.theme.Figtree,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
            }
        }

        if (confettiProgress.value < 1f) {
            val confettiColors = listOf(
                streakColors.light1,
                streakColors.light2,
                streakColors.deep1,
                Color.White.copy(alpha = 0.92f)
            )
            Canvas(modifier = Modifier.matchParentSize().scale(1.25f)) {
                val progress = confettiProgress.value
                val easedProgress = FastOutSlowInEasing.transform(progress)
                val origin = Offset(size.width / 2f, size.height / 2f)
                confettiParticles.forEach { particle ->
                    val distance = particle.maxDistanceDp.dp.toPx() * easedProgress
                    val fall = 10.dp.toPx() * progress * progress
                    val particleCenter = Offset(
                        x = origin.x + cos(particle.angle) * distance,
                        y = origin.y + sin(particle.angle) * distance + fall
                    )
                    drawCircle(
                        color = confettiColors[particle.colorIndex % confettiColors.size]
                            .copy(alpha = (1f - progress).coerceIn(0f, 1f) * 0.86f),
                        radius = particle.radiusDp.dp.toPx() * (1f - progress * 0.25f),
                        center = particleCenter
                    )
                }
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
    val driftPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16)
            driftPhase.floatValue += 0.016f
        }
    }
    val period = 1900 + index * 320
    val drift = 1.4f * sin(driftPhase.floatValue * (2000f / period))

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

@Composable
fun GrowthPlaceholderScreen(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF2A2A2A),
                            Color(0xFF141414)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TetherRed.copy(alpha = 0.15f))
                        .border(1.dp, TetherRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "PRO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = TetherRed,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.TrendingUp,
                    contentDescription = "Growth",
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Growth",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Coming Soon",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TetherRed
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Advanced financial intelligence is coming.",
                    fontSize = 14.sp,
                    color = GrimeGrey,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
