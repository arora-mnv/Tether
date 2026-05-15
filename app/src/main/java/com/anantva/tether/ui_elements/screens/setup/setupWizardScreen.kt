package com.anantva.tether.ui_elements.screens.setup

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.sp
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.anantva.tether.ui_elements.screens.AuthScreen
import com.anantva.tether.ui.theme.GrimeGrey
import com.anantva.tether.ui.theme.VintageCream
import java.text.SimpleDateFormat
import java.util.*

private val TetherRed = Color(0xFFE53935)
private val CardBg    = Color(0xFF1A1A1A)
private val DarkBg    = Color(0xFF0F0F0F)

// ─────────────────────────────────────────────
// Permission definitions
// ─────────────────────────────────────────────

internal data class RuntimePermission(
    val permission: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val minSdk: Int = 0       // 0 = always required
)

internal fun buildRuntimePermissions(): List<RuntimePermission> = buildList {
    // Notifications — API 33+ only
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(RuntimePermission(
            permission  = android.Manifest.permission.POST_NOTIFICATIONS,
            label       = "Notifications",
            description = "Show instant one-tap logging cards the moment a transaction is detected.",
            icon        = Icons.Outlined.Notifications
        ))
    }
    // Gallery / Media — API 33+ uses READ_MEDIA_IMAGES, older uses READ_EXTERNAL_STORAGE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(RuntimePermission(
            permission  = android.Manifest.permission.READ_MEDIA_IMAGES,
            label       = "Gallery Access",
            description = "Share payment screenshots with Tether so ML Kit can extract the transaction details automatically.",
            icon        = Icons.Outlined.Image
        ))
    } else {
        add(RuntimePermission(
            permission  = android.Manifest.permission.READ_EXTERNAL_STORAGE,
            label       = "Gallery Access",
            description = "Share payment screenshots with Tether so ML Kit can extract the transaction details automatically.",
            icon        = Icons.Outlined.Image
        ))
    }
    // Contacts
    add(RuntimePermission(
        permission  = android.Manifest.permission.READ_CONTACTS,
        label       = "Contacts",
        description = "Recognise and tag who you paid or received money from when logging a transaction.",
        icon        = Icons.Outlined.Contacts
    ))
}

// ─────────────────────────────────────────────
// Notification Listener check
// ─────────────────────────────────────────────

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.contains(context.packageName)
}

// ─────────────────────────────────────────────
// Setup Wizard Screen
// ─────────────────────────────────────────────

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val currentStep     by viewModel.currentStep.collectAsState()
    val name            by viewModel.userName.collectAsState()
    val balance         by viewModel.currentBalance.collectAsState()
    val goal            by viewModel.savingsGoal.collectAsState()
    val commitment by viewModel.monthlyCommitment.collectAsState()
    val hasSavedCommitment by viewModel.hasSavedCommitment.collectAsState()
    val isCloud         by viewModel.isCloudStorage.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val setupComplete   by viewModel.setupComplete.collectAsState()

    LaunchedEffect(setupComplete) {
        if (setupComplete) onSetupComplete()
    }

    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current  // ✅ Add this

    val runtimePermissions = remember { buildRuntimePermissions() }
    val grantedPermissions  = remember { mutableStateMapOf<String, Boolean>() }
    val allRuntimeGranted   by remember(runtimePermissions) {
        derivedStateOf { runtimePermissions.all { grantedPermissions[it.permission] == true } }
    }

    var notificationListenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }
    var termsAccepted by remember { mutableStateOf(false) }

    val allPermissionsReady = allRuntimeGranted && notificationListenerEnabled

    fun refreshRuntimePermissionState() {
        runtimePermissions.forEach { perm ->
            val granted = ContextCompat.checkSelfPermission(context, perm.permission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            grantedPermissions[perm.permission] = granted
        }
    }

    // Initialize runtime permission state on first load
    LaunchedEffect(runtimePermissions) {
        refreshRuntimePermissionState()
    }

    // ✅ Fix 1: Re-check notification listener every time the app comes back to foreground
    // This catches the user returning from Settings after enabling it
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationListenerEnabled = isNotificationListenerEnabled(context)
                refreshRuntimePermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) -> grantedPermissions[perm] = granted }
        // Re-check notification listener when returning from any permission dialog
        notificationListenerEnabled = isNotificationListenerEnabled(context)
    }

    val totalSteps = 7f
    val visualStep = currentStep.toFloat()

    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            phase.floatValue += 0.016f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        if (currentStep < 7) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(56.dp)) {
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE53935).copy(alpha = 0.15f * (0.5f + 0.5f * kotlin.math.sin(phase.floatValue * 1.2f))),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                            radius = 32f
                        ),
                        radius = 32f
                    )
                }
                val avatarProgress = (visualStep / totalSteps).coerceIn(0f, 1f)
                val sizeMul = (0.7f + avatarProgress * 0.3f)
                OnboardingIdentityOrb(
                    orbSize = (42 * sizeMul).dp,
                    progress = avatarProgress
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Spacer(modifier = Modifier.height(64.dp))
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(400)) { it } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(400)) { -it } + fadeOut()
                } else {
                    slideInHorizontally(animationSpec = tween(400)) { -it } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(400)) { it } + fadeOut()
                }
            }, label = "SetupAnimation"
        ) { step ->
            when (step) {
                1 -> StepStorageChoice(
                    isCloudSelected = isCloud,
                    onChoiceSelected = viewModel::setStoragePreference,
                    termsAccepted = termsAccepted,
                    onTermsChanged = { termsAccepted = it }
                )
                2 -> {
                    if (isCloud) {
                        StepAuth {
                            viewModel.setAuthenticated(true)
                            viewModel.nextStep()
                        }
                    } else {
                        StepInputCard(
                            title = "Your Name",
                            subtitle = "What should we call you?",
                            value = name,
                            onValueChange = viewModel::updateUserName,
                            prefix = "",
                            keyboardType = KeyboardType.Text,
                            maxLen = 32
                        )
                    }
                }
                3 -> StepInputCard("Current Balance", "How much do you currently have?", balance, viewModel::updateBalance, "₹")
                4 -> StepInputCard("Savings Goal", "How much do you want to save?", goal, viewModel::updateSavingsGoal, "₹")
                5 -> StepMonthlyCommitment(
                    savingsGoal        = goal.toDoubleOrNull() ?: 0.0,
                    monthlyCommitment  = commitment,
                    hasSavedCommitment = hasSavedCommitment,
                    onHasSavedChange   = viewModel::setHasSavedCommitment,
                    onCommitmentChange = viewModel::updateMonthlyCommitment
                )
                6 -> StepPermissions(
                    runtimePermissions          = runtimePermissions,
                    grantedPermissions          = grantedPermissions,
                    notificationListenerEnabled = notificationListenerEnabled,
                    onOpenNotificationSettings  = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
                7 -> StepActivation(onContinue = { viewModel.nextStep() })
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 1) {
                TextButton(onClick = viewModel::previousStep) {
                    Text("Back", color = Color.Gray)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            val buttonEnabled = if (currentStep == 7) true
                else isStepValid(currentStep, name, balance, goal, commitment, isAuthenticated, notificationListenerEnabled, termsAccepted)
            Button(
                onClick = {
                    when (currentStep) {
                        6 -> {
                            when {
                                allPermissionsReady -> viewModel.nextStep()
                                !allRuntimeGranted -> {
                                    val toRequest = runtimePermissions
                                        .filter { grantedPermissions[it.permission] != true }
                                        .map { it.permission }
                                        .toTypedArray()
                                    permissionLauncher.launch(toRequest)
                                }
                                !notificationListenerEnabled -> {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    )
                                }
                            }
                        }
                        else -> viewModel.nextStep()
                    }
                },
                colors  = ButtonDefaults.buttonColors(containerColor = TetherRed),
                shape   = RoundedCornerShape(12.dp),
                enabled = buttonEnabled
            ) {
                Text(
                    when {
                        currentStep == 7                -> "Enter Tether"
                        currentStep == 6 && allPermissionsReady -> "Continue"
                        currentStep == 6                -> "Grant \u0026 Continue"
                        else                            -> "Next"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
// ─────────────────────────────────────────────
// Step composables
// ─────────────────────────────────────────────

@Composable
fun StepInputCard(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String,
    keyboardType: KeyboardType = KeyboardType.Number,
    maxLen: Int = 8
) {
    val focusRequester = remember { FocusRequester() }
    // Avoid focus requests racing with AnimatedContent transitions (keyboard jank).
    LaunchedEffect(title, keyboardType) {
        withFrameNanos { /* wait one frame */ }
        focusRequester.requestFocus()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = VintageCream)
        Spacer(modifier = Modifier.height(40.dp))
        OutlinedTextField(
            value           = value,
            onValueChange   = { if (it.length <= maxLen) onValueChange(it) },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            textStyle       = MaterialTheme.typography.displayLarge.copy(color = Color.White),
            singleLine      = true,
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor          = TetherRed
            ),
            prefix   = {
                Text(
                    text  = prefix,
                    style = MaterialTheme.typography.displayLarge.copy(color = Color.Gray)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepDatePicker(targetDateMillis: Long?, onDateSelected: (Long?) -> Unit) {
    var showDatePicker  by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = targetDateMillis)
    val formatter       = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val dateText        = if (targetDateMillis != null) formatter.format(Date(targetDateMillis)) else "Select Date"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Target Date", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "By when do you want to save this money?",
            style = MaterialTheme.typography.bodyMedium,
            color = VintageCream
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedCard(
            onClick  = { showDatePicker = true },
            colors   = CardDefaults.outlinedCardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth().height(80.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text  = dateText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = if (targetDateMillis != null) Color.White else Color.Gray
                    )
                )
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        onDateSelected(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    }) { Text("OK", color = TetherRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

@Composable
fun StepStorageChoice(
    isCloudSelected: Boolean,
    onChoiceSelected: (Boolean) -> Unit,
    termsAccepted: Boolean = false,
    onTermsChanged: ((Boolean) -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Put your data in the cloud?", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        StorageCard("Local Only", "Your data stays only on this device.", !isCloudSelected) { onChoiceSelected(false) }
        Spacer(modifier = Modifier.height(14.dp))
        StorageCard("Cloud Sync", "Sync your financial identity across devices.", isCloudSelected) { onChoiceSelected(true) }

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x12FFFFFF))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = if (isCloudSelected) "Your encrypted data syncs securely across your devices."
                          else "All data stays on this device.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tether stores your financial activity securely and never sells personal financial data.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { onTermsChanged?.invoke(it) },
                colors = CheckboxDefaults.colors(checkedColor = TetherRed, uncheckedColor = GrimeGrey)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "I agree to the ",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "Terms",
                fontSize = 13.sp,
                color = TetherRed,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = " & ",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "Privacy Policy",
                fontSize = 13.sp,
                color = TetherRed,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = ".", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun StorageCard(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) TetherRed.copy(alpha = 0.2f) else CardBg
        ),
        border   = if (isSelected)
            BorderStroke(1.dp, SolidColor(TetherRed))
        else
            BorderStroke(1.dp, SolidColor(Color(0xFF2A2A2A)))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) TetherRed else Color.White
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = VintageCream)
        }
    }
}

@Composable
fun StepAuth(onAuthSuccess: () -> Unit) {
    AuthScreen(
        forceReauth = true,
        onLoginSuccess = onAuthSuccess,
        onNameRequired = onAuthSuccess
    )
}

// ─────────────────────────────────────────────
// Permissions Step
// ─────────────────────────────────────────────

@Composable
internal fun StepPermissions(
    runtimePermissions: List<RuntimePermission>,
    grantedPermissions: Map<String, Boolean>,
    notificationListenerEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text       = "One Last Step",
            style      = MaterialTheme.typography.titleLarge,
            color      = Color.White,
            modifier   = Modifier.fillMaxWidth(),
            textAlign  = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text      = "These power Tether's core features.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = Color.Gray,
            modifier  = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Special access card (Notification Listener) ──────────────────
        SpecialPermissionCard(
            icon        = Icons.Outlined.NotificationsActive,
            label       = "Notification Access",
            description = "The core Interceptor Engine. Reads payment notifications from GPay, PhonePe, and any banking app to auto-detect transactions in real time.",
            isGranted   = notificationListenerEnabled,
            badgeLabel  = if (notificationListenerEnabled) "Enabled" else "Required — Tap to Enable",
            onClick     = onOpenNotificationSettings
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Runtime permission cards ──────────────────────────────────────
        runtimePermissions.forEach { perm ->
            RuntimePermissionCard(
                permission = perm,
                isGranted  = grantedPermissions[perm.permission] == true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Privacy reassurance note
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector       = Icons.Filled.Shield,
                contentDescription = null,
                tint              = TetherRed,
                modifier          = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text  = "All processing happens on-device. Tether never sends your financial data to any server.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepMonthlyCommitment(
    savingsGoal:        Double,
    monthlyCommitment:  Double,
    hasSavedCommitment: Boolean,
    onHasSavedChange:   (Boolean) -> Unit,
    onCommitmentChange: (Double) -> Unit
) {
    // Slider range: ₹500 → ₹10,000 in steps of ₹500
    val minCommitment = 500f
    val maxCommitment = 10_000f
    val steps         = ((maxCommitment - minCommitment) / 500f).toInt() - 1

    // Editable amount text
    var amountText by remember { mutableStateOf(monthlyCommitment.toInt().toString()) }

    // Sync text when slider changes externally
    LaunchedEffect(monthlyCommitment) {
        amountText = monthlyCommitment.toInt().toString()
    }

    // Live projection
    val monthsToGoal = if (monthlyCommitment > 0 && savingsGoal > 0) {
        (savingsGoal / monthlyCommitment).toInt()
    } else null

    val projectionText = when {
        monthsToGoal == null    -> "Set a commitment to see your projection"
        monthsToGoal <= 0       -> "You're already there!"
        monthsToGoal == 1       -> "Goal reached in 1 month 🎉"
        monthsToGoal < 12       -> "Goal reached in $monthsToGoal months"
        else -> {
            val years  = monthsToGoal / 12
            val months = monthsToGoal % 12
            if (months == 0) "Goal reached in $years year${if (years > 1) "s" else ""}"
            else "Goal reached in $years year${if (years > 1) "s" else ""} $months month${if (months > 1) "s" else ""}"
        }
    }

    // Estimated completion date
    val completionDate = monthsToGoal?.let { months ->
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.MONTH, months)
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            .format(cal.time)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text  = "Monthly Commitment",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "How much can you set aside each month?",
            style = MaterialTheme.typography.bodyMedium,
            color = VintageCream
        )

        Spacer(Modifier.height(40.dp))

        // ── Editable commitment display ──────────────────────────────────
        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                if (input.length <= 6) {
                    amountText = input
                    input.toIntOrNull()?.let { value ->
                        val clamped = value.coerceIn(500, 10000)
                        onCommitmentChange(clamped.toDouble())
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.displayMedium.copy(color = Color.White),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TetherRed,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = TetherRed
            ),
            prefix = {
                Text(
                    text = "₹",
                    style = MaterialTheme.typography.displayMedium.copy(color = Color.Gray)
                )
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(Modifier.height(24.dp))

        // ── Slider ───────────────────────────────────────────────────
        Slider(
            value         = monthlyCommitment.toFloat().coerceIn(minCommitment, maxCommitment),
            onValueChange = { onCommitmentChange(it.toDouble()) },
            valueRange    = minCommitment..maxCommitment,
            steps         = steps,
            colors        = SliderDefaults.colors(
                thumbColor        = TetherRed,
                activeTrackColor  = TetherRed,
                inactiveTrackColor = Color(0xFF2A2A2A)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("₹500",    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("₹10,000", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(Modifier.height(32.dp))

        // ── Commitment confirmation ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Already saved this month?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "If yes, your goal progress starts from this month. If no, it starts next month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = hasSavedCommitment,
                onCheckedChange = onHasSavedChange
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Live projection card ──────────────────────────────────────
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.outlinedCardColors(containerColor = Color(0xFF1A1A1A)),
            border   = BorderStroke(
                1.dp,
                SolidColor(if (monthlyCommitment > 0) TetherRed.copy(alpha = 0.5f) else Color(0xFF2A2A2A))
            )
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text      = projectionText,
                    fontSize  = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color     = if (monthlyCommitment > 0) Color.White else Color.Gray,
                    textAlign = TextAlign.Center
                )
                completionDate?.let { date ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text      = "Estimated: $date",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = TetherRed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// Helper extension
fun Int.formatWithCommas(): String =
    String.format(java.util.Locale.US, "%,d", this)
@Composable
fun SpecialPermissionCard(
    icon: ImageVector,
    label: String,
    description: String,
    isGranted: Boolean,
    badgeLabel: String,
    onClick: () -> Unit
) {
    val borderColor = if (isGranted) TetherRed else Color(0xFFFF8F00) // amber when pending
    val bgColor     = if (isGranted) TetherRed.copy(alpha = 0.08f) else Color(0xFFFF8F00).copy(alpha = 0.06f)
    val iconTint    = if (isGranted) TetherRed else Color(0xFFFF8F00)
    val badgeBg     = if (isGranted) TetherRed else Color(0xFFFF8F00)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isGranted) onClick() },
        colors   = CardDefaults.outlinedCardColors(containerColor = bgColor),
        border   = BorderStroke(1.dp, SolidColor(borderColor))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconTint.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector       = icon,
                        contentDescription = null,
                        tint              = iconTint,
                        modifier          = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = label,
                        style      = MaterialTheme.typography.titleSmall,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Badge
                    Box(
                        modifier = Modifier
                            .background(badgeBg.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text     = badgeLabel,
                            fontSize = 10.sp,
                            color    = badgeBg,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(TetherRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector       = Icons.Filled.Check,
                            contentDescription = "Enabled",
                            tint              = Color.White,
                            modifier          = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector       = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Settings",
                        tint              = Color(0xFFFF8F00),
                        modifier          = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text     = description,
                style    = MaterialTheme.typography.bodySmall,
                color    = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
internal fun RuntimePermissionCard(
    permission: RuntimePermission,
    isGranted: Boolean
) {
    val borderColor = if (isGranted) TetherRed else Color(0xFF2A2A2A)
    val bgColor     = if (isGranted) TetherRed.copy(alpha = 0.08f) else CardBg

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.outlinedCardColors(containerColor = bgColor),
        border   = BorderStroke(1.dp, SolidColor(borderColor))
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isGranted) TetherRed.copy(alpha = 0.2f) else Color(0xFF2A2A2A),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector       = permission.icon,
                    contentDescription = null,
                    tint              = if (isGranted) TetherRed else Color.Gray,
                    modifier          = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = permission.label,
                    style      = MaterialTheme.typography.titleSmall,
                    color      = if (isGranted) TetherRed else Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text     = permission.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = Color.Gray,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(TetherRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector       = Icons.Filled.Check,
                        contentDescription = "Granted",
                        tint              = Color.White,
                        modifier          = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Onboarding Identity Orb
// ─────────────────────────────────────────────

private val OrbRed = Color(0xFFE53935)
private val OrbCrimson = Color(0xFFB71C1C)
private val OrbOrange = Color(0xFFFF6B35)

@Composable
private fun OnboardingIdentityOrb(
    orbSize: Dp = 56.dp,
    progress: Float = 0f
) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            phase.floatValue += 0.016f
        }
    }

    Box(
        modifier = Modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(orbSize)) {
            val t = phase.floatValue
            val px = orbSize.toPx()
            val cx = px / 2f
            val cy = px / 2f
            val r = px / 2f

            val segments = 32
            val morph = 1f + (1f - progress) * 0.1f
            val path = Path().apply {
                for (i in 0..segments) {
                    val angle = (i.toFloat() / segments) * 2f * kotlin.math.PI.toFloat()
                    val wave = kotlin.math.sin(angle * 2.5f + t * 0.8f) * 0.06f * morph
                    val wave2 = kotlin.math.sin(angle * 4f - t * 0.5f) * 0.03f * morph
                    val radius = r * (0.78f + wave + wave2)
                    val x = cx + radius * kotlin.math.cos(angle)
                    val y = cy + radius * kotlin.math.sin(angle)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path,
                Brush.radialGradient(
                    colors = listOf(
                        OrbRed.copy(alpha = 0.95f),
                        OrbCrimson.copy(alpha = 0.7f),
                        OrbOrange.copy(alpha = 0.3f)
                    ),
                    center = androidx.compose.ui.geometry.Offset(cx - r * 0.15f, cy - r * 0.2f),
                    radius = r
                )
            )

            val specShift = t * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx - r * 0.3f, cy - r * 0.3f),
                    radius = r * 0.3f
                ),
                radius = r * 0.3f
            )

            val glowAlpha = 0.08f * (0.5f + 0.5f * kotlin.math.sin(t * 1.2f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbRed.copy(alpha = glowAlpha), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    radius = r * 1.4f
                ),
                radius = r * 1.4f
            )
            drawCircle(
                color = OrbRed.copy(alpha = 0.12f),
                radius = r * 1.05f,
                style = Stroke(width = 0.8f)
            )
        }
    }
}

// ─────────────────────────────────────────────
// Activation Screen
// ─────────────────────────────────────────────

@Composable
private fun StepActivation(onContinue: () -> Unit) {
    val actPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            actPhase.floatValue += 0.016f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                val t = actPhase.floatValue
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (i in 0..5) {
                    val orbitR = size.minDimension * (0.25f + 0.1f * (0.5f + 0.5f * kotlin.math.sin(t * 0.3f + i * 1.2f)))
                    val angle = t * 0.4f + i * 1.047f
                    val px = cx + orbitR * kotlin.math.cos(angle)
                    val py = cy + orbitR * kotlin.math.sin(angle)
                    drawCircle(
                        Color(0xFFE53935).copy(alpha = 0.06f * (0.5f + 0.5f * kotlin.math.sin(t * 0.5f + i * 0.7f))),
                        radius = 2.5f + 1.5f * (0.5f + 0.5f * kotlin.math.sin(t + i))
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE53935).copy(alpha = 0.1f),
                            Color(0xFFE53935).copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = size.minDimension * 0.35f
                    ),
                    radius = size.minDimension * 0.35f
                )
            }
            OnboardingIdentityOrb(orbSize = 56.dp, progress = 1f)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Your financial rhythm is ready.",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tether adapts to how you spend,\nsave, and move through money.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        FeaturePayoffCard(
            illustration = @Composable { PulseLineIllustration() },
            title = "Real-time tracking",
            subtitle = "Transactions are detected instantly.",
            delay = 0
        )
        Spacer(modifier = Modifier.height(10.dp))
        FeaturePayoffCard(
            illustration = @Composable { NeuralOrbIllustration() },
            title = "Adaptive intelligence",
            subtitle = "Tether learns your habits and evolves with your behavior.",
            delay = 1
        )
        Spacer(modifier = Modifier.height(10.dp))
        FeaturePayoffCard(
            illustration = @Composable { MomentumRingIllustration() },
            title = "Momentum system",
            subtitle = "Your streak, vibe, and personality react to your decisions.",
            delay = 2
        )
    }
}

@Composable
private fun FeaturePayoffCard(
    illustration: @Composable () -> Unit,
    title: String,
    subtitle: String,
    delay: Int
) {
    val visible = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((200 + delay * 150).toLong())
        visible.value = true
    }

    val cardPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            cardPhase.floatValue += 0.016f
        }
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(tween(400)) + slideInVertically(animationSpec = tween(400)) { 30 },
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x14FFFFFF))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = cardPhase.floatValue
                val sweepX = size.width * (0.3f + 0.4f * (0.5f + 0.5f * kotlin.math.sin(t * 0.4f)))
                drawCircle(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF453A).copy(alpha = 0.04f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(sweepX, size.height * 0.5f),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    illustration()
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// ── Canvas Illustrations ──

@Composable
private fun PulseLineIllustration() {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(16); phase.floatValue += 0.016f }
    }
    Canvas(modifier = Modifier.size(36.dp)) {
        val t = phase.floatValue
        val path = Path()
        val steps = 20
        for (i in 0..steps) {
            val x = (i.toFloat() / steps) * size.width
            val y = size.height / 2f + kotlin.math.sin((i.toFloat() / steps) * 6f * kotlin.math.PI.toFloat() + t * 2f) * size.height * 0.25f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFFFF453A).copy(alpha = 0.8f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        for (i in 0..2) {
            val px = ((t * 0.5f + i * 0.33f) % 1f) * size.width
            val py = size.height / 2f + kotlin.math.sin(((px / size.width) * 6f * kotlin.math.PI.toFloat()) + t * 2f) * size.height * 0.25f
            drawCircle(Color(0xFFFF453A).copy(alpha = 0.6f), 2f, androidx.compose.ui.geometry.Offset(px, py))
        }
    }
}

@Composable
private fun NeuralOrbIllustration() {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(16); phase.floatValue += 0.016f }
    }
    Canvas(modifier = Modifier.size(36.dp)) {
        val t = phase.floatValue
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.minDimension * 0.35f
        val nodes = listOf(
            androidx.compose.ui.geometry.Offset(cx, cy - r),
            androidx.compose.ui.geometry.Offset(cx + r, cy),
            androidx.compose.ui.geometry.Offset(cx, cy + r),
            androidx.compose.ui.geometry.Offset(cx - r, cy)
        )
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val alpha = 0.2f + 0.3f * (0.5f + 0.5f * kotlin.math.sin(t * 1.5f + i + j))
                drawLine(Color(0xFFFF453A).copy(alpha = alpha), nodes[i], nodes[j], strokeWidth = 0.8f)
            }
        }
        val pulseR = r * (0.5f + 0.2f * kotlin.math.sin(t * 1.2f))
        drawCircle(Color(0xFFFF453A).copy(alpha = 0.15f), pulseR, androidx.compose.ui.geometry.Offset(cx, cy))
        for (i in nodes.indices) {
            val drift = 2f * kotlin.math.sin(t * 1.8f + i * 2f)
            val pos = nodes[i] + androidx.compose.ui.geometry.Offset(drift, drift * 0.5f)
            drawCircle(Color(0xFFFF453A).copy(alpha = 0.6f), 2.5f, pos)
        }
        drawCircle(Color(0xFFFF453A).copy(alpha = 0.3f), 1.5f, androidx.compose.ui.geometry.Offset(cx, cy))
    }
}

@Composable
private fun MomentumRingIllustration() {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(16); phase.floatValue += 0.016f }
    }
    Canvas(modifier = Modifier.size(36.dp)) {
        val t = phase.floatValue
        val cx = size.width / 2f; val cy = size.height / 2f
        val r = size.minDimension * 0.38f
        for (layer in 0 until 2) {
            val sweep = 180f + 90f * (0.5f + 0.5f * kotlin.math.sin(t * 0.5f + layer))
            val startAngle = t * 60f + layer * 120f
            val path = Path()
            val steps = 12
            for (i in 0..steps) {
                val angle = ((startAngle + sweep * i / steps) * kotlin.math.PI.toFloat() / 180f)
                val radius = r + layer * 3f
                val x = cx + radius * kotlin.math.cos(angle)
                val y = cy + radius * kotlin.math.sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFFFF453A).copy(alpha = 0.25f - layer * 0.08f), style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        }
        val progress = 0.3f + 0.7f * (0.5f + 0.5f * kotlin.math.sin(t * 0.3f))
        val angle = t * 40f
        val dotR = r * 0.7f
        val dx = cx + dotR * kotlin.math.cos(angle)
        val dy = cy + dotR * kotlin.math.sin(angle)
        drawCircle(Color(0xFFFF453A).copy(alpha = 0.8f), 2f, androidx.compose.ui.geometry.Offset(dx, dy))
    }
}

// ─────────────────────────────────────────────
// Validation
// ─────────────────────────────────────────────

fun isStepValid(
    step: Int,
    name: String,
    balance: String,
    goal: String,
    commitment: Double,
    auth: Boolean,
    notificationListenerEnabled: Boolean = false,
    termsAccepted: Boolean = true
): Boolean {
    return when (step) {
        1    -> termsAccepted
        2    -> true
        3    -> balance.isNotBlank()
        4    -> goal.isNotBlank()
        5    -> commitment > 0.0
        6    -> notificationListenerEnabled
        7    -> true
        else -> false
    }
}
