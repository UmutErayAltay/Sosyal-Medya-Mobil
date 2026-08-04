package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Highlight
import com.umuterayaltay.sosyal.nativeapp.repository.HighlightItem
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HighlightsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HighlightsViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HighlightsViewModelFactory

/**
 * Öne çıkanlar (highlights) — bağımsız ekran (görev kapsamı: ProfileScreen'e
 * BU DALGADA bağlanmıyor, Dalga 4'e ertelendi). Basit bir grid listesi + tek
 * bir highlight'ı görüntüleme (story-viewer benzeri ama [HighlightItemDto]
 * listesi üzerinde — StoryViewerScreen'in AKSİNE poll/reaction/reply YOK,
 * bkz. backend api_view_highlight() docstring'i: highlight öğeleri story'nin
 * `id`sine FK'sız KOPYALARDIR, anket TAŞINMAZ).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HighlightsViewModel = viewModel(factory = HighlightsViewModelFactory(userId)),
) {
    val highlights by viewModel.highlights.collectAsState()
    val selectedTitle by viewModel.selectedTitle.collectAsState()
    val selectedIsMine by viewModel.selectedIsMine.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    var openHighlightId by remember { mutableStateOf<String?>(null) }
    // "Düzenle" diyaloğu — SADECE başlık (title) alanı var, kapak (cover_url)
    // değiştirme bu turda KAPSAM DIŞI bırakıldı (zaman kısıtı, bkz. görev
    // tanımı "Kapak değiştirme opsiyonel/kapsam dışı bırakılabilir").
    var showEditDialog by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HighlightsEvent.SessionExpired -> onSessionExpired()
                is HighlightsEvent.HighlightDeleted -> openHighlightId = null
                is HighlightsEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (openHighlightId != null) {
        HighlightViewerContent(
            title = selectedTitle,
            isMine = selectedIsMine,
            items = selectedItems,
            loading = loading,
            onNavigateBack = { openHighlightId = null },
            onDelete = {
                openHighlightId?.let { viewModel.deleteHighlight(it) }
            },
            onEditClick = {
                editTitleText = selectedTitle
                showEditDialog = true
            },
        )

        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Öne çıkanı düzenle") },
                text = {
                    OutlinedTextField(
                        value = editTitleText,
                        onValueChange = { editTitleText = it },
                        label = { Text("Başlık") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEditDialog = false
                            openHighlightId?.let { viewModel.updateHighlight(it, editTitleText) }
                        },
                    ) { Text("Kaydet") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Vazgeç") }
                },
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Öne Çıkanlar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && highlights.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            highlights.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = error ?: "Henüz öne çıkan yok",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(padding.calculateTopPadding(), 12.dp, 12.dp, 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(highlights, key = { it.id }) { highlight ->
                    HighlightGridItem(
                        highlight = highlight,
                        onClick = {
                            openHighlightId = highlight.id
                            viewModel.viewHighlight(highlight.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightGridItem(highlight: Highlight, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!highlight.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = highlight.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = highlight.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${highlight.itemCount} öğe",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightViewerContent(
    title: String,
    isMine: Boolean,
    items: List<HighlightItem>,
    loading: Boolean,
    onNavigateBack: () -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit,
) {
    var currentIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBackIosNew, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (isMine) {
                        // Silme ikonunun YANINA eklendi — kapak değiştirme kapsam
                        // dışı bırakıldığı için SADECE başlık düzenleniyor (bkz.
                        // HighlightsScreen üstündeki showEditDialog yorumu).
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Filled.Edit, contentDescription = "Öne çıkanı düzenle")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Öne çıkanı sil")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator()
                items.isEmpty() -> Text("Öğe yok", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val item = items.getOrNull(currentIndex) ?: items.first()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize().weight(1f, fill = true)) {
                            if (!item.videoUrl.isNullOrBlank()) {
                                VideoSegmentSimple(videoUrl = item.videoUrl)
                            } else if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (!item.caption.isNullOrBlank()) {
                                Text(
                                    text = item.caption,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        .padding(12.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${currentIndex + 1} / ${items.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "Önceki",
                                    modifier = Modifier.clickable(enabled = currentIndex > 0) {
                                        currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                    },
                                    color = if (currentIndex > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Sonraki",
                                    modifier = Modifier.clickable(enabled = currentIndex < items.size - 1) {
                                        currentIndex = (currentIndex + 1).coerceAtMost(items.size - 1)
                                    },
                                    color = if (currentIndex < items.size - 1) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Highlight öğesindeki video için sade bir oynatıcı — StoryViewerScreen'in
 * VideoSegment'inden FARKLI olarak otomatik ilerleme/`onEnded` YOK (kullanıcı
 * elle "Sonraki" ile geçiyor, bkz. HighlightViewerContent). */
@Composable
private fun VideoSegmentSimple(videoUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = androidx.compose.runtime.remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
