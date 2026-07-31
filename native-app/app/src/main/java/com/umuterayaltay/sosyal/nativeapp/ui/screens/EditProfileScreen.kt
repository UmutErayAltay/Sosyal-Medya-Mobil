package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.viewmodel.EditProfileEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.EditProfileViewModel

/**
 * "Profili Düzenle" ekranı — form (full_name/bio/username) + avatar
 * (CreatePostScreen'deki Photo Picker deseniyle TUTARLI: seç + önizle +
 * kaldır) + is_private/hide_last_seen için Material3 Switch + açıklayıcı
 * metin. Kaydet başarılı olunca [onSaved] çağrılır (NavHost geri navigasyonu
 * yapar — bu ekran kendi navigasyonunu BİLMEZ, CreatePostScreen'deki
 * onPostCreated deseniyle TUTARLI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: EditProfileViewModel = viewModel(),
) {
    val context = LocalContext.current
    val fullName by viewModel.fullName.collectAsState()
    val bio by viewModel.bio.collectAsState()
    val username by viewModel.username.collectAsState()
    val isPrivate by viewModel.isPrivate.collectAsState()
    val hideLastSeen by viewModel.hideLastSeen.collectAsState()
    val currentAvatarUrl by viewModel.currentAvatarUrl.collectAsState()
    val selectedAvatarUri by viewModel.selectedAvatarUri.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditProfileEvent.Success -> onSaved()
                is EditProfileEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onAvatarSelected(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profili Düzenle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !submitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = { viewModel.submit(context) }, enabled = !loading) {
                            Text("Kaydet")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    if (selectedAvatarUri != null) {
                        AsyncImage(
                            model = selectedAvatarUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                        )
                    } else if (!currentAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                        )
                    }

                    if (selectedAvatarUri != null) {
                        IconButton(
                            onClick = { viewModel.onAvatarSelected(null) },
                            enabled = !submitting,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Yeni görseli kaldır")
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Profil Fotoğrafını Değiştir")
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = { Text("Ad Soyad") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Kullanıcı Adı") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text("Biyografi") },
                    minLines = 3,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Gizlilik",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                SettingsSwitchRow(
                    title = "Gizli Hesap",
                    description = "Hesabın gizli olduğunda gönderilerini sadece onayladığın takipçilerin görebilir",
                    checked = isPrivate,
                    onCheckedChange = viewModel::onIsPrivateChange,
                    enabled = !submitting,
                )

                SettingsSwitchRow(
                    title = "Son Görülmeyi Gizle",
                    description = "Açıksa profilinde en son ne zaman aktif olduğun görünmez",
                    checked = hideLastSeen,
                    onCheckedChange = viewModel::onHideLastSeenChange,
                    enabled = !submitting,
                )
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
