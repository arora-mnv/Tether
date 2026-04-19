package com.anantva.tether.ui_elements.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val CardBg = Color(0xFF1A1A1A)
private val DarkBg = Color(0xFF0F0F0F)
private val GrimeGrey = Color(0xFFA0A0A0)
private val TetherRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var email by remember(uiState.email) { mutableStateOf(uiState.email) }
    var phone by remember(uiState.phone) { mutableStateOf(uiState.phone) }

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
                    text = "Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GrimeGrey)
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cloud sync", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Enable sync (not implemented yet)", color = GrimeGrey, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = uiState.isCloudStorage, onCheckedChange = viewModel::setCloudStorage)
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { /* placeholder */ },
                enabled = uiState.isCloudStorage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TetherRed)
            ) {
                Text("Sync now", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.save(name.trim(), email.trim(), phone.trim())
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

