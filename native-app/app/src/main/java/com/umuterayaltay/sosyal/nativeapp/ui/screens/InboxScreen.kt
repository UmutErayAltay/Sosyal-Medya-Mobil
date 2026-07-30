package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Scaffold
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

    Scaffold(
        topBar = {
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
        },
    ) { padding ->
        when {
            loading && conversations.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && conversations.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            conversations.isEmpty() -> CenteredMessage(padding) { Text("Henüz mesajın yok") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, onClick = { onConversationClick(conversation.id) })
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!conversation.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = conversation.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = if (conversation.isGroup) Icons.Filled.Groups else Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = conversation.name ?: "Bilinmeyen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
            )
            if (!conversation.lastMessagePreview.isNullOrBlank()) {
                Text(
                    text = conversation.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (conversation.hasUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatClockTime(conversation.lastMessageAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conversation.hasUnread) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
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
