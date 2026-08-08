package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.auth.GoogleSignInHelper
import com.umuterayaltay.sosyal.nativeapp.viewmodel.AuthViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.LoginUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.PendingCredential
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var googleLoading by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    fun startGoogleSignIn() {
        coroutineScope.launch {
            googleLoading = true
            try {
                val idToken = GoogleSignInHelper.requestIdToken(context)
                viewModel.loginWithGoogle(idToken)
            } catch (e: NoCredentialException) {
                // Kullanıcı hesap seçmeden iptal etti - sessizce geri dön,
                // hata SPAM'lemesin (bkz. görev spesifikasyonu).
            } catch (e: GetCredentialException) {
                viewModel.onGoogleSignInFailed()
            } finally {
                googleLoading = false
            }
        }
    }

    Scaffold { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Marka karşılama alanı — logo görseli YOK (res/ altında yok), bir
            // ikon rozeti + wordmark ile "Sosyal" kimliği veriliyor. SADECE
            // görsel: 2FA/hata state makinesi aşağıda AYNEN korunuyor.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sosyal",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            val currentState = uiState
            if (currentState is LoginUiState.NeedsCode) {
                // ---- 2FA doğrulama kodu ekranı ----
                var code by remember { mutableStateOf("") }
                val codeShake = remember { Animatable(0f) }
                var codeErrorMessage by remember { mutableStateOf("") }
                if (uiState is LoginUiState.Error) codeErrorMessage = (uiState as LoginUiState.Error).message
                LaunchedEffect(uiState) {
                    if (uiState is LoginUiState.Error) {
                        codeShake.animateTo(
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

                Text(
                    text = "Authenticator uygulamandaki 6 haneli kodu gir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { new ->
                        if (new.length <= 6 && new.all { it.isDigit() }) code = new
                    },
                    label = { Text("6 haneli kod") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = codeShake.value.dp)
                        .padding(bottom = 8.dp),
                )

                AnimatedVisibility(visible = uiState is LoginUiState.Error, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = codeErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Button(
                    onClick = {
                        when (val pending = currentState.pending) {
                            is PendingCredential.EmailPassword ->
                                viewModel.submitLoginCode(pending.email, pending.password, code)
                            is PendingCredential.Google ->
                                viewModel.submitGoogleLoginCode(pending.idToken, code)
                        }
                    },
                    enabled = code.length == 6 && uiState !is LoginUiState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Crossfade(targetState = uiState is LoginUiState.Loading, label = "loginCodeButton") { isLoading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(if (isLoading) "Doğrulanıyor..." else "Doğrula")
                        }
                    }
                }

                TextButton(
                    onClick = { viewModel.resetState() },
                    enabled = uiState !is LoginUiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Geri dön")
                }
            } else {
                // ---- Normal e-posta/şifre formu ----
                Text(
                    text = "Devam etmek için giriş yapın",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )

                val formShake = remember { Animatable(0f) }
                var formErrorMessage by remember { mutableStateOf("") }
                if (uiState is LoginUiState.Error) formErrorMessage = (uiState as LoginUiState.Error).message
                LaunchedEffect(uiState) {
                    if (uiState is LoginUiState.Error) {
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

                TextButton(
                    onClick = onNavigateToForgotPassword,
                    enabled = uiState !is LoginUiState.Loading && !googleLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Şifremi unuttum")
                }

                AnimatedVisibility(visible = uiState is LoginUiState.Error, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = formErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Button(
                    onClick = { viewModel.login(email, password) },
                    enabled = uiState !is LoginUiState.Loading && !googleLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Crossfade(targetState = uiState is LoginUiState.Loading, label = "loginButton") { isLoading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(if (isLoading) "Giriş yapılıyor..." else "Giriş yap")
                        }
                    }
                }

                TextButton(
                    onClick = onNavigateToRegister,
                    enabled = uiState !is LoginUiState.Loading && !googleLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Hesabın yok mu? Kayıt ol")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "veya",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                OutlinedButton(
                    onClick = { startGoogleSignIn() },
                    enabled = !googleLoading && uiState !is LoginUiState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Crossfade(targetState = googleLoading, label = "googleButton") { isLoading ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text(if (isLoading) "Bağlanıyor..." else "Google ile devam et")
                        }
                    }
                }
            }
        }
    }
}
