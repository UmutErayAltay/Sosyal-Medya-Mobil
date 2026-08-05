package com.umuterayaltay.sosyal.nativeapp.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.ConversationSummaryDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.InboxEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.InboxViewModel

/**
 * Gelen kutusu (inbox) — DiscoverScreen'deki UserRow/UserResultRow ile GÖRSEL
 * olarak tutarlı (avatar + isim, tıklanabilir satır) ama veri şekli farklı
 * (son mesaj önizlemesi + zaman + okunmadı rozeti eklendi) — bu yüzden
 * UserRow reuse edilmedi, kendi satır composable'ı (ConversationRow) yazıldı.
 *
 * Backend sözleşmesi: app/api_v1.py api_message_conversations() —
 * GET /api/v1/messages/conversations (bkz. ApiModels.kt ConversationSummaryDto).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onConversationClick: (String) -> Unit,
    onNewMessageClick: () -> Unit,
    onNewGroupClick: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: InboxViewModel = viewModel(),
) {
    val conversations by viewModel.conversations.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    // FeedScreen.kt'deki AYNI karar (bkz. HideableTopBar.kt) — nested-scroll
    // tabanlı enterAlwaysScrollBehavior() TERK EDİLDİ, listState üzerinden
    // elle scroll-yönü tespiti kullanılıyor.
    val listState = rememberLazyListState()
    val isTopBarVisible by rememberTopBarVisibility(listState)

    // Madde 6 (top bar mimarisi rewrite): FeedScreen.kt'deki AYNI karar -
    // Scaffold'un topBar slotu TAMAMEN KALDIRILDI, bar artık bir Box overlay
    // katmanı (OverlayTopBar), içerik TOP_BAR_HEIGHT kadar SABİT üst boşluk
    // bırakır (bar'ın görünürlüğü bu boşluğu ETKİLEMEZ).
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && conversations.isEmpty() -> CenteredMessage { CircularProgressIndicator() }
            error != null && conversations.isEmpty() -> CenteredMessage {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            conversations.isEmpty() -> CenteredMessage {
                Text(
                    text = "Henüz mesajın yok",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = TOP_BAR_HEIGHT + 4.dp, bottom = 4.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, onClick = { onConversationClick(conversation.id) })
                }
            }
        }

        OverlayTopBar(
            visible = isTopBarVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            TopAppBar(
                title = { Text("Mesajlar") },
                actions = {
                    IconButton(onClick = onNewGroupClick) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = "Yeni Grup")
                    }
                    IconButton(onClick = onNewMessageClick) {
                        Icon(Icons.Filled.Add, contentDescription = "Yeni Mesaj")
                    }
                },
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!conversation.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = conversation.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (conversation.isGroup) Icons.Filled.Groups else Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = conversation.name ?: "Bilinmeyen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!conversation.lastMessagePreview.isNullOrBlank()) {
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (conversation.hasUnread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = formatClockTime(conversation.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (conversation.hasUnread) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (conversation.hasUnread) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (conversation.hasUnread) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .align(Alignment.End)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// Madde 6: padding parametresi Scaffold ile birlikte KALDIRILDI - artık
// TOP_BAR_HEIGHT ile SABİT bir üst boşluk bırakılıyor (bkz. yukarıdaki Box
// yorumu), Scaffold'un DEĞİŞKEN padding'ine bağımlılık YOK.
@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_HEIGHT)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
