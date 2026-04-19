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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    onToggleType:    () -> Unit,
    onConfirm:       () -> Unit,
    onDelete:        () -> Unit,
    onDismiss:       () -> Unit
) {
    // Local editable text state — synced to ViewModel on change
    var amountText   by remember(state.isVisible) { mutableStateOf(state.amount.toString()) }
    var merchantText by remember(state.isVisible) { mutableStateOf(state.merchant) }

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
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ───────────────────────────────────────────────
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

            // ── Type toggle ───────────────────────────────────────────
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

            Spacer(Modifier.height(24.dp))

            // ── Amount field ──────────────────────────────────────────
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

            // ── Merchant field ────────────────────────────────────────
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

            Spacer(Modifier.height(32.dp))

            // ── Save button + countdown ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Countdown pill
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
                    // Sweeping arc drawn via Canvas would be cleaner but
                    // CircularProgressIndicator achieves the same result simply
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

                // Save button
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

            // Delete button
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
