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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

    // ✅ Fix 2: Auto-proceed the moment all permissions are ready
    // User should never need to tap the button again after granting everything
    LaunchedEffect(allPermissionsReady, currentStep) {
        if (currentStep == 7 && allPermissionsReady) {
            viewModel.nextStep()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        results.forEach { (perm, granted) -> grantedPermissions[perm] = granted }
        // Re-check notification listener when returning from any permission dialog
        notificationListenerEnabled = isNotificationListenerEnabled(context)
    }

    val totalSteps = if (isCloud) 7f else 6f
    val visualStep = if (!isCloud && currentStep == 7) 6f else currentStep.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        LinearProgressIndicator(
            progress   = { visualStep / totalSteps },
            modifier   = Modifier.fillMaxWidth().height(8.dp),
            color      = TetherRed,
            trackColor = GrimeGrey,
        )

        Spacer(modifier = Modifier.height(40.dp))

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
                1 -> StepInputCard(
                    title = "Your Name",
                    subtitle = "What should we call you?",
                    value = name,
                    onValueChange = viewModel::updateUserName,
                    prefix = "",
                    keyboardType = KeyboardType.Text,
                    maxLen = 32
                )
                2 -> StepInputCard("Current Balance", "How much do you currently have?", balance, viewModel::updateBalance, "₹")
                3 -> StepInputCard("Savings Goal", "How much do you want to save?", goal, viewModel::updateSavingsGoal, "₹")
                4 -> StepMonthlyCommitment(
                    savingsGoal        = goal.toDoubleOrNull() ?: 0.0,
                    monthlyCommitment  = commitment,
                    hasSavedCommitment = hasSavedCommitment,
                    onHasSavedChange   = viewModel::setHasSavedCommitment,
                    onCommitmentChange = viewModel::updateMonthlyCommitment
                )
                5 -> StepStorageChoice(isCloud, viewModel::setStoragePreference)
                6 -> StepAuth {
                    viewModel.setAuthenticated(true)
                    viewModel.nextStep()
                }
                7 -> StepPermissions(
                    runtimePermissions          = runtimePermissions,
                    grantedPermissions          = grantedPermissions,
                    notificationListenerEnabled = notificationListenerEnabled,
                    onOpenNotificationSettings  = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
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

            Button(
                onClick = {
                    if (currentStep == 7) {
                        when {
                            // ✅ Everything ready — viewModel.nextStep() will be called
                            // automatically by LaunchedEffect, but handle tap too
                            allPermissionsReady -> viewModel.nextStep()

                            // ✅ Runtime permissions still needed
                            !allRuntimeGranted -> {
                                val toRequest = runtimePermissions
                                    .filter { grantedPermissions[it.permission] != true }
                                    .map { it.permission }
                                    .toTypedArray()
                                permissionLauncher.launch(toRequest)
                            }

                            // ✅ Only notification listener missing
                            !notificationListenerEnabled -> {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            }
                        }
                    } else {
                        viewModel.nextStep()
                    }
                },
                colors  = ButtonDefaults.buttonColors(containerColor = TetherRed),
                shape   = RoundedCornerShape(12.dp),
                enabled = isStepValid(currentStep, name, balance, goal, commitment, isAuthenticated)
            ) {
                Text(
                    when {
                        currentStep < 7     -> "Next"
                        allPermissionsReady -> "Finish Setup"
                        else                -> "Grant & Continue"
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
fun StepStorageChoice(isCloudSelected: Boolean, onChoiceSelected: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(text = "Data Storage", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(40.dp))
        StorageCard("Local Only", "Data stays on your device.", !isCloudSelected) { onChoiceSelected(false) }
        Spacer(modifier = Modifier.height(16.dp))
        StorageCard("Cloud Sync", "Access data anywhere.", isCloudSelected) { onChoiceSelected(true) }
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
    String.format("%,d", this)
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
// Validation
// ─────────────────────────────────────────────

fun isStepValid(step: Int, name: String, balance: String, goal: String, commitment: Double, auth: Boolean): Boolean {
    return when (step) {
        1    -> name.isNotBlank()
        2    -> balance.isNotBlank()
        3    -> goal.isNotBlank()
        4    -> commitment > 0.0
        5    -> true
        6    -> auth
        7    -> true  // button itself handles permission logic
        else -> false
    }
}
