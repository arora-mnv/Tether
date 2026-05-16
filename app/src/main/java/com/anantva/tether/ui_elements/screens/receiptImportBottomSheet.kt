package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anantva.tether.data.local.entity.SpendingCategories

private val TetherRed = Color(0xFFE53935)
private val CardBg = Color(0xFF1A1A1A)
private val DarkBg = Color(0xFF0F0F0F)
private val GrimeGrey = Color(0xFFA0A0A0)
private val CreditGreen = Color(0xFF43A047)
private val ScanBlue = Color(0xFF4FC3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptImportBottomSheet(
    state: ReceiptImportUiState,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onToggleType: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = if (state.isDebit) TetherRed else CreditGreen

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
        if (state.isProcessing) {
            LoadingContent()
        } else {
            FormContent(
                state = state,
                accentColor = accentColor,
                onAmountChange = onAmountChange,
                onMerchantChange = onMerchantChange,
                onCategoryChange = onCategoryChange,
                onToggleType = onToggleType,
                onConfirm = onConfirm,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")

    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanY"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .size(width = 200.dp, height = 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(width = 160.dp, height = 100.dp)) {
                val scanPos = size.height * scanY
                    drawRoundRect(
                        color = Color(0xFF2A2A2A),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, ScanBlue, Color.Transparent)
                    ),
                    start = Offset(0f, scanPos),
                    end = Offset(size.width, scanPos),
                    strokeWidth = 3f
                )
            }

            Text(
                text = "Scanning",
                fontSize = 13.sp,
                color = GrimeGrey,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Reading receipt\u2026",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = pulseAlpha),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Extracting transaction details",
            fontSize = 13.sp,
            color = GrimeGrey,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        ShimmerPlaceholder(widthFraction = 0.7f)
        Spacer(Modifier.height(8.dp))
        ShimmerPlaceholder(widthFraction = 0.5f)
        Spacer(Modifier.height(8.dp))
        ShimmerPlaceholder(widthFraction = 0.6f)
    }
}

@Composable
private fun ShimmerPlaceholder(widthFraction: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A).copy(alpha = alpha))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormContent(
    state: ReceiptImportUiState,
    accentColor: Color,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onToggleType: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember(state.detectedAmount, state.isVisible) {
        mutableStateOf(state.detectedAmount)
    }
    var merchantText by remember(state.detectedMerchant, state.isVisible) {
        mutableStateOf(state.detectedMerchant)
    }
    var selectedCategory by remember(state.isVisible) {
        mutableStateOf(
            state.detectedCategory.ifEmpty { SpendingCategories.OTHER }
        )
    }
    var hasManualCategoryOverride by remember {
        mutableStateOf(false)
    }

    val amountValue = amountText.toDoubleOrNull()
    val canSave = amountValue != null && amountValue > 0.0 && merchantText.trim().isNotEmpty()
    
    val isUncertain = state.confidence < 0.7f
    val uncertainColor = Color(0xFFFFD54F)

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
                text = if (state.confidence > 0) "Receipt Import" else "Import Transaction",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GrimeGrey)
            }
        }

        if (isUncertain) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Please verify imported details",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = uncertainColor
            )
        }

        if (state.detectedDate.isNotEmpty() || state.confidence > 0) {
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.detectedDate.isNotEmpty()) {
                    Text(
                        text = "Date: ${state.detectedDate}",
                        fontSize = 12.sp,
                        color = GrimeGrey
                    )
                }
                if (state.confidence > 0) {
                    val confPercent = (state.confidence * 100).toInt()
                    Text(
                        text = "Match: ${confPercent}%",
                        fontSize = 12.sp,
                        color = if (confPercent > 70) CreditGreen else GrimeGrey
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBg)
                .padding(4.dp)
        ) {
            listOf(true to "Expense", false to "Credit").forEach { (value, label) ->
                val selected = state.isDebit == value
                val bg = when {
                    selected && value -> TetherRed
                    selected && !value -> CreditGreen
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { if (!selected) onToggleType() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else GrimeGrey
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Amount", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = {
                amountText = it
                onAmountChange(it)
            },
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
                focusedBorderColor = if (isUncertain) uncertainColor else accentColor,
                unfocusedBorderColor = if (isUncertain) uncertainColor.copy(alpha = 0.5f) else Color(0xFF2A2A2A),
                cursorColor = accentColor
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text("Merchant", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = merchantText,
            onValueChange = {
                merchantText = it
                onMerchantChange(it)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isUncertain) uncertainColor else accentColor,
                unfocusedBorderColor = if (isUncertain) uncertainColor.copy(alpha = 0.5f) else Color(0xFF2A2A2A),
                cursorColor = accentColor
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(0.7f)) {
                CategorySelectorField(
                    selectedCategory = selectedCategory,
                    accentColor = accentColor,
                    onCategorySelected = { category ->
                        hasManualCategoryOverride = true
                        selectedCategory = category
                        onCategoryChange(category)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .weight(0.3f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkBg)
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Repeat", fontSize = 13.sp, color = GrimeGrey, fontWeight = FontWeight.Medium)
                Switch(
                    checked = false,
                    onCheckedChange = { },
                    modifier = Modifier.size(width = 40.dp, height = 24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = GrimeGrey,
                        uncheckedTrackColor = Color(0xFF2A2A2A),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text(
                text = "Save Transaction",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
