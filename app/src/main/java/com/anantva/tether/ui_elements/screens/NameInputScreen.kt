package com.anantva.tether.ui_elements.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anantva.tether.ui.theme.CardBg
import com.anantva.tether.ui.theme.DarkBg
import com.anantva.tether.ui.theme.GrimeGrey
import com.anantva.tether.ui.theme.GrimeGreyDark
import com.anantva.tether.ui.theme.TetherRed
import com.anantva.tether.ui.theme.TetherWhite

@Composable
fun NameInputScreen(
    onNameSaved: () -> Unit
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "\uD83D\uDC4B",
                fontSize = 56.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "What should we call you?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TetherWhite
            )
            Text(
                text = "This will be shown on your dashboard",
                style = MaterialTheme.typography.bodyMedium,
                color = GrimeGrey,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = null },
                placeholder = { Text("Your name", color = GrimeGrey) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = GrimeGrey) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                isError = error != null,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TetherRed.copy(alpha = 0.5f),
                    unfocusedBorderColor = GrimeGreyDark.copy(alpha = 0.5f),
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    cursorColor = TetherRed,
                    errorBorderColor = MaterialTheme.colorScheme.error
                )
            )

            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val scale by animateFloatAsState(
                targetValue = if (isSaving) 0.96f else 1f,
                animationSpec = tween(100),
                label = "saveBtnScale"
            )

            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Please enter your name"
                        return@Button
                    }
                    if (name.length < 2) {
                        error = "Name must be at least 2 characters"
                        return@Button
                    }
                    error = null
                    isSaving = true
                    authViewModel.saveUserProfile(name.trim()) {
                        isSaving = false
                        onNameSaved()
                    }
                },
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TetherRed,
                    disabledContainerColor = GrimeGreyDark.copy(alpha = 0.5f)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TetherWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TetherWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Continue", color = TetherWhite)
            }
        }
    }
}
