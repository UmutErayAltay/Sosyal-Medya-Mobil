package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModelFactory

/**
 * Tek bir konuşma ekranı — mesaj geçmişi (eskiden yeniye, yukarı kaydırınca
 * daha eski sayfa) + metin gönderme (+ yanıtlama). Backend sözleşmesi:
 * app/api_v1.py api_message_conversation_detail()/api_send_message()
 * (bkz. ConversationViewModel docstring'i — basit polling ile "neredeyse
 * canlı", gerçek Supabase Realtime YOK).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onManageGroupClick: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModelFactory(conversationId),
    ),
) {
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingOlder by viewModel.loadingOlder.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val conversationInfo by viewModel.conversationInfo.collectAsState()
    val sendText by viewModel.sendText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val myUserId by viewModel.myUserId.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val listState = rememberLazyListState()

    // Yeni mesaj (gönderilen ya da polling ile gelen) listenin SONUNA
    // eklenince en alta kaydır — anahtar SADECE son mesajın id'si, bu yüzden
    // loadOlder()'ın listenin BAŞINA eklediği eski sayfalar burayı TETİKLEMEZ.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Listenin başına (yukarı kaydırınca) yaklaşınca daha eski sayfayı yükle —
    // loadOlder() zaten hasMore/loading guard'lı, tekrar tetiklenmesi zararsız.
    LaunchedEffect(listState, hasMore) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { firstVisible ->
            if (hasMore && firstVisible <= 2) {
                viewModel.loadOlder()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val info = conversationInfo
                        if (!info?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = info?.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                imageVector = if (info?.isGroup == true) Icons.Filled.Groups else Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Text(
                            text = info?.name ?: "Konuşma",
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (conversationInfo?.isGroup == true) {
                        IconButton(onClick = onManageGroupClick) {
                            Icon(Icons.Filled.Groups, contentDescription = "Grubu Yönet")
                        }
                    }
                },
            )
        },
        bottomBar = {
            ConversationInputBar(
                sendText = sendText,
                onSendTextChange = viewModel::onSendTextChange,
                onSend = viewModel::send,
                replyingTo = replyingTo,
                onCancelReply = viewModel::clearReplyingTo,
            )
        },
    ) { padding ->
        when {
            loading && messages.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && messages.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.loadInitial() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            messages.isEmpty() -> CenteredMessage(padding) { Text("Henüz mesaj yok, ilk mesajı sen gönder") }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (loadingOlder) {
                    item(key = "loading_older") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = myUserId != null && message.senderId == myUserId,
                        onReplyClick = { viewModel.setReplyingTo(message) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: MessageDto, isMine: Boolean, onReplyClick: () -> Unit) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                // Uzun basınca "Yanıtla" — bu turda tek uzun-tık aksiyonu var,
                // ayrı bir menü/IconButton İCAT edilmedi (spesifikasyon: basit).
                .combinedClickable(onClick = {}, onLongClick = onReplyClick),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                val replyTo = message.replyTo
                if (replyTo != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(contentColor.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = replyTo.profiles?.username ?: "Bilinmeyen kullanıcı",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                        )
                        Text(
                            text = replyTo.content?.takeIf { it.isNotBlank() }
                                ?: if (!replyTo.imageUrl.isNullOrBlank()) "Görsel" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
                if (!message.content.isNullOrBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        modifier = Modifier.padding(top = if (replyTo != null) 6.dp else 0.dp),
                    )
                }
                Text(
                    text = formatClockTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationInputBar(
    sendText: String,
    onSendTextChange: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: MessageDto?,
    onCancelReply: () -> Unit,
) {
    Column {
        if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val replyUsername = replyingTo.profiles?.username ?: "kullanıcı"
                val preview = replyingTo.content?.takeIf { it.isNotBlank() } ?: "Görsel"
                Text(
                    text = "$replyUsername kullanıcısına yanıt veriyorsun: \"$preview\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Filled.Close, contentDescription = "Yanıtlamayı iptal et")
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = sendText,
                onValueChange = onSendTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Bir mesaj yaz...") },
            )
            IconButton(onClick = onSend, enabled = sendText.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
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
