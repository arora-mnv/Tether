package com.anantva.tether.ui_elements.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TetherRed = Color(0xFFE53935)
private val CardBg = Color(0xFF1A1A1A)
private val DarkBg = Color(0xFF0F0F0F)
private val GrimeGrey = Color(0xFFA0A0A0)
private val CreditGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    title: String,
    initialAmount: Double,
    initialMerchant: String,
    initialIsDebit: Boolean,
    onDismiss: () -> Unit,
    onSave: (amount: Double, merchant: String, isDebit: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount.takeIf { it > 0 }?.toString() ?: "") }
    var merchantText by remember { mutableStateOf(initialMerchant) }
    var isDebit by remember { mutableStateOf(initialIsDebit) }

    val accentColor = if (isDebit) TetherRed else CreditGreen
    val amountValue = amountText.toDoubleOrNull()
    val canSave = amountValue != null && amountValue > 0.0 && merchantText.trim().isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        dragHandle = {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GrimeGrey)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf(true to "Expense", false to "Credit").forEach { (value, label) ->
                    val selected = isDebit == value
                    val bg = when {
                        selected && value -> TetherRed
                        selected && !value -> CreditGreen
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { isDebit = value }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else GrimeGrey
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Amount", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                prefix = {
                    Text("₹", fontSize = 22.sp, color = accentColor, fontWeight = FontWeight.Bold)
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    cursorColor = accentColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            Text("Merchant", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    cursorColor = accentColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = {
                    val parsedAmount = amountValue ?: return@Button
                    onSave(parsedAmount, merchantText.trim(), isDebit)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(
                    text = "Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

