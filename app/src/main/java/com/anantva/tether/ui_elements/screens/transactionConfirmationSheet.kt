package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.data.local.entity.SpendingCategories

private val TetherRed  = Color(0xFFE53935)
private val CardBg     = Color(0xFF1A1A1A)
private val DarkBg     = Color(0xFF0F0F0F)
private val GrimeGrey  = Color(0xFFA0A0A0)
private val CreditGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionConfirmationSheet(
    state:           PendingTransactionUiState,
    onAmountChange:  (Double) -> Unit,
    onMerchantChange:(String) -> Unit,
    onCategoryChange:(String) -> Unit,
    suggestCategory: suspend (merchant: String, isDebit: Boolean) -> String,
    onToggleRecurring: () -> Unit,
    onToggleType:    () -> Unit,
    onConfirm:       () -> Unit,
    onDelete:        () -> Unit,
    onDismiss:       () -> Unit
) {
    var amountText   by remember(state.isVisible) { mutableStateOf(state.amount.toString()) }
    var merchantText by remember(state.isVisible) { mutableStateOf(state.merchant) }
    var selectedCategory by remember(state.isVisible) { mutableStateOf(state.category) }
    var isRecurring by remember(state.isVisible) { mutableStateOf(state.isRecurring) }
    var hasManualCategoryOverride by remember(state.isVisible) { mutableStateOf(false) }

    // Sync recurring toggle to parent state
    LaunchedEffect(isRecurring) { onToggleRecurring() }

    LaunchedEffect(merchantText, state.isDebit, hasManualCategoryOverride, state.isVisible) {
        if (!state.isVisible || hasManualCategoryOverride) return@LaunchedEffect

        val suggestedCategory = if (merchantText.isBlank()) {
            if (state.isDebit) SpendingCategories.OTHER else SpendingCategories.INCOME
        } else {
            suggestCategory(merchantText.trim(), state.isDebit)
        }
        selectedCategory = suggestedCategory
        onCategoryChange(suggestedCategory)
    }

    // Countdown arc animation
    val countdownFraction = state.countdown / 15f
    val sweepAngle by animateFloatAsState(
        targetValue   = 360f * countdownFraction,
        animationSpec = tween(900, easing = LinearEasing),
        label         = "countdown_arc"
    )

    val accentColor = if (state.isDebit) TetherRed else CreditGreen

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = CardBg,
        dragHandle        = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3A))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Transaction Detected",
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = GrimeGrey)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text  = "Review and correct if needed",
                style = MaterialTheme.typography.bodySmall,
                color = GrimeGrey
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(true to "Debit", false to "Credit").forEach { (isDebit, label) ->
                    val isSelected = state.isDebit == isDebit
                    val bgColor    = when {
                        isSelected && isDebit  -> TetherRed
                        isSelected && !isDebit -> CreditGreen
                        else                   -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .clickable { if (!isSelected) onToggleType() }
                            .padding(horizontal = 28.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = label,
                            fontSize   = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color      = if (isSelected) Color.White else GrimeGrey
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Amount", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = amountText,
                onValueChange = {
                    amountText = it
                    it.toDoubleOrNull()?.let { d -> onAmountChange(d) }
                },
                prefix = {
                    Text("₹", fontSize = 22.sp, color = accentColor, fontWeight = FontWeight.Bold)
                },
                textStyle     = LocalTextStyle.current.copy(
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    cursorColor          = accentColor
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text("Merchant", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = merchantText,
                onValueChange = {
                    merchantText = it
                    onMerchantChange(it)
                },
                textStyle = LocalTextStyle.current.copy(
                    fontSize  = 18.sp,
                    color     = Color.White
                ),
                singleLine = true,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accentColor,
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    cursorColor          = accentColor
                ),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text("Category", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CategorySelectorField(
                selectedCategory = selectedCategory,
                accentColor = accentColor,
                onCategorySelected = { category ->
                    hasManualCategoryOverride = true
                    selectedCategory = category
                    onCategoryChange(category)
                }
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mark as Recurring", fontSize = 14.sp, color = Color.White)
                Switch(
                    checked = isRecurring,
                    onCheckedChange = {
                        isRecurring = it
                        onToggleRecurring()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(Modifier.height(32.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Box(
                    modifier         = Modifier
                        .size(48.dp)
                        .border(
                            width  = 2.dp,
                            brush  = SolidColor(accentColor.copy(alpha = 0.4f)),
                            shape  = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress      = { countdownFraction },
                        modifier      = Modifier.size(44.dp),
                        color         = accentColor,
                        trackColor    = Color(0xFF2A2A2A),
                        strokeWidth   = 3.dp
                    )
                    Text(
                        text      = state.countdown.toString(),
                        fontSize  = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color     = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.width(16.dp))

                Button(
                    onClick  = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(
                        text       = "Save Transaction",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete Transaction",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TetherRed
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text      = "Hiding in ${state.countdown}s (stays pending until you save)",
                fontSize  = 11.sp,
                color     = GrimeGrey,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }
    }
}
