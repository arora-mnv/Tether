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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.data.local.entity.TransactionEntity
import com.anantva.tether.ui_elements.components.TetherBottomNavBar
import com.anantva.tether.ui.theme.VintageCream
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.anantva.tether.ui_elements.components.NavDestination as TetherNav

private val TetherRed = Color(0xFFE53935)
private val DarkBg    = Color(0xFF0F0F0F)
private val CardBg    = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)
private const val STREAK_CAP = 60

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
    pendingListViewModel: PendingTransactionsViewModel = hiltViewModel()
) {
    val uiState         by viewModel.uiState.collectAsState()
    val pendingState    by pendingViewModel.uiState.collectAsState()  // ✅ NEW
    val pendingListState by pendingListViewModel.uiState.collectAsState()
    var selectedDestination by remember { mutableStateOf<TetherNav>(TetherNav.Home) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showPendingList by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    // ✅ Show confirmation sheet whenever a transaction is pending
    if (pendingState.isVisible) {
        TransactionConfirmationSheet(
            state            = pendingState,
            onAmountChange   = pendingViewModel::updateAmount,
            onMerchantChange = pendingViewModel::updateMerchant,
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
            onDismiss = { showManualEntry = false },
            onSave = { amount, merchant, isDebit ->
                manualTxnViewModel.addManualTransaction(amount, merchant, isDebit)
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
                    onOpenProfile = { showProfile = true }
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
    onOpenProfile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onOpenProfile) {
                    Icon(
                        imageVector        = Icons.Filled.AccountCircle,
                        contentDescription = "Profile",
                        tint               = Color.White,
                        modifier           = Modifier.size(32.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { BalloonSection(uiState = uiState) }
        item { Spacer(Modifier.height(32.dp)) }
        item { DailyLimitDisplay(uiState = uiState) }
        item { Spacer(Modifier.height(18.dp)) }
        item { GoalProgressCard(uiState = uiState) }
        item { Spacer(Modifier.height(34.dp)) }

        item { PendingSection(pendingTransactions = pendingTransactions, onSeeAll = onSeeAllPending) }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun PendingSection(
    pendingTransactions: List<TransactionEntity>,
    onSeeAll: () -> Unit
) {
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
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No pending transactions",
                style = MaterialTheme.typography.bodyMedium,
                color = GrimeGrey
            )
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
private fun MiniPendingRow(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.merchant,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Tap to review",
                color = GrimeGrey,
                fontSize = 11.sp
            )
        }
        Text(
            text = formatCurrency(transaction.amount),
            color = TetherRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─────────────────────────────────────────────
// Placeholder for screens not yet built
// ─────────────────────────────────────────────

@Composable
fun PlaceholderScreen(title: String, innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(text = "Coming soon", style = MaterialTheme.typography.bodyMedium, color = GrimeGrey)
        }
    }
}

// ─────────────────────────────────────────────
// Balloon Section — unchanged from last version
// ─────────────────────────────────────────────

@Composable
fun BalloonSection(uiState: DashboardUiState) {
    var balloonState by remember { mutableStateOf(BalloonState.NORMAL) }
    val burstScale        = remember { Animatable(1f) }
    val burstAlpha        = remember { Animatable(1f) }
    val particleProgress  = remember { Animatable(0f) }

    LaunchedEffect(uiState.isOverLimit) {
        if (uiState.isOverLimit && balloonState == BalloonState.NORMAL) {
            balloonState = BalloonState.BURSTING
            coroutineScope {
                launch { burstScale.animateTo(1.45f, tween(200)) }
                launch { delay(100); burstAlpha.animateTo(0f, tween(230)) }
                launch { delay(60); particleProgress.animateTo(1f, tween(640, easing = FastOutSlowInEasing)) }
            }
            balloonState = BalloonState.POPPED
        }
    }

    val streakPct     = (uiState.streakDays.toFloat() / STREAK_CAP).coerceIn(0f, 1f)
    val balloonSizeDp = 120f + 140f * streakPct
    val countFontSize = (22f + 18f * streakPct).sp
    val labelFontSize = (9f + 5f * streakPct).sp

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

    val burstParticles = remember {
        List(12) { i ->
            val base   = (i * 30f * PI / 180f).toFloat()
            val jitter = ((i * 7 % 20) - 10f) * (PI / 180f).toFloat()
            BurstParticle(
                angle         = base + jitter,
                maxDistanceDp = 80f + (i * 11 % 55).toFloat(),
                color         = listOf(
                    Color(0xFFE53935), Color(0xFFFF5252), Color(0xFFB71C1C),
                    Color(0xFFFF8A80), Color(0xFFFFCDD2), Color(0xFFFF1744)
                )[i % 6],
                radiusDp = 3f + (i % 5).toFloat()
            )
        }
    }

    Box(
        modifier         = Modifier.fillMaxWidth().height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        when (balloonState) {
            BalloonState.NORMAL -> {
                Box(
                    modifier = Modifier
                        .offset(y = floatOffset.dp)
                        .size(balloonSizeDp.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF5252), TetherRed, Color(0xFFB71C1C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = uiState.streakDays.toString(),
                            fontSize   = countFontSize,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                        Text(
                            text     = "Day Streak",
                            fontSize = labelFontSize,
                            color    = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            BalloonState.BURSTING -> {
                val scale    = burstScale.value
                val alpha    = burstAlpha.value
                val progress = particleProgress.value

                Box(
                    modifier = Modifier
                        .size(balloonSizeDp.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF5252), TetherRed, Color(0xFFB71C1C))
                            )
                        )
                )

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

            BalloonState.POPPED -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        Text("💸", fontSize = 52.sp)
                        repeat(4) { BuzzingFly(index = it) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Budget blown!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TetherRed)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Over by ${formatCurrency((uiState.dailySpent - uiState.dailyLimit).coerceAtLeast(0))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GrimeGrey
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Streak reset. Start fresh tomorrow.", fontSize = 11.sp, color = Color(0xFF606060))
                }
            }
        }
    }
}

@Composable
fun BuzzingFly(index: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "fly_$index")
    val orbitRadius = 28f + index * 16f
    val orbitSpeed  = 1300 + index * 400
    val startPhase  = index * 90f

    val angle by infiniteTransition.animateFloat(
        initialValue  = startPhase,
        targetValue   = startPhase + 360f,
        animationSpec = infiniteRepeatable(tween(orbitSpeed, easing = LinearEasing)),
        label         = "orbit_$index"
    )
    val wobbleX by infiniteTransition.animateFloat(
        initialValue  = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation  = tween(120 + index * 40, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wx_$index"
    )
    val wobbleY by infiniteTransition.animateFloat(
        initialValue  = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(100 + index * 30, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "wy_$index"
    )

    val rad = angle * PI.toFloat() / 180f
    val x   = cos(rad) * orbitRadius + wobbleX
    val y   = sin(rad) * orbitRadius * 0.6f + wobbleY

    Box(
        modifier = Modifier
            .size(10.dp, 5.dp)
            .offset(x = x.dp, y = y.dp)
            .background(Color(0xFF777777), CircleShape)
    )
}

@Composable
fun DailyLimitDisplay(uiState: DashboardUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ── Daily limit number ────────────────────────────────────────
        if (uiState.isOverLimit) {
            Text(
                "Limit Exceeded",
                style = MaterialTheme.typography.bodyMedium,
                color = TetherRed
            )
            Text(
                text       = formatCurrency(uiState.dailySpent),
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = TetherRed
            )
            Text(
                "limit was ${formatCurrency(uiState.dailyLimit)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF606060)
            )
        } else {
            Text(
                "Remaining Today",
                style = MaterialTheme.typography.bodyMedium,
                color = GrimeGrey
            )
            Text(
                text       = formatCurrency(uiState.dailyLimitRemaining),
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Text(
                "of ${formatCurrency(uiState.dailyLimit)} daily limit",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF606060)
            )
        }

        // ── Goal projection pill ──────────────────────────────────────
        if (uiState.monthsToGoal > 0 && uiState.projectedCompletionDate.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text      = "Goal projection",
                        fontSize  = 11.sp,
                        color     = Color(0xFF606060),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("${formatCurrency(uiState.savingsGoal)} in ")
                            append("${uiState.monthsToGoal} month${if (uiState.monthsToGoal > 1) "s" else ""}")
                        },
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text      = uiState.projectedCompletionDate,
                        fontSize  = 12.sp,
                        color     = TetherRed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun GoalProgressCard(uiState: DashboardUiState) {
    if (uiState.savingsGoal <= 0.0 || uiState.monthlyCommitment <= 0.0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                text = "Goal progress",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { uiState.goalProgressPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = if (uiState.isGoalCompleted) VintageCream else TetherRed,
                trackColor = Color(0xFF2A2A2A)
            )

            Spacer(Modifier.height(10.dp))

            val pctText = (uiState.goalProgressPct * 100).toInt().coerceIn(0, 100)
            Text(
                text = "$pctText% complete",
                color = GrimeGrey,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (uiState.isGoalCompleted)
                    "Goal completed"
                else
                    "Remaining: ${formatCurrency(uiState.goalRemainingAmount)}",
                color = if (uiState.isGoalCompleted) VintageCream else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
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
