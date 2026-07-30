package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.EnrollStep
import com.umuterayaltay.sosyal.nativeapp.viewmodel.TwoFactorEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.TwoFactorViewModel

/**
 * "Güvenlik (2FA)" ekranı — SettingsScreen'in "Güvenlik (2FA)" satırından
 * erişilir. Açılınca GET /2fa/status ile mevcut durum yüklenir, sonra iki
 * basit hâl render eder: kapalıysa "Etkinleştir" (2 adımlı enroll diyalogu),
 * açıksa "Kapat" (şifre+kod'u AYNI anda isteyen tek adımlı disable diyalogu).
 * Tüm diyalog/hata/loading görsel dili SettingsScreen'in deaktivasyon
 * diyaloguyla AYNI (AlertDialog + OutlinedTextField + CircularProgressIndicator).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: TwoFactorViewModel = viewModel(),
) {
    val loading by viewModel.loading.collectAsState()
    val loadError by viewModel.loadError.collectAsState()
    val enabled by viewModel.enabled.collectAsState()

    val showEnrollDialog by viewModel.showEnrollDialog.collectAsState()
    val enrollStep by viewModel.enrollStep.collectAsState()
    val enrollBusy by viewModel.enrollBusy.collectAsState()
    val enrollError by viewModel.enrollError.collectAsState()

    val showDisableDialog by viewModel.showDisableDialog.collectAsState()
    val disableBusy by viewModel.disableBusy.collectAsState()
    val disableError by viewModel.disableError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TwoFactorEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Güvenlik (2FA)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            loadError != null && enabled == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError ?: "")
                    TextButton(onClick = viewModel::load) { Text("Tekrar dene") }
                }
            }

            enabled == true -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("İki adımlı doğrulama aktif", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hesabına giriş yaparken şifrenin yanında authenticator uygulamandaki " +
                        "6 haneli kodu da gireceksin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = viewModel::openDisableDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Kapat")
                }
            }

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Icon(
                    Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("İki adımlı doğrulama kapalı", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hesabını daha güvenli hale getirmek için giriş yaparken şifrenin " +
                        "yanında authenticator uygulamandaki (Google Authenticator, Authy vb.) " +
                        "bir kodu da isteyebiliriz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = viewModel::openEnrollDialog) {
                    Text("Etkinleştir")
                }
            }
        }
    }

    if (showEnrollDialog) {
        EnrollDialog(
            step = enrollStep,
            busy = enrollBusy,
            error = enrollError,
            onDismiss = viewModel::dismissEnrollDialog,
            onSubmitPassword = viewModel::submitPassword,
            onSubmitCode = viewModel::submitVerifyCode,
        )
    }

    if (showDisableDialog) {
        DisableDialog(
            busy = disableBusy,
            error = disableError,
            onDismiss = viewModel::dismissDisableDialog,
            onConfirm = viewModel::disable,
        )
    }
}

/** Enroll'un iki adımı (şifre -> kod) TEK diyalogda render edilir — [step]
 * değiştikçe içerik değişir, diyalog kapanmaz. [password] her iki adım arasında
 * bu composable'ın kendi local state'inde taşınır (backend verify çağrısı da
 * AYNI password'ü tekrar ister). */
@Composable
private fun EnrollDialog(
    step: EnrollStep,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmitPassword: (String) -> Unit,
    onSubmitCode: (password: String, code: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val isCodeStep = step is EnrollStep.AwaitingCode
    val canSubmit = if (isCodeStep) code.length == 6 else password.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(if (isCodeStep) "Kodu doğrula" else "İki adımlı doğrulamayı etkinleştir") },
        text = {
            Column {
                when (step) {
                    is EnrollStep.AwaitingPassword -> {
                        Text(
                            "Devam etmek için şifreni gir.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Şifre") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is EnrollStep.AwaitingCode -> {
                        Text(
                            "Authenticator uygulamana (Google Authenticator, Authy vb.) bu " +
                                "anahtarı manuel gir:",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SelectionContainer {
                            Text(
                                text = formatSecret(step.secret),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) code = new },
                            label = { Text("6 haneli kod") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (busy) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        is EnrollStep.AwaitingPassword -> onSubmitPassword(password)
                        is EnrollStep.AwaitingCode -> onSubmitCode(password, code)
                    }
                },
                enabled = !busy && canSubmit,
            ) {
                Text(if (isCodeStep) "Doğrula" else "Devam Et")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Vazgeç")
            }
        },
    )
}

/** Şifre VE kodu AYNI diyalogda, AYNI anda ister (backend'in ikisini birlikte
 * istemesi yüzünden — bkz. TwoFactorDisableRequest docstring'i). */
@Composable
private fun DisableDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String, code: String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
        title = { Text("İki adımlı doğrulamayı kapat") },
        text = {
            Column {
                Text(
                    "Kapatmak için şifreni ve authenticator uygulamandaki güncel 6 haneli " +
                        "kodu birlikte gir.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) code = new },
                    label = { Text("6 haneli kod") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (busy) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, code) },
                enabled = !busy && password.isNotBlank() && code.length == 6,
            ) {
                Text("Kapat", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Vazgeç")
            }
        },
    )
}

/** "CEGTOKC7CAZLDTCHOWUPRBO3QWZXUIIF" -> "CEGT OKC7 CAZL DTCH OWUP RBO3 QWZX
 * UIIF" — SADECE görsel boşluk, backend'e gönderilirken KULLANILMAZ (zaten
 * factor_id/code ile devam ediliyor, secret bir daha hiç gönderilmiyor). */
private fun formatSecret(secret: String): String = secret.chunked(4).joinToString(" ")
