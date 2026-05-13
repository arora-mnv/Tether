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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
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

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.toastEvent.collect { event ->
            val message = when (event) {
                TransactionToastEvent.Success -> "Transaction saved"
                is TransactionToastEvent.Failure -> "Failed: ${event.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    var editing by remember { mutableStateOf<TransactionEntity?>(null) }

    if (editing != null) {
        TransactionEditSheet(
            title = "Edit Transaction",
            initialAmount = editing!!.amount,
            initialMerchant = editing!!.merchant,
            initialIsDebit = editing!!.type == "Expense",
            initialCategory = editing!!.category,
            initialIsRecurring = editing!!.typedCategory == com.anantva.tether.data.local.entity.TxnCategory.RECURRING,
            onDismiss = { editing = null },
            suggestCategory = viewModel::suggestCategory,
            onSave = { amount, merchant, isDebit, category, isRecurring ->
                val updated = editing!!.copy(
                    amount = amount,
                    merchant = merchant,
                    type = if (isDebit) "Expense" else "Credit",
                    category = category,
                    txnCategory = if (isRecurring) 
                        com.anantva.tether.data.local.entity.TxnCategory.RECURRING.toDbValue() 
                    else 
                        com.anantva.tether.data.local.entity.TxnCategory.NORMAL.toDbValue()
                )
                viewModel.updateTransaction(updated)
                editing = null
            },
            onDelete = {
                viewModel.deleteTransaction(editing!!.transactionId)
                editing = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Vault",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.filter == TransactionFilter.ALL,
                onClick = { viewModel.setFilter(TransactionFilter.ALL) },
                label = { Text("All") },
                modifier = Modifier.height(36.dp)
            )
            FilterChip(
                selected = uiState.filter == TransactionFilter.EXPENSE,
                onClick = { viewModel.setFilter(TransactionFilter.EXPENSE) },
                label = { Text("Expense") },
                modifier = Modifier.height(36.dp)
            )
            FilterChip(
                selected = uiState.filter == TransactionFilter.CREDIT,
                onClick = { viewModel.setFilter(TransactionFilter.CREDIT) },
                label = { Text("Credit") },
                modifier = Modifier.height(36.dp)
            )
            Spacer(Modifier.weight(1f))
            SortChip(
                value = uiState.sort,
                onValueChange = viewModel::setSort
            )
        }

        Spacer(Modifier.height(16.dp))

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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

@Composable
private fun SortChip(
    value: TransactionSort,
    onValueChange: (TransactionSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val sortLabel = when (value) {
        TransactionSort.DATE_DESC -> "Newest"
        TransactionSort.DATE_ASC -> "Oldest"
        TransactionSort.AMOUNT_DESC -> "Highest"
        TransactionSort.AMOUNT_ASC -> "Lowest"
    }

    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = !expanded },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(sortLabel)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                }
            },
            modifier = Modifier.height(36.dp)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Newest") },
                onClick = { onValueChange(TransactionSort.DATE_DESC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Oldest") },
                onClick = { onValueChange(TransactionSort.DATE_ASC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Highest") },
                onClick = { onValueChange(TransactionSort.AMOUNT_DESC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Lowest") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDate(transaction.date),
                    color = GrimeGrey,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCurrencyVault(transaction.amount),
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isDebit) "Expense" else "Credit",
                    color = GrimeGrey.copy(alpha = 0.7f),
                    fontSize = 10.sp
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
