package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.viewmodel.DraftsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.DraftsViewModel

/**
 * "Taslaklarım" ekranı — app/templates/drafts.html'in native karşılığı
 * (bkz. DraftsViewModel). Web'in flat buton-satırı tasarımı BİLEREK
 * PostCard'ın zengin görünümü (beğeni/yorum/bookmark ikonları vb. — bir
 * taslakta HİÇBİRİ anlamlı değil, kimse henüz göremiyor) YERİNE tercih
 * edildi: sadece içerik önizlemesi + tek görsel/"Video" rozeti + Yayınla/
 * Düzenle/Sil. Ayarlar'daki "Taslaklarım" satırından erişilir. Post giriş
 * animasyonu BİLEREK YOK (kullanıcı raporu: "postların gelme animasyonları
 * gereksiz kasmaya neden oluyor" — bkz. FeedScreen.kt'nin 2026-08-21'de
 * TAMAMEN kaldırdığı PostFeedStaggerReveal, burada baştan hiç eklenmedi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: DraftsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val drafts by viewModel.drafts.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val processingIds by viewModel.processingIds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DraftsEvent.SessionExpired -> onSessionExpired()
                is DraftsEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Taslaklarım") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && drafts.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && drafts.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            drafts.isEmpty() -> CenteredMessage(padding) {
                Text("Henüz taslağın yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(drafts, key = { it.id }) { draft ->
                    DraftItem(
                        draft = draft,
                        processing = draft.id in processingIds,
                        onPublish = { viewModel.publish(draft.id) },
                        onSave = { newContent -> viewModel.editContent(draft.id, newContent) },
                        onDelete = { viewModel.delete(draft.id) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun DraftItem(
    draft: Post,
    processing: Boolean,
    onPublish: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showEditDialog by remember(draft.id) { mutableStateOf(false) }
    var editContent by remember(draft.id) { mutableStateOf(draft.content ?: "") }
    var showDeleteConfirm by remember(draft.id) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = draft.content?.takeIf { it.isNotBlank() } ?: "(içerik yok, sadece görsel/video)",
                style = MaterialTheme.typography.bodyMedium,
            )
            val thumbnailUrl = draft.imageUrl ?: draft.imageUrls?.firstOrNull()
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Taslak görseli",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            } else if (!draft.videoUrl.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Video",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editContent = draft.content ?: ""; showEditDialog = true }) {
                        Text("Düzenle")
                    }
                    TextButton(onClick = onPublish) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Yayınla", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Taslağı düzenle") },
            text = {
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        onSave(editContent)
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Vazgeç") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Taslağı sil") },
            text = { Text("Bu taslağı silmek istediğine emin misin? Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Vazgeç") }
            },
        )
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
