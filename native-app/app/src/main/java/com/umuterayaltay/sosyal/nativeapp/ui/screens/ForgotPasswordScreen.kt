package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ForgotPasswordUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ForgotPasswordViewModel

/**
 * "Şifremi unuttum" ekranı — LoginScreen/RegisterScreen'in AYNI görsel stiliyle
 * (OutlinedTextField, Button, Loading/Error gösterimi, marka rozeti). Gönderim
 * başarılı olunca (backend HER DURUMDA {"ok":true} döner — enumeration
 * koruması) e-postayı kontrol etmesi gerektiğini söyleyen bir onay durumuna
 * geçer. Uygulama-içi sıfırlama (deep link) YOK — kullanıcı e-postadaki linkle
 * mevcut web sayfasında ("/reset-password") şifresini sıfırlar, bu kapsam DIŞI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onNavigateBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Şifremi unuttum") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState is ForgotPasswordUiState.Success) {
                // ---- Onay durumu ----
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "E-postanı kontrol et",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "Gelen linke tıklayarak şifreni sıfırlayabilirsin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
                TextButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Girişe dön")
                }
            } else {
                // ---- E-posta formu ----
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Şifreni mi unuttun?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = "E-posta adresini gir, sana bir sıfırlama bağlantısı gönderelim",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )

                val formShake = remember { Animatable(0f) }
                var formErrorMessage by remember { mutableStateOf("") }
                if (uiState is ForgotPasswordUiState.Error) formErrorMessage = (uiState as ForgotPasswordUiState.Error).message
                LaunchedEffect(uiState) {
                    if (uiState is ForgotPasswordUiState.Error) {
                        formShake.animateTo(
                            targetValue = 0f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                0f at 0
                                -10f at 50
                                10f at 100
                                -6f at 150
                                6f at 200
                                -3f at 250
                                3f at 300
                                0f at 400
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = formShake.value.dp)
                        .padding(bottom = 12.dp),
                )

                AnimatedVisibility(visible = uiState is ForgotPasswordUiState.Error, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = formErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Button(
                    onClick = { viewModel.sendResetLink(email) },
                    enabled = uiState !is ForgotPasswordUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Crossfade(targetState = uiState is ForgotPasswordUiState.Loading, label = "forgotPasswordButton") { isLoading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(if (isLoading) "Gönderiliyor..." else "Sıfırlama bağlantısı gönder")
                        }
                    }
                }
            }
        }
    }
}
