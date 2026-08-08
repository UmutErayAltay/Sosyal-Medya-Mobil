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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.RegisterUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.RegisterViewModel

/**
 * "Kayıt Ol" ekranı — LoginScreen'in AYNI görsel stiliyle (OutlinedTextField,
 * Button, Loading/Error gösterimi), email+şifre+username 3 alanla. Başarı →
 * [onRegisterSuccess] (NavHost'ta onLoginSuccess'ile AYNI davranışı çağırır,
 * ROUTE_MAIN'e navigate+popUpTo). Kendi RegisterViewModel/RegisterUiState'i var
 * (LoginUiState'le KARIŞTIRILMASIN diye).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = viewModel(),
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is RegisterUiState.Success) {
            onRegisterSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kayıt Ol") },
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
            // LoginScreen'in AYNI marka rozeti — tutarlı karşılama kimliği.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hesap oluştur",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Sosyal'e katılmak için bilgilerini gir",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            )

            val formShake = remember { Animatable(0f) }
            var formErrorMessage by remember { mutableStateOf("") }
            if (uiState is RegisterUiState.Error) formErrorMessage = (uiState as RegisterUiState.Error).message
            LaunchedEffect(uiState) {
                if (uiState is RegisterUiState.Error) {
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

            Column(modifier = Modifier.offset(x = formShake.value.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Kullanıcı adı") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster",
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            AnimatedVisibility(visible = uiState is RegisterUiState.Error, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = formErrorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Button(
                onClick = { viewModel.register(email, password, username) },
                enabled = uiState !is RegisterUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Crossfade(targetState = uiState is RegisterUiState.Loading, label = "registerButton") { isLoading ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(if (isLoading) "Kayıt olunuyor..." else "Kayıt ol")
                    }
                }
            }
        }
    }
}
