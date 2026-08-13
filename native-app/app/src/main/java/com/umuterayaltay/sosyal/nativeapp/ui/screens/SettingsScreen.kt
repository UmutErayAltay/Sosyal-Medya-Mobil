package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.data.ThemeMode
import com.umuterayaltay.sosyal.nativeapp.viewmodel.SettingsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.SettingsViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.UpdateUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.UpdateViewModel

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
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeMode by ServiceLocator.themePreferenceStore.themeMode.collectAsState()

    // 2026-08-09: uygulama içi güncelleme (kullanıcı isteği: "her seferinde
    // elle indirip kurmak yerine uygulama içinden güncelleme") — kendi
    // ViewModel'i, SettingsViewModel'in (deaktivasyon/çıkış) sorumluluğuna
    // KARIŞTIRILMADI.
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateViewModel: UpdateViewModel = viewModel()

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

            SettingsSectionHeader("Görünüm")
            SettingsRow(
                icon = Icons.Filled.DarkMode,
                label = "Tema: ${themeModeLabel(themeMode)}",
                onClick = { showThemeDialog = true },
            )

            SettingsSectionHeader("Uygulama")
            SettingsRow(
                icon = Icons.Filled.SystemUpdate,
                label = "Uygulama Güncellemeleri",
                onClick = {
                    updateViewModel.resetToIdle()
                    showUpdateDialog = true
                },
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
                    AnimatedVisibility(visible = deactivateError != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            text = deactivateError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    AnimatedVisibility(
                        visible = deactivating,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
            title = { Text("Tema seç") },
            text = {
                Column {
                    ThemeOptionRow(
                        label = "Aydınlık",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = {
                            ServiceLocator.themePreferenceStore.setThemeMode(ThemeMode.LIGHT)
                            showThemeDialog = false
                        },
                    )
                    ThemeOptionRow(
                        label = "Koyu",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = {
                            ServiceLocator.themePreferenceStore.setThemeMode(ThemeMode.DARK)
                            showThemeDialog = false
                        },
                    )
                    ThemeOptionRow(
                        label = "Sistem varsayılanı",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = {
                            ServiceLocator.themePreferenceStore.setThemeMode(ThemeMode.SYSTEM)
                            showThemeDialog = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Kapat")
                }
            },
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            viewModel = updateViewModel,
            onDismiss = { showUpdateDialog = false },
        )
    }
}

/**
 * Ayarlar > Uygulama Güncellemeleri diyalogu (2026-08-09) — GitHub Release'i
 * kontrol edip yeni bir APK varsa indirip kurulum akışını başlatır.
 * [UpdateUiState]'in HER dalı (Idle/Checking/UpToDate/Available/Downloading/
 * ReadyToInstall/Error) burada AYRI bir içerik gösterir — CallScreen.kt'deki
 * CallUiState switch'iyle AYNI desen.
 */
@Composable
private fun UpdateDialog(viewModel: UpdateViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    // Kurulum izni (API 26+ "Bilinmeyen kaynaklardan yükle") ayar
    // ekranından DÖNÜNCE — kullanıcı izni verdiyse kurulum Intent'ini
    // OTOMATİK ateşle, tekrar "Kur"a basmasına GEREK KALMASIN.
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val ready = state as? UpdateUiState.ReadyToInstall ?: return@rememberLauncherForActivityResult
        if (viewModel.hasInstallPermission(context)) {
            context.startActivity(viewModel.buildInstallIntent(context, ready.file))
        }
    }

    fun installOrRequestPermission(file: java.io.File) {
        if (viewModel.hasInstallPermission(context)) {
            context.startActivity(viewModel.buildInstallIntent(context, file))
        } else {
            installPermissionLauncher.launch(viewModel.buildInstallPermissionSettingsIntent(context))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.SystemUpdate, contentDescription = null) },
        title = { Text("Uygulama Güncellemeleri") },
        text = {
            Column {
                Text(
                    "Mevcut sürüm: ${viewModel.currentVersionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when (val s = state) {
                    is UpdateUiState.Idle -> {
                        Text(
                            "Yeni bir sürüm olup olmadığını kontrol edebilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateUiState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "Kontrol ediliyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    is UpdateUiState.UpToDate -> {
                        Text(
                            "Uygulaman güncel.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateUiState.Available -> {
                        val sizeMb = s.info.sizeBytes / (1024 * 1024)
                        val patchMb = s.info.deltaPlan?.patchSize?.div(1024 * 1024)
                        Text(
                            if (patchMb != null) {
                                "Yeni bir sürüm var. Parça güncelleme: ${patchMb}MB (tam sürüm ${sizeMb}MB yerine)."
                            } else {
                                "Yeni bir sürüm mevcut (${sizeMb}MB). Bu sürüm için parça güncelleme yok."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is UpdateUiState.DownloadingPatch -> {
                        UpdateProgressRow("Yama indiriliyor...", s.done, s.total)
                    }
                    is UpdateUiState.DownloadingFull -> {
                        Column {
                            if (s.fellBackReason != null) {
                                Text(
                                    "Parça güncelleme uygulanamadı, tam sürüm indiriliyor…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            UpdateProgressRow("İndiriliyor...", s.done, s.total)
                        }
                    }
                    is UpdateUiState.ApplyingPatch -> {
                        UpdateProgressRow("Yama uygulanıyor...", s.done, s.total)
                    }
                    is UpdateUiState.Verifying -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                "Doğrulanıyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Column {
                            Text(
                                "İndirme tamamlandı. Kuruluma başlamak için \"Kur\"a bas — " +
                                    "sistem izin isterse (\"Bilinmeyen kaynaklardan yükle\") ayarı " +
                                    "açıp geri dön, kurulum otomatik devam eder.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (s.viaDelta && s.savedBytes > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Bu güncellemede ${s.savedBytes / (1024 * 1024)}MB indirmeden tasarruf edildi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    is UpdateUiState.Error -> {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateUiState.Idle, is UpdateUiState.Error -> {
                    TextButton(onClick = { viewModel.checkForUpdate(context) }) {
                        Text("Kontrol Et")
                    }
                }
                is UpdateUiState.UpToDate -> {
                    TextButton(onClick = { viewModel.checkForUpdate(context) }) {
                        Text("Tekrar Kontrol Et")
                    }
                }
                is UpdateUiState.Available -> {
                    TextButton(onClick = { viewModel.downloadUpdate(context) }) {
                        Text("İndir")
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    TextButton(onClick = { installOrRequestPermission(s.file) }) {
                        Text("Kur")
                    }
                }
                is UpdateUiState.DownloadingPatch, is UpdateUiState.DownloadingFull,
                is UpdateUiState.ApplyingPatch, UpdateUiState.Verifying -> {
                    // Aktif işlem sırasında AYRI bir "İptal" butonu var
                    // (dismissButton) — burada tekrar tetikleyecek bir buton yok.
                }
                is UpdateUiState.Checking -> {
                    // Butonsuz — kontrol sürerken kullanıcı tekrar TETİKLEMESİN.
                }
            }
        },
        dismissButton = {
            val isActive = state is UpdateUiState.DownloadingPatch || state is UpdateUiState.DownloadingFull ||
                state is UpdateUiState.ApplyingPatch || state is UpdateUiState.Verifying
            TextButton(onClick = onDismiss) {
                Text(
                    when {
                        isActive -> "İptal"
                        state is UpdateUiState.ReadyToInstall -> "Sonra"
                        else -> "Kapat"
                    },
                )
            }
        },
    )
}

/** [UpdateDialog]'un indirme/uygulama fazlarında ortak kullandığı gerçek
 * yüzde + byte sayaçlı ilerleme satırı — önceden SADECE belirsiz
 * ([CircularProgressIndicator]) bir spinner vardı, kullanıcı 58 MB'lık bir
 * işlemin ne kadar sürdüğünü hiç göremiyordu. */
@Composable
private fun UpdateProgressRow(label: String, done: Long, total: Long) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${done / 1024 / 1024}MB / ${total / 1024 / 1024}MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Ayarlar satırında gösterilen kısa tema etiketi. */
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "Aydınlık"
    ThemeMode.DARK -> "Koyu"
    ThemeMode.SYSTEM -> "Sistem varsayılanı"
}

/** Tema seçim diyalogundaki tek bir radio satırı. */
@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Seçili satır hafif bir zemin rengine geçiş yapıyor — tema değişimini
    // anlık göstermek yerine göz yormayan bir crossfade ile vurguluyor.
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(200),
        label = "themeOptionBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
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
