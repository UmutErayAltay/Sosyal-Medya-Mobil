package com.umuterayaltay.sosyal.nativeapp.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.network.SessionDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ActiveSessionsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ActiveSessionsViewModel

/**
 * "Aktif Oturumlar" ekranı — hesaba giriş yapmış BAŞKA NATIVE CİHAZLARIN
 * listesi (web'in "Aktif Oturumlar" — user_sessions/tarayıcı — özelliğinin
 * KAVRAMSAL karşılığı ama FARKLI bir mekanizma: native'in kendi bearer token
 * sistemi, api_tokens, üzerinde çalışır — bkz. ApiModels.kt SessionDto notu).
 *
 * Görsel dil BlockedUsersScreen/FollowListScreen'in AYNI liste + CenteredMessage
 * deseni. `is_current` olan satırda "Bu cihaz" rozeti gösterilir ve revoke
 * butonu HİÇ render EDİLMEZ (backend zaten 400 use_logout döner, ama baştan
 * göstermemek daha temiz — kendi oturumunu kapatmak için normal "Çıkış Yap"
 * kullanılmalı). Listenin altında "Diğer Tüm Oturumları Sonlandır" — SettingsScreen'in
 * deaktivasyon AlertDialog'u İLE AYNI görsel dil (icon + açıklama + confirm/dismiss).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionsScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: ActiveSessionsViewModel = viewModel(),
) {
    val sessions by viewModel.sessions.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val revokingId by viewModel.revokingId.collectAsState()
    val revokingOthers by viewModel.revokingOthers.collectAsState()
    val revokeOthersError by viewModel.revokeOthersError.collectAsState()

    var showRevokeOthersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActiveSessionsEvent.SessionExpired -> onSessionExpired()
                is ActiveSessionsEvent.OthersRevoked -> showRevokeOthersDialog = false
            }
        }
    }

    val hasOtherSessions = sessions.any { !it.isCurrent }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktif Oturumlar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && sessions.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }

            error != null && sessions.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }

            sessions.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Başka bir aktif oturum yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            revoking = revokingId == session.id,
                            onRevoke = { viewModel.revoke(session.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (hasOtherSessions) {
                    HorizontalDivider()
                    Button(
                        onClick = {
                            viewModel.clearRevokeOthersError()
                            showRevokeOthersDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    ) {
                        Text("Diğer Tüm Oturumları Sonlandır")
                    }
                }
            }
        }
    }

    if (showRevokeOthersDialog) {
        AlertDialog(
            onDismissRequest = { if (!revokingOthers) showRevokeOthersDialog = false },
            icon = { Icon(Icons.Filled.Smartphone, contentDescription = null) },
            title = { Text("Diğer tüm oturumları sonlandır") },
            text = {
                Column {
                    Text(
                        "Bu cihaz dışındaki tüm oturumlar sonlandırılacak ve o cihazlar " +
                            "tekrar giriş yapmak zorunda kalacak. Şifrenin çalınmış olabileceğini " +
                            "düşünüyorsan bu işlemi yapmak güvenli bir önlemdir.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (revokeOthersError != null) {
                        Text(
                            text = revokeOthersError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (revokingOthers) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.revokeOthers() },
                    enabled = !revokingOthers,
                ) {
                    Text("Sonlandır", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRevokeOthersDialog = false },
                    enabled = !revokingOthers,
                ) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

@Composable
private fun SessionRow(
    session: SessionDto,
    revoking: Boolean,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.deviceName?.takeIf { it.isNotBlank() } ?: "Bilinmeyen cihaz",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (session.isCurrent) {
                    Text(
                        text = "Bu cihaz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            val lastUsed = formatClockTime(session.lastUsedAt ?: session.createdAt)
            if (lastUsed.isNotBlank()) {
                Text(
                    text = "Son aktif: $lastUsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!session.isCurrent) {
            if (revoking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRevoke) {
                    Text("Çıkış Yaptır", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
