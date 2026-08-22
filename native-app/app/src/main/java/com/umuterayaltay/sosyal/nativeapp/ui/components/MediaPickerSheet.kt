package com.umuterayaltay.sosyal.nativeapp.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.GifDto
import com.umuterayaltay.sosyal.nativeapp.network.StickerDto
import com.umuterayaltay.sosyal.nativeapp.repository.CreateStickerResult
import com.umuterayaltay.sosyal.nativeapp.repository.GifSearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.StickersResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * GIF + Sticker seçici — mesaj/yorum/post composer'larından paylaşılan TEK
 * bileşen (Faz 5 Dalga 3B, native Android). GIF sekmesi app/api_v1/gifs.py
 * api_gif_search()'ü (Klipy proxy, [GifsRepository]) debounce'lı arar; Sticker
 * sekmesi kullanıcının kendi çıkartmalarını [StickersRepository.getMyStickers]
 * (Dalga 1C'de yazıldı, burada SADECE tüketiliyor) ile listeler.
 *
 * BİLİNÇLİ SINIR: bu bileşen ViewModel'siz — state'i kendi içinde tutar ve
 * ServiceLocator'daki repository'leri DOĞRUDAN çağırır. Gerekçe: üç farklı
 * composer'da (ConversationScreen, CreatePostScreen, PostDetailScreen yorum
 * çubuğu) REUSE ediliyor; GIF/sticker arama state'ini her birinin kendi
 * ViewModel'ine taşımak (ya da paylaşılan bir ViewModel İCAT etmek) gereksiz
 * bir bağlantı yaratırdı — seçim SONUCU (url/id) callback ile çağırana geri
 * verilir, gerçek gönderim mantığı HER composer'ın KENDİ ViewModel'inde kalır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onGifSelected: (String) -> Unit,
    // 2026-08-08 (kullanıcı raporu: "çıkartma gönderince direkt yüklenmiyor,
    // bi süre sonra geliyor") — SADECE id yerine TAM StickerDto (imageUrl
    // dahil) verilir, böylece çağıran taraf optimistic (anında gösterilen)
    // mesaj balonuna sticker'ın GÖRSELİNİ de koyabilir (bkz.
    // ConversationViewModel.send() yorumu) — id'den SONRADAN ayrı bir ağ
    // isteğiyle image_url çekmeye gerek kalmaz.
    onStickerSelected: (StickerDto) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = GIF, 1 = Çıkartma

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("GIF") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Çıkartma") },
                )
            }
            when (selectedTab) {
                0 -> GifTab(onGifSelected = onGifSelected)
                else -> StickerTab(onStickerSelected = onStickerSelected)
            }
        }
    }
}

/** Boş sorgu = trending (backend api_gif_search()'ün AYNI kuralı) — sekme
 * ilk açıldığında query="" ile hemen bir arama tetiklenir. */
@Composable
private fun GifTab(onGifSelected: (String) -> Unit) {
    val gifsRepository = ServiceLocator.gifsRepository
    var query by remember { mutableStateOf("") }
    var gifs by remember { mutableStateOf<List<GifDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var disabled by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // MessageSearchViewModel'deki AYNI debounce deseni (400ms) — burada
    // ayrı bir Flow/ViewModel İCAT edilmedi, LaunchedEffect(query) zaten
    // her tuş vuruşunda önceki bekleyen çağrıyı iptal edip yeniden başlatır.
    LaunchedEffect(query) {
        delay(400)
        loading = true
        errorMsg = null
        when (val result = gifsRepository.search(query.trim())) {
            is GifSearchResult.Success -> {
                gifs = result.gifs
                disabled = false
            }
            is GifSearchResult.Disabled -> {
                disabled = true
                gifs = emptyList()
            }
            is GifSearchResult.Error -> {
                errorMsg = "GIF aranamadı, lütfen tekrar deneyin"
                gifs = emptyList()
            }
        }
        loading = false
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)) {
        if (!disabled) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("GIF ara...") },
                singleLine = true,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 360.dp)
                .padding(top = 8.dp),
        ) {
            when {
                disabled -> CenteredHint("GIF özelliği şu anda kullanılamıyor.")
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                errorMsg != null -> CenteredHint(errorMsg ?: "")
                gifs.isEmpty() -> CenteredHint("Sonuç bulunamadı")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(gifs) { gif ->
                        AsyncImage(
                            model = gif.preview ?: gif.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onGifSelected(gif.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerTab(onStickerSelected: (StickerDto) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stickersRepository = ServiceLocator.stickersRepository
    var stickers by remember { mutableStateOf<List<StickerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    // 2026-08-22 (kullanıcı isteği: "çıkartma gönderme kısmında + olsun
    // sticker ekleyebilelim") — StickersRepository.createSticker() Dalga 1C'de
    // yazılmış ama HİÇBİR UI'dan çağrılmıyordu (bkz. dosya başı yorumu: "Dalga
    // 3'te başka bir ajan ekleyecek"), burada TÜKETİLİYOR.
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        when (val result = stickersRepository.getMyStickers()) {
            is StickersResult.Success -> stickers = result.stickers
            is StickersResult.Error -> errorMsg = "Çıkartmalar yüklenemedi"
        }
        loading = false
    }

    val addStickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            uploading = true
            scope.launch {
                val mimeType = context.contentResolver.getType(uri)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    when (val result = stickersRepository.createSticker(bytes, mimeType)) {
                        is CreateStickerResult.Success -> {
                            stickers = listOf(
                                StickerDto(id = result.id, imageUrl = result.imageUrl, mineCreated = true),
                            ) + stickers
                        }
                        is CreateStickerResult.Error -> errorMsg = "Çıkartma eklenemedi"
                    }
                }
                uploading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 360.dp)
            .padding(12.dp),
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            errorMsg != null -> CenteredHint(errorMsg ?: "")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !uploading) {
                                addStickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Yeni çıkartma ekle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (stickers.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { CenteredHint("Henüz çıkartman yok") }
                } else {
                    items(stickers) { sticker ->
                        AsyncImage(
                            model = sticker.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onStickerSelected(sticker) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
