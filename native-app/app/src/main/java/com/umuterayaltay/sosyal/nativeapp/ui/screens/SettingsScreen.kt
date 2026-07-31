package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.SettingsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.SettingsViewModel

/**
 * "Ayarlar" menü ekranı — üç basit navigasyon satırı (Profili Düzenle/Bildirim
 * Tercihleri/Yakın Arkadaşlar) + altta ayrı/vurgulu bir "Hesabı Deaktive Et"
 * satırı. Menünün kendisi state taşımaz (viewModel sadece deaktivasyon
 * diyalogu için var — bkz. SettingsViewModel).
 *
 * [onDeactivated] BAŞARILI deaktivasyon sonrası çağrılır — [onSessionExpired]
 * ile AYNI navigasyon hedefine (login ekranı) gider ama kavramsal olarak
 * farklıdır: kullanıcı burada BİLEREK çıkış yaptı, oturumu dışarıdan
 * geçersizleşmedi. [onLoggedOut] da AYNI hedefe gider (normal "Çıkış Yap") —
 * üçü de navigasyon davranışı olarak özdeş, kavramsal olarak farklı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToNotificationPreferences: () -> Unit,
    onNavigateToCloseFriends: () -> Unit,
    onNavigateToTwoFactor: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit,
    onNavigateToActiveSessions: () -> Unit,
    onDeactivated: () -> Unit,
    onLoggedOut: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val deactivating by viewModel.deactivating.collectAsState()
    val deactivateError by viewModel.deactivateError.collectAsState()
    val loggingOut by viewModel.loggingOut.collectAsState()
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Deactivated -> {
                    showDeactivateDialog = false
                    onDeactivated()
                }
                is SettingsEvent.LoggedOut -> onLoggedOut()
                is SettingsEvent.SessionExpired -> {
                    showDeactivateDialog = false
                    onSessionExpired()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
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
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader("Hesap")
            SettingsRow(
                icon = Icons.Filled.Edit,
                label = "Profili Düzenle",
                onClick = onNavigateToEditProfile,
            )
            SettingsRow(
                icon = Icons.Filled.Notifications,
                label = "Bildirim Tercihleri",
                onClick = onNavigateToNotificationPreferences,
            )
            SettingsRow(
                icon = Icons.Filled.Stars,
                label = "Yakın Arkadaşlar",
                onClick = onNavigateToCloseFriends,
            )

            SettingsSectionHeader("Gizlilik ve Güvenlik")
            SettingsRow(
                icon = Icons.Filled.Security,
                label = "Güvenlik (2FA)",
                onClick = onNavigateToTwoFactor,
            )
            SettingsRow(
                icon = Icons.Filled.Block,
                label = "Engellenen Kullanıcılar",
                onClick = onNavigateToBlockedUsers,
            )
            SettingsRow(
                icon = Icons.Filled.Smartphone,
                label = "Aktif Oturumlar",
                onClick = onNavigateToActiveSessions,
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))

            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = if (loggingOut) "Çıkış yapılıyor..." else "Çıkış Yap",
                labelWeight = FontWeight.SemiBold,
                onClick = { if (!loggingOut) viewModel.logout() },
            )
            SettingsRow(
                icon = Icons.Filled.PersonOff,
                label = "Hesabı Deaktive Et",
                tint = MaterialTheme.colorScheme.error,
                iconBackground = MaterialTheme.colorScheme.errorContainer,
                onClick = {
                    password = ""
                    viewModel.clearDeactivateError()
                    showDeactivateDialog = true
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { if (!deactivating) showDeactivateDialog = false },
            icon = { Icon(Icons.Filled.PersonOff, contentDescription = null) },
            title = { Text("Hesabı deaktive et") },
            text = {
                Column {
                    Text(
                        "Hesabın deaktive edilecek ve tekrar giriş yapana kadar başkaları " +
                            "tarafından görülemeyecek. Bu işlem geri alınabilir, tekrar giriş " +
                            "yaparak hesabını yeniden aktifleştirebilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Şifre") },
                        placeholder = { Text("Şifresiz hesaplarda boş bırakılabilir") },
                        singleLine = true,
                        enabled = !deactivating,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (deactivateError != null) {
                        Text(
                            text = deactivateError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (deactivating) {
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
                    onClick = { viewModel.deactivate(password) },
                    enabled = !deactivating,
                ) {
                    Text("Deaktive Et", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeactivateDialog = false },
                    enabled = !deactivating,
                ) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

/** Bölüm başlığı — SettingsScreen'in düz satır listesini "Hesap" / "Gizlilik
 * ve Güvenlik" gibi mantıksal gruplara ayırır (görsel cila, navigasyon/state
 * DEĞİŞMEDİ). */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    labelWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = labelWeight,
            color = tint,
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxWidth()
                .weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
