package com.anantva.tether.ui_elements.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.anantva.tether.data.local.entity.SpendingCategories

private val TetherRed = Color(0xFFE53935)
private val CardBg = Color(0xFF1A1A1A)
private val DarkBg = Color(0xFF0F0F0F)
private val GrimeGrey = Color(0xFFA0A0A0)
private val CreditGreen = Color(0xFF43A047)

val CATEGORY_LIST = listOf(
    SpendingCategories.FOOD,
    SpendingCategories.TRANSPORT,
    SpendingCategories.SHOPPING,
    SpendingCategories.BILLS,
    SpendingCategories.ENTERTAINMENT,
    SpendingCategories.HEALTH,
    SpendingCategories.EDUCATION,
    SpendingCategories.RENT,
    SpendingCategories.EMI,
    SpendingCategories.SUBSCRIPTION,
    SpendingCategories.INVESTMENTS,
    SpendingCategories.OTHER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectorField(
    selectedCategory: String,
    accentColor: Color,
    onCategorySelected: (String) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember(showSheet) { mutableStateOf("") }
    val filteredCategories = remember(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            CATEGORY_LIST
        } else {
            CATEGORY_LIST.filter { it.contains(query, ignoreCase = true) }
        }
    }

    Box {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Open categories",
                    tint = GrimeGrey
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color(0xFF2A2A2A),
                cursorColor = accentColor
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showSheet = true }
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = CardBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Choose category",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text("Search categories", color = GrimeGrey)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) accentColor.copy(alpha = 0.18f) else DarkBg)
                                .border(
                                    width = if (isSelected) 1.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onCategorySelected(category)
                                    showSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else GrimeGrey,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditSheet(
    title: String,
    initialAmount: Double,
    initialMerchant: String,
    initialIsDebit: Boolean,
    initialCategory: String = SpendingCategories.OTHER,
    initialIsRecurring: Boolean = false,
    onDismiss: () -> Unit,
    suggestCategory: suspend (merchant: String, isDebit: Boolean) -> String,
    onSave: (amount: Double, merchant: String, isDebit: Boolean, category: String, isRecurring: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var amountText by remember { mutableStateOf(initialAmount.takeIf { it > 0 }?.toString() ?: "") }
    var merchantText by remember { mutableStateOf(initialMerchant) }
    var isDebit by remember { mutableStateOf(initialIsDebit) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var isRecurring by remember { mutableStateOf(initialIsRecurring) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var hasManualCategoryOverride by remember { mutableStateOf(false) }

    val accentColor = if (isDebit) TetherRed else CreditGreen
    val amountValue = amountText.toDoubleOrNull()
    val canSave = amountValue != null && amountValue > 0.0 && merchantText.trim().isNotEmpty()

    LaunchedEffect(merchantText, isDebit, hasManualCategoryOverride) {
        if (hasManualCategoryOverride) return@LaunchedEffect

        selectedCategory = if (merchantText.isBlank()) {
            if (isDebit) SpendingCategories.OTHER else SpendingCategories.INCOME
        } else {
            suggestCategory(merchantText.trim(), isDebit)
        }
    }

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

            Spacer(Modifier.height(14.dp))

            Text("Category", fontSize = 12.sp, color = GrimeGrey, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CategorySelectorField(
                selectedCategory = selectedCategory,
                accentColor = accentColor,
                onCategorySelected = { category ->
                    hasManualCategoryOverride = true
                    selectedCategory = category
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
                    onCheckedChange = { isRecurring = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = accentColor,
                        checkedTrackColor = accentColor.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(Modifier.height(26.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onDelete != null) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TetherRed)
                    ) {
                        Text(
                            text = "Delete",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Button(
                    onClick = {
                        val parsedAmount = amountValue ?: return@Button
                        onSave(parsedAmount, merchantText.trim(), isDebit, selectedCategory, isRecurring)
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .weight(if (onDelete != null) 1f else 1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CreditGreen)
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

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Transaction",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this transaction?",
                    color = GrimeGrey
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = TetherRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = GrimeGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            containerColor = CardBg
        )
    }
}
