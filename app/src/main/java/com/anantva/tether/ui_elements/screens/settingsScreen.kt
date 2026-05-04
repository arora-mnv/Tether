package com.anantva.tether.ui_elements.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.data.repository.SyncResult
import com.anantva.tether.ui_elements.screens.AuthScreen

private val DarkBg = Color(0xFF0F0F0F)
private val CardBg = Color(0xFF1A1A1A)
private val GrimeGrey = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val context = LocalContext.current

    var resetDialog by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }

    // Clear sync state when user leaves sync section
    LaunchedEffect(syncState) {
        if (syncState is SyncResult.Done || syncState is SyncResult.Error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncState()
        }
    }

    if (showAuth) {
        AuthScreen(
            forceReauth = true,
            onLoginSuccess = {
                showAuth = false
                viewModel.setCloudStorage(true)
            },
            onNameRequired = {
                showAuth = false
                viewModel.setCloudStorage(true)
            },
            onCancel = { showAuth = false }
        )
        return
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Reset all data") },
            text = { Text("This will delete all transactions and restart setup.") },
            confirmButton = {
                Button(onClick = {
                    resetDialog = false
                    viewModel.resetAllData {
                        val intent = Intent(context, com.anantva.tether.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(DarkBg)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        SettingsCard {
            OutlinedTextField(
                value = uiState.savingsGoal,
                onValueChange = viewModel::setSavingsGoal,
                label = { Text("Goal amount", color = GrimeGrey) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            OutlinedTextField(
                value = uiState.monthlyCommitment,
                onValueChange = viewModel::setMonthlyCommitment,
                label = { Text("Monthly commitment", color = GrimeGrey) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsCard {
            ToggleRow(
                title = "Already saved this month",
                subtitle = "Controls whether this month counts toward goal progress",
                checked = uiState.hasSavedCommitment,
                onCheckedChange = viewModel::setHasSavedCommitment
            )
        }

        SettingsCard {
            Column {
                val subtitleText = when (val state = syncState) {
                    is SyncResult.Syncing -> state.message
                    is SyncResult.Done -> state.message
                    is SyncResult.Error -> state.message
                    else -> "Enable cloud sync (requires login)"
                }
                ToggleRow(
                    title = "Cloud storage",
                    subtitle = subtitleText,
                    checked = uiState.isCloudStorage,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showAuth = true
                        } else {
                            viewModel.setCloudStorage(false)
                        }
                    }
                )
                // Show progress indicator during sync
                if (syncState is SyncResult.Syncing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Text(
                            text = (syncState as SyncResult.Syncing).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = GrimeGrey
                        )
                    }
                }
            }
        }

        SettingsCard {
            ToggleRow(
                title = "Process notifications",
                subtitle = "Ignore transaction notifications when off",
                checked = uiState.notificationsEnabled,
                onCheckedChange = viewModel::setNotificationsEnabled
            )
        }

        Button(
            onClick = { resetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Reset data", fontWeight = FontWeight.Bold)
        }

        TextButton(
            onClick = {
                val wasCloudOn = uiState.isCloudStorage
                viewModel.logout()
                if (wasCloudOn) showAuth = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout", color = Color.White)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = GrimeGrey, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
