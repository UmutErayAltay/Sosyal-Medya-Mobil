package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingOlder by viewModel.loadingOlder.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val conversationInfo by viewModel.conversationInfo.collectAsState()
    val sendText by viewModel.sendText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val myUserId by viewModel.myUserId.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()

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
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (info?.isGroup == true) Icons.Filled.Groups else Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = info?.name ?: "Konuşma",
                            style = MaterialTheme.typography.titleMedium,
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
                onSend = { viewModel.send(context) },
                replyingTo = replyingTo,
                onCancelReply = viewModel::clearReplyingTo,
                selectedImageUri = selectedImageUri,
                onImageSelected = viewModel::onImageSelected,
            )
        },
    ) { padding ->
        when {
            loading && messages.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && messages.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { viewModel.loadInitial() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            messages.isEmpty() -> CenteredMessage(padding) {
                Text(
                    text = "Henüz mesaj yok, ilk mesajı sen gönder",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    // Konuşma balonu köşe yuvarlaklığı — WhatsApp/Telegram gibi kendi
    // mesajının/gönderenin tarafına bakan köşe "sivriltilerek" hangi taraftan
    // geldiği anında ayırt edilir (renk zaten ayırıyordu, şekil ek bir ipucu).
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }
    // Optimistic ("local-" id'li, henüz sunucuya ulaşmamış) mesaj — davranış
    // AYNI kalıyor (ConversationViewModel.send() mantığına dokunulmadı), sadece
    // hafif saydamlıkla "gönderiliyor" hissi veriliyor (spesifikasyonda istenen).
    val isSending = message.id.startsWith("local-")
    val bubbleAlpha = if (isSending) 0.6f else 1f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .alpha(bubbleAlpha)
                // Uzun basınca "Yanıtla" — bu turda tek uzun-tık aksiyonu var,
                // ayrı bir menü/IconButton İCAT edilmedi (spesifikasyon: basit).
                .combinedClickable(onClick = {}, onLongClick = onReplyClick),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                val replyTo = message.replyTo
                if (replyTo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(contentColor.copy(alpha = 0.10f), MaterialTheme.shapes.extraSmall)
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(contentColor.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = replyTo.profiles?.username ?: "Bilinmeyen kullanıcı",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor,
                            )
                            Text(
                                text = replyTo.content?.takeIf { it.isNotBlank() }
                                    ?: if (!replyTo.imageUrl.isNullOrBlank()) "Görsel" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.85f),
                                maxLines = 1,
                            )
                        }
                    }
                }
                val imageUrl = message.imageUrl
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = if (replyTo != null) 6.dp else 0.dp)
                            .size(200.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                if (!message.content.isNullOrBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        modifier = Modifier.padding(top = if (replyTo != null || !imageUrl.isNullOrBlank()) 6.dp else 0.dp),
                    )
                }
                Text(
                    text = if (isSending) "gönderiliyor…" else formatClockTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
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
    selectedImageUri: Uri?,
    onImageSelected: (Uri?) -> Unit,
) {
    // CreatePostScreen'deki AYNI Photo Picker deseni — seçilen Uri ViewModel'e
    // (ConversationViewModel.selectedImageUri) bildirilir, bu composable
    // KENDİ local state'ini TUTMAZ.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> onImageSelected(uri) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column {
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
                    )
                    val replyUsername = replyingTo.profiles?.username ?: "kullanıcı"
                    val preview = replyingTo.content?.takeIf { it.isNotBlank() } ?: "Görsel"
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)) {
                        Text(
                            text = "$replyUsername kullanıcısına yanıt veriyorsun",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Filled.Close, contentDescription = "Yanıtlamayı iptal et")
                    }
                }
            }
            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    IconButton(
                        onClick = { onImageSelected(null) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Görseli kaldır",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Görsel ekle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = sendText,
                    onValueChange = onSendTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    placeholder = { Text("Bir mesaj yaz...") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    maxLines = 4,
                )
                val canSend = sendText.isNotBlank() || selectedImageUri != null
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
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
