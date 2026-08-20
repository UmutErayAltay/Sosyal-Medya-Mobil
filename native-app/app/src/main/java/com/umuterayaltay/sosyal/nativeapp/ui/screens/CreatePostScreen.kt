package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ui.components.MediaPickerSheet
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CreatePostEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CreatePostViewModel

private data class VisibilityOption(val value: String, val label: String, val icon: ImageVector)

private val VISIBILITY_OPTIONS = listOf(
    VisibilityOption("public", "Herkese Açık", Icons.Filled.Public),
    VisibilityOption("followers", "Takipçiler", Icons.Filled.Group),
    VisibilityOption("close_friends", "Yakın Arkadaşlar", Icons.Filled.Star),
)

/**
 * "Yeni Gönderi" ekranı — app/api_v1.py api_create_post() sözleşmesiyle
 * (bkz. CreatePostViewModel) AYNI BİLİNÇLİ SINIR: metin + (EN FAZLA 4 opsiyonel
 * görsel VEYA TEK opsiyonel video/reel, ikisi birden UI'da mutually exclusive)
 * + görünürlük (galeriden, Android Photo Picker'ın PickMultipleVisualMedia'sı
 * ile — 2026-08-09, kullanıcı isteği: "instadaki gibi kaydırmalı olabilir",
 * bkz. PostCard.kt'deki HorizontalPager carousel). Görsel/video seçildiğinde
 * diğeri otomatik temizlenir (bkz. ViewModel onImagesSelected/
 * onVideoSelected). Video seçiliyken "Reel olarak paylaş" Switch'i görünür —
 * reels.py'nin is_reel=True + video_url IS NOT NULL filtresi ZATEN doğru
 * postları buluyor, burada SADECE bayrak taşınıyor. Görünürlük seçimi
 * DiscoverScreen'deki SearchTypeFilterRow'un FilterChip satırı DESENİYLE
 * tutarlı. Paylaşım sonrası navigasyon (geri dönme) bu ekranın BİLMEDİĞİ bir
 * şey — [onPostCreated] callback'i çağrılır, NavHost geri navigasyonu yapar
 * (NewMessageScreen'deki onConversationReady deseniyle TUTARLI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onNavigateBack: () -> Unit,
    onPostCreated: () -> Unit,
    onSessionExpired: () -> Unit,
    // Share-target (2026-08-21) — Android paylaş menüsünden gelen metin/görsel
    // (bkz. AppNavHost.kt composable("createPost")). SADECE ilk composition'da
    // BİR KEZ uygulanır (bkz. aşağıdaki LaunchedEffect(Unit)) — sonra
    // [onShareContentConsumed] çağrılıp outer state temizlenir, aksi halde
    // kullanıcı içeriği SİLİP yeniden yazsa bile her recomposition'da eski
    // paylaşılan metin GERİ gelirdi.
    initialText: String? = null,
    initialImageUri: Uri? = null,
    onShareContentConsumed: () -> Unit = {},
    viewModel: CreatePostViewModel = viewModel(),
) {
    val context = LocalContext.current
    val content by viewModel.content.collectAsState()
    val visibility by viewModel.visibility.collectAsState()
    val selectedImageUris by viewModel.selectedImageUris.collectAsState()
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val isReel by viewModel.isReel.collectAsState()
    val selectedGifUrl by viewModel.selectedGifUrl.collectAsState()
    val showPollEditor by viewModel.showPollEditor.collectAsState()
    val pollOptions by viewModel.pollOptions.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val error by viewModel.error.collectAsState()

    // GIF seçici (Faz 5 Dalga 3B) — MediaPickerSheet PAYLAŞILAN bileşen hem
    // GIF hem Sticker sekmesi taşıyor ama backend api_create_post() SADECE
    // gif_url kabul ediyor (sticker_id post oluşturmada desteklenmiyor, bkz.
    // InteractionsRepository.createPost yorumu) — bu yüzden onStickerSelected
    // BİLİNÇLİ olarak no-op bırakıldı, sekme gizlenmedi (bileşene dokunulmadı).
    var showMediaPicker by remember { mutableStateOf(false) }
    val mediaPickerSheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        if (initialText != null) viewModel.onContentChange(initialText)
        if (initialImageUri != null) viewModel.onImagesSelected(listOf(initialImageUri))
        if (initialText != null || initialImageUri != null) onShareContentConsumed()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreatePostEvent.Success -> {
                    if (event.savedAsDraft) {
                        Toast.makeText(context, "Taslak kaydedildi", Toast.LENGTH_SHORT).show()
                    }
                    onPostCreated()
                }
                is CreatePostEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    // 2026-08-09 (kullanıcı isteği: "1'den fazla görsel ekleme olsun") —
    // PickVisualMedia (tekil) yerine PickMultipleVisualMedia(maxItems=4),
    // backend'in upload_images(..., max_count=4) sınırıyla AYNI üst sınır.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4),
    ) { uris -> viewModel.onImagesSelected(uris) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onVideoSelected(uri) }

    // Anket editörü açıkken en az 2 dolu seçenek de "içerik var" sayılır —
    // ViewModel.submit()'teki AYNI eşik (backend api_create_post() ile AYNI).
    val hasPoll = showPollEditor && pollOptions.count { it.isNotBlank() } >= 2
    val canSubmit = (
        content.isNotBlank() || selectedImageUris.isNotEmpty() || selectedVideoUri != null ||
            !selectedGifUrl.isNullOrBlank() || hasPoll
        ) && !submitting

    if (showMediaPicker) {
        MediaPickerSheet(
            sheetState = mediaPickerSheetState,
            onDismiss = { showMediaPicker = false },
            onGifSelected = { url ->
                showMediaPicker = false
                viewModel.onGifSelected(url)
            },
            onStickerSelected = {},
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Gönderi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !submitting) {
                        Icon(Icons.Filled.Close, contentDescription = "Vazgeç")
                    }
                },
                actions = {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        // 2026-08-21 (taslak): web'in create_post()'undaki AYNI
                        // iki-buton mantığı (action=draft vs normal submit) —
                        // reel'de taslak anlamsız (bkz. reels.py'nin is_reel
                        // filtresi hiçbir zaman is_draft=true postu göstermez,
                        // yayınlanana kadar reel de "yokmuş" gibi davranır, bu
                        // yüzden reel iken de gösterilmeye devam eder, ayrı bir
                        // kısıt İCAT EDİLMEDİ).
                        TextButton(onClick = { viewModel.submit(context, saveAsDraft = true) }, enabled = canSubmit) {
                            Text("Taslak Kaydet")
                        }
                        TextButton(onClick = { viewModel.submit(context) }, enabled = canSubmit) {
                            Text("Paylaş")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Kimler görebilir?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VisibilityFilterRow(selected = visibility, onSelect = viewModel::onVisibilityChange)
            }

            OutlinedTextField(
                value = content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ne düşünüyorsun?") },
                minLines = 4,
                enabled = !submitting,
                shape = MaterialTheme.shapes.medium,
            )

            if (selectedImageUris.isNotEmpty()) {
                // 2026-08-09 (kullanıcı isteği: "1'den fazla görsel ekleme
                // olsun") — Android Photo Picker'ın PickMultipleVisualMedia'sı
                // HER launch'ta TAM bir seçim döndürür (önceki seçime EKLEME
                // yapmaz, YERİNE geçer) — bu yüzden "daha fazla ekle" tuşu YOK,
                // sadece tekil kaldırma (X) + tüm seçimi YENİDEN yapan
                // "Görselleri değiştir" butonu var, PostCard'daki carousel'in
                // AYNI sırasıyla önizleniyor (LazyRow, dot-indicator YOK —
                // burada zaten hepsi aynı anda yan yana görünür).
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(selectedImageUris) { index, uri ->
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    IconButton(
                                        onClick = { viewModel.onImageRemovedAt(index) },
                                        enabled = !submitting,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Görseli kaldır",
                                            modifier = Modifier.size(16.dp),
                                        )
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
                        ) {
                            Text("Görselleri değiştir (${selectedImageUris.size}/4)")
                        }
                    }
                }
            } else if (selectedVideoUri == null && selectedGifUrl.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !submitting,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Text(text = "Görsel Ekle", modifier = Modifier.padding(start = 8.dp))
                }
            }

            // GIF önizleme/seçim (Faz 5 Dalga 3B) — görsel/video ile mutually
            // exclusive (ViewModel.onGifSelected/onImagesSelected/onVideoSelected
            // zaten diğerlerini temizliyor, backend api_create_post()'daki
            // AYNI kural).
            if (selectedImageUris.isEmpty() && selectedVideoUri == null) {
                if (!selectedGifUrl.isNullOrBlank()) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = selectedGifUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            IconButton(
                                onClick = { viewModel.onGifCleared() },
                                enabled = !submitting,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "GIF'i kaldır")
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showMediaPicker = true },
                        enabled = !submitting,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Gif, contentDescription = null)
                        Text(text = "GIF Ekle", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Video seçimi — görsel ile mutually exclusive (yukarıdaki
            // ViewModel.onImagesSelected/onVideoSelected zaten diğerini
            // temizliyor). Tam video player İCAT EDİLMEDİ (görev tanımı
            // gereği gerekmiyor) — sadece dosya seçildiğini gösteren basit
            // bir ikon+etiket + kaldır butonu yeterli.
            if (selectedImageUris.isEmpty()) {
                if (selectedVideoUri != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f),
                            exit = fadeOut(tween(150)),
                        ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        ) {
                            Icon(Icons.Filled.Movie, contentDescription = null)
                            Text(
                                text = "Video seçildi",
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.onVideoSelected(null) },
                                enabled = !submitting,
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Videoyu kaldır")
                            }
                        }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Reel olarak paylaş",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = isReel,
                                onCheckedChange = viewModel::onReelToggle,
                                enabled = !submitting,
                            )
                        }
                    }
                } else if (selectedGifUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                            )
                        },
                        enabled = !submitting,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Text(text = "Video Ekle", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Anket ekle/kaldır (Faz 5 Dalga 4C) — görsel/video/GIF ile
            // mutually-exclusive DEĞİL (backend api_create_post() ile AYNI
            // kural, GIF'in aksine birlikte gönderilebilir), bu yüzden diğer
            // medya butonlarının durumundan BAĞIMSIZ her zaman görünür.
            OutlinedButton(
                onClick = { viewModel.togglePollEditor() },
                enabled = !submitting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.BarChart, contentDescription = null)
                Text(
                    text = if (showPollEditor) "Anketi Kaldır" else "Anket Ekle",
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (showPollEditor) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pollOptions.forEachIndexed { index, optionText ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = optionText,
                                onValueChange = { viewModel.onPollOptionChange(index, it) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Seçenek ${index + 1}") },
                                singleLine = true,
                                enabled = !submitting,
                                shape = MaterialTheme.shapes.medium,
                            )
                            // İlk 2 seçenek kaldırılamaz — "en az 2 seçenek"
                            // kuralı ViewModel.removePollOption() ile AYNI.
                            if (index >= 2) {
                                IconButton(
                                    onClick = { viewModel.removePollOption(index) },
                                    enabled = !submitting,
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Seçeneği kaldır")
                                }
                            }
                        }
                    }
                    if (pollOptions.size < 4) {
                        TextButton(
                            onClick = { viewModel.addPollOption() },
                            enabled = !submitting,
                        ) {
                            Text("Seçenek Ekle")
                        }
                    }
                }
            }

            if (error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VisibilityFilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(VISIBILITY_OPTIONS, key = { it.value }) { option ->
            val isSelected = selected == option.value
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option.value) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
