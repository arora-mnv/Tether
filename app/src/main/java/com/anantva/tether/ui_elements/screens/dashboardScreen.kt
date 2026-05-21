package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.ui_elements.components.SharedUserViewModel
import com.anantva.tether.ui_elements.components.TetherAvatar
import com.anantva.tether.ui_elements.components.UserUiState
import com.anantva.tether.ui_elements.components.TetherBottomNavBar
import com.anantva.tether.ui_elements.components.TetherOrb
import com.anantva.tether.ui_elements.components.OrbTier
import com.anantva.tether.ui_elements.components.OrbMomentumState
import com.anantva.tether.ui.theme.VintageCream
import com.google.firebase.BuildConfig
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import com.anantva.tether.ui_elements.components.NavDestination as TetherNav

private val TetherRed = Color(0xFFE53935)
private val SafeGreen = Color(0xFF7FAE65)
private val DarkBg    = Color(0xFF0F0F0F)
private val CardBg    = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel:        DashboardViewModel        = hiltViewModel(),
    pendingViewModel: PendingTransactionViewModel = hiltViewModel(),
    manualTxnViewModel: ManualTransactionViewModel = hiltViewModel(),
    pendingListViewModel: PendingTransactionsViewModel = hiltViewModel(),
    insightsViewModel: InsightsViewModel = hiltViewModel(),
    receiptImportViewModel: com.anantva.tether.ui_elements.screens.ReceiptImportViewModel = hiltViewModel(),
    sharedUserViewModel: com.anantva.tether.ui_elements.components.SharedUserViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsState()
    val pendingState    by pendingViewModel.uiState.collectAsState()
    val pendingListState by pendingListViewModel.uiState.collectAsState()
    val insightsState   by insightsViewModel.uiState.collectAsState()
    val spendTrendValues by insightsViewModel.spendTrendValues.collectAsState()
    val trendLabels = insightsViewModel.trendLabels
    val userUiState by sharedUserViewModel.uiState.collectAsStateWithLifecycle()
    var selectedDestination by remember { mutableStateOf<TetherNav>(TetherNav.Home) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showPendingList by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val receiptState by receiptImportViewModel.uiState.collectAsState()

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            receiptImportViewModel.onReceiptShared(uri, context.contentResolver)
        }
    }

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

    LaunchedEffect(receiptImportViewModel) {
        receiptImportViewModel.toastEvent.collect { event ->
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
            personality = insightsState?.spendingPersonality ?: "Forming",
            userUiState = userUiState
        )
    }

    if (showActionSheet) {
        com.anantva.tether.ui_elements.components.FabActionSheet(
            onDismiss = { showActionSheet = false },
            onActionSelected = { action ->
                showActionSheet = false
                when (action) {
                    com.anantva.tether.ui_elements.components.FabAction.AddTransaction -> showManualEntry = true
                    com.anantva.tether.ui_elements.components.FabAction.UploadReceipt -> galleryLauncher.launch("image/*")
                }
            }
        )
    }

    if (receiptState.isVisible) {
        ReceiptImportBottomSheet(
            state = receiptState,
            onAmountChange = receiptImportViewModel::updateAmount,
            onMerchantChange = receiptImportViewModel::updateMerchant,
            onCategoryChange = receiptImportViewModel::updateCategory,
            onToggleType = receiptImportViewModel::toggleType,
            onConfirm = receiptImportViewModel::confirm,
            onDismiss = receiptImportViewModel::dismiss
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
                    showActionSheet = true
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
                    userUiState = userUiState,
                    insightsState = insightsState,
                    onNavigateToInsights = { selectedDestination = TetherNav.Insights },
                    onNavigateToVault = { selectedDestination = TetherNav.Vault },
                    onMarkMonthSaved = viewModel::markCurrentMonthSaved,
                    onUndoCurrentMonth = viewModel::undoCurrentMonthContribution,
                    onDebugSetStreak = if (BuildConfig.DEBUG) { value -> viewModel.debugSetStreak(value) } else null
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
    userUiState: UserUiState = UserUiState(),
    insightsState: com.anantva.tether.ui_elements.screens.InsightsUiState? = null,
    onNavigateToInsights: () -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onMarkMonthSaved: () -> Unit = {},
    onUndoCurrentMonth: () -> Unit = {},
    onDebugSetStreak: ((Int) -> Unit)? = null
) {
    var momentumState by remember { mutableStateOf(OrbMomentumState.ALIVE) }
    var silenceActive by remember { mutableStateOf(false) }

    val deathSequence = updateTransition(momentumState, label = "death_seq")

    val vignetteIntensity by deathSequence.animateFloat(
        transitionSpec = {
            when {
                targetState == OrbMomentumState.DEAD -> tween(400, easing = FastOutSlowInEasing)
                targetState == OrbMomentumState.RECOVERING -> tween(600, easing = FastOutSlowInEasing)
                else -> snap()
            }
        },
        label = "vignette"
    ) { state: OrbMomentumState ->
        when (state) {
            OrbMomentumState.DEAD -> if (silenceActive) 0.55f else 0.30f
            else -> 0f
        }
    }

    LaunchedEffect(momentumState) {
        if (momentumState == OrbMomentumState.DEAD) {
            silenceActive = true
            delay(2000)
            silenceActive = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    userUiState = userUiState,
                    usagePercent = usagePercent
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                OrbSection(
                    uiState = uiState,
                    insightsState = insightsState,
                    onDebugSetStreak = onDebugSetStreak,
                    momentumState = momentumState,
                    onMomentumStateChange = { newState ->
                        momentumState = newState
                    }
                )
            }

            item { Spacer(Modifier.height(20.dp)) }

            item { CoreStatsRow(uiState = uiState, onNavigateToInsights = onNavigateToInsights, onNavigateToVault = onNavigateToVault) }

            item { Spacer(Modifier.height(18.dp)) }

            item {
                GoalProgressCard(
                    uiState = uiState,
                    onMarkMonthSaved = onMarkMonthSaved,
                    onUndoCurrentMonth = onUndoCurrentMonth
                )
            }

            item { PendingSection(pendingTransactions = pendingTransactions, onSeeAll = onSeeAllPending) }

            item { Spacer(Modifier.height(100.dp)) }
        }

        if (vignetteIntensity > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = vignetteIntensity * 0.35f))
            )
        }

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
    userUiState: UserUiState = UserUiState(),
    usagePercent: Float = 0f
) {
    val isLoggedIn = userUiState.isLoggedIn
    val firstName = userUiState.displayName
        .takeIf { it.isNotBlank() && it != "there" }
        ?.trim()
        ?.substringBefore(" ")
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still awake"
    }
    val tips = listOf(
        "Check the daily limit before your next spend",
        "Recurring bills stay out of streak damage",
        "Small wants add up fastest near month end",
        "Mark this month's saving once it actually moves"
    )
    val subtext = tips[LocalDate.now().dayOfYear % tips.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (firstName == null || !isLoggedIn) greeting else "$greeting, $firstName",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                color = GrimeGrey,
                fontSize = 13.sp
            )
        }

        TetherAvatar(
            userUiState = userUiState,
            size = 42.dp,
            isLoggedIn = isLoggedIn,
            onClick = onOpenProfile
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
fun OrbSection(
    uiState: DashboardUiState,
    insightsState: InsightsUiState? = null,
    onDebugSetStreak: ((Int) -> Unit)? = null,
    momentumState: OrbMomentumState = OrbMomentumState.ALIVE,
    onMomentumStateChange: (OrbMomentumState) -> Unit = {}
) {
    var showMilestone by remember { mutableStateOf(false) }
    var milestoneDays by remember { mutableIntStateOf(0) }
    val milestoneGlow = remember { androidx.compose.animation.core.Animatable(0f) }

    var prevBroken by remember { mutableStateOf(false) }

    val isBroken = uiState.streakDays == 0 && uiState.isOverLimit
    val isEmpty = uiState.streakDays == 0

    LaunchedEffect(uiState.streakMilestoneReached) {
        if (uiState.streakMilestoneReached > 0) {
            milestoneDays = uiState.streakMilestoneReached
            showMilestone = true
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

    LaunchedEffect(isBroken, isEmpty) {
        if (isBroken && !prevBroken && momentumState == OrbMomentumState.ALIVE) {
            onMomentumStateChange(OrbMomentumState.COLLAPSING)
        }
        if (!isEmpty && momentumState == OrbMomentumState.DEAD) {
            onMomentumStateChange(OrbMomentumState.RECOVERING)
        }
        prevBroken = isBroken
    }

    LaunchedEffect(momentumState) {
        if (momentumState == OrbMomentumState.RECOVERING) {
            delay(2200)
            onMomentumStateChange(OrbMomentumState.ALIVE)
        }
    }

    val stressLevel = if (uiState.dailyLimit > 0)
        (uiState.dailySpent.toFloat() / uiState.dailyLimit).coerceIn(0f, 1f) else 0f
    val orbSize = (100 + OrbTier.fromStreak(uiState.streakDays).ordinal * 10).dp

    val feedbackMessage = insightsState?.dailyInsightMessage?.takeIf { it.isNotBlank() } ?: run {
        when {
            uiState.streakDays == 0 && uiState.isOverLimit -> listOf(
                "The momentum collapsed.",
                "Today broke the rhythm.",
                "Everything went quiet."
            ).random()
            uiState.dailySpent == 0 && uiState.streakDays > 0 -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding.",
                "Today stayed under control."
            ).random()
            uiState.dailySpent == 0 -> "Today stayed under control."
            uiState.isOverLimit && uiState.streakDays > 7 -> listOf(
                "The streak is struggling.",
                "You're approaching the edge."
            ).random()
            uiState.isOverLimit -> listOf(
                "You're approaching the edge.",
                "There's almost nothing left."
            ).random()
            else -> listOf(
                "Momentum feels stable.",
                "The rhythm is holding."
            ).random()
        }
    }

    val deathMessage = remember { listOf(
        "The momentum collapsed.",
        "Today broke the rhythm.",
        "Everything went quiet."
    ).random() }

    val isDeathState = momentumState == OrbMomentumState.COLLAPSING || momentumState == OrbMomentumState.DEAD

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
        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            TetherOrb(
                stressLevel = stressLevel,
                streakDays = uiState.streakDays,
                size = orbSize,
                momentumState = momentumState,
                onCollapseComplete = {
                    if (momentumState == OrbMomentumState.COLLAPSING) {
                        onMomentumStateChange(OrbMomentumState.DEAD)
                    }
                }
            )

            OrbMilestoneGlow(
                glowValue = milestoneGlow.value,
                streakDays = uiState.streakDays
            )
        }

        OrbMilestoneText(
            showMilestone = showMilestone,
            milestoneDays = milestoneDays
        )

        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = if (isDeathState) 1 else 0,
            transitionSpec = {
                fadeIn(tween(300, delayMillis = if (targetState == 1) 350 else 0)) togetherWith fadeOut(tween(200))
            },
            label = "status_text"
        ) { showDeathText ->
            Text(
                text = if (showDeathText == 1) deathMessage else feedbackMessage,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (showDeathText == 1) GrimeGrey.copy(alpha = 0.65f)
                        else if (uiState.isOverLimit) TetherRed else GrimeGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        if (BuildConfig.DEBUG) {
            OrbDebugControls(
                onDelta = { delta -> onDebugSetStreak?.invoke(uiState.streakDays + delta) }
            )
        }
    }
}

@Composable
private fun OrbMilestoneGlow(glowValue: Float, streakDays: Int) {
    if (glowValue > 0.01f) {
        val glowColor = OrbTier.fromStreak(streakDays.coerceAtLeast(1)).let { t ->
            when (t) {
                OrbTier.BRONZE -> Color(0xFFFFB36B)
                OrbTier.SILVER -> Color(0xFFDCE6F2)
                OrbTier.GOLD -> Color(0xFFFFE27A)
                OrbTier.PURPLE -> Color(0xFFC084FF)
                OrbTier.DEEP_GOLD -> Color(0xFFFFD95A)
                OrbTier.ORANGE -> Color(0xFFFF9A4D)
                OrbTier.RED -> Color(0xFFFF6B6B)
            }
        }
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
                    imageVector = Icons.Filled.TrendingUp,
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
