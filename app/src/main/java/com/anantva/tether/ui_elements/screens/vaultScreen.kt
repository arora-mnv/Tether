package com.anantva.tether.ui_elements.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.data.local.entity.TransactionEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF0F0F0F)
private val CardBg = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)
private val TetherRed = Color(0xFFE53935)
private val CreditGreen = Color(0xFF43A047)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    innerPadding: PaddingValues,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

    if (editing != null) {
        TransactionEditSheet(
            title = "Edit Transaction",
            initialAmount = editing!!.amount,
            initialMerchant = editing!!.merchant,
            initialIsDebit = editing!!.type == "Expense",
            onDismiss = { editing = null },
            onSave = { amount, merchant, isDebit ->
                val updated = editing!!.copy(
                    amount = amount,
                    merchant = merchant,
                    type = if (isDebit) "Expense" else "Credit"
                )
                viewModel.updateTransaction(updated)
                editing = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(DarkBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Vault",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.filter == TransactionFilter.ALL,
                    onClick = { viewModel.setFilter(TransactionFilter.ALL) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = uiState.filter == TransactionFilter.EXPENSE,
                    onClick = { viewModel.setFilter(TransactionFilter.EXPENSE) },
                    label = { Text("Expense") }
                )
                FilterChip(
                    selected = uiState.filter == TransactionFilter.CREDIT,
                    onClick = { viewModel.setFilter(TransactionFilter.CREDIT) },
                    label = { Text("Credit") }
                )
            }

            SortDropdown(
                value = uiState.sort,
                onValueChange = viewModel::setSort
            )
        }

        Spacer(Modifier.height(14.dp))

        if (uiState.transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No confirmed transactions yet.",
                    color = GrimeGrey,
                    fontSize = 14.sp
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(uiState.transactions, key = { it.transactionId }) { txn ->
                TransactionRow(
                    transaction = txn,
                    onClick = { editing = txn }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    value: TransactionSort,
    onValueChange: (TransactionSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = when (value) {
                TransactionSort.DATE_DESC -> "Date (newest)"
                TransactionSort.DATE_ASC -> "Date (oldest)"
                TransactionSort.AMOUNT_DESC -> "Amount (high)"
                TransactionSort.AMOUNT_ASC -> "Amount (low)"
            },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .size(width = 160.dp, height = 56.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Date (newest)") },
                onClick = { onValueChange(TransactionSort.DATE_DESC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Date (oldest)") },
                onClick = { onValueChange(TransactionSort.DATE_ASC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Amount (high)") },
                onClick = { onValueChange(TransactionSort.AMOUNT_DESC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Amount (low)") },
                onClick = { onValueChange(TransactionSort.AMOUNT_ASC); expanded = false }
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val isDebit = transaction.type == "Expense"
    val amountColor = if (isDebit) TetherRed else CreditGreen

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDate(transaction.date),
                    color = GrimeGrey,
                    fontSize = 12.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrencyVault(transaction.amount),
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isDebit) "Expense" else "Credit",
                    color = GrimeGrey,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatCurrencyVault(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

private fun formatDate(epochMillis: Long): String {
    val df = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
    return df.format(Date(epochMillis))
}
