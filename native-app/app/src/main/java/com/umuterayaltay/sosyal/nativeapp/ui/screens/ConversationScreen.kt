package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ui.components.MediaPickerSheet
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageReactionDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModelFactory
import kotlinx.coroutines.launch

// Madde 9 (kullanıcı raporu: chatte paylaşılan post'a tıklanmıyor) — backend'in
// (app/api_v1/messaging.py api_share_post() / app/messaging/sending.py)
// ürettiği paylaşılan-post mesaj formatı: "[not]\n\n📎 Paylaşılan post: /post/{id}\n\"...\"\n— @kullanıcı".
// Web'in chat.js'i (formatSharedPosts, bkz. app/static/js/chat.js ~213-266) AYNI
// prefiksi ayrıştırıp GERÇEK bir görsel kart'a çeviriyor (not ayrı, kart İÇİNDE
// görsel+yazar+içerik, "Gönderiyi Gör" gibi bir aksiyon metni YOK - kartın
// tamamı zaten tıklanabilir). ÖNCEKİ tur SADECE tıklanabilirlik eklemişti
// (sadece ID çıkarımı) - bu tur (madde 3) o karta BİREBİR native karşılığını
// tamamlıyor, bkz. SharedPostCard composable'ı.
private const val SHARED_POST_PREFIX = "📎 Paylaşılan post:"
private val SHARED_POST_ID_REGEX = Regex("/post/([\\w-]+)")
// Web'in postContent ayrıştırmasındaki `"..."` alıntısının AYNISI — backend
// içeriği `\"{content[:100]}\"` şeklinde tırnak içine alıyor (bkz.
// app/messaging/sending.py share_post_multiple()).
private val SHARED_POST_EXCERPT_REGEX = Regex("\"([^\"]*)\"")
// Web'in "— @username" satırının AYNISI (bkz. chat.js "author.startsWith('—')").
private val SHARED_POST_AUTHOR_REGEX = Regex("—\\s*@([\\w.]+)")

private fun extractSharedPostId(content: String?): String? {
    if (content.isNullOrBlank() || !content.contains(SHARED_POST_PREFIX)) return null
    return SHARED_POST_ID_REGEX.find(content)?.groupValues?.getOrNull(1)
}

/** Madde 3 — kart için gereken TÜM alanları TEK seferde ayrıştırır: kullanıcının
 * paylaşırken eklediği opsiyonel not (kartın DIŞINDA, web'in `.share-note`'u
 * gibi normal metin olarak gösterilir), postun ilk 100 karakterlik alıntısı ve
 * yazarın kullanıcı adı. */
private data class SharedPostInfo(
    val postId: String,
    val note: String?,
    val excerpt: String?,
    val author: String?,
)

private fun extractSharedPostInfo(content: String?): SharedPostInfo? {
    val postId = extractSharedPostId(content) ?: return null
    val safeContent = content!!
    val note = safeContent.substringBefore(SHARED_POST_PREFIX).trim().takeIf { it.isNotEmpty() }
    val excerpt = SHARED_POST_EXCERPT_REGEX.find(safeContent)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    val author = SHARED_POST_AUTHOR_REGEX.find(safeContent)?.groupValues?.getOrNull(1)
    return SharedPostInfo(postId, note, excerpt, author)
}

/**
 * Tek bir konuşma ekranı — mesaj geçmişi (eskiden yeniye, yukarı kaydırınca
 * daha eski sayfa) + metin gönderme (+ yanıtlama) + Faz 5 Dalga 1B mesaj
 * gelişmiş işlemleri (düzenle/sil/tepki/sabitle/ilet/sohbeti sessize al/ara).
 * Backend sözleşmesi: app/api_v1/messaging.py (bkz. ConversationViewModel
 * docstring'i — basit polling ile "neredeyse canlı", gerçek Supabase
 * Realtime YOK sadece INSERT'ler için).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onManageGroupClick: () -> Unit,
    onNavigateToMessageSearch: () -> Unit,
    // Madde 9 (kullanıcı raporu: paylaşılan post'a tıklanmıyor) — VARSAYILAN
    // DEĞERLİ ({}), diğer opsiyonel callback'lerle AYNI gerekçe (PostCard.kt
    // deseni): henüz bağlanmamış çağrı yerleri (varsa) değişmeden derlenmeye
    // devam eder. AppNavHost.kt'de PostDetailScreen'in ZATEN var olan
    // "postDetail/{postId}" route'una bağlanır.
    onNavigateToPostDetail: (String) -> Unit = {},
    // Grup sesli/görüntülü arama (native görev — LiveKit) — VARSAYILAN
    // DEĞERLİ ({}), onNavigateToPostDetail ile AYNI gerekçe (henüz
    // bağlanmamış çağrı yerleri değişmeden derlenmeye devam eder).
    // AppNavHost.kt'de yeni "call/{conversationId}" route'una bağlanır.
    onCallClick: () -> Unit = {},
    // 1:1 sesli/görüntülü arama (native görev — WebRTC + Supabase Realtime
    // broadcast, LiveKit grup aramasından TAMAMEN AYRI) — SADECE isGroup==false
    // iken görünen İKİ AYRI ikon (sesli+görüntülü, bkz. aşağıdaki TopAppBar
    // actions — kullanıcı raporu: "sadece görüntülü arama var normal arama
    // yok", TEK ikonun her zaman video başlattığı önceki MVP kararı yerine
    // web'deki gibi ayrı butonlara geçildi). otherUserId backend'in
    // ConversationInfoDto'sunda YOK (bkz. app/api_v1/messaging.py
    // api_message_conversation_detail() — "conversation" JSON'ı SADECE
    // name/avatar_url döner, other_user id'sini DÖNMÜYOR ve backend'e
    // DOKUNULMAYACAK, görev kısıtı) — bu yüzden mesaj listesindeki İLK
    // "benden olmayan" mesajın sender_id'si kullanılır (bkz. aşağıdaki
    // otherUserId remember bloğu). Karşı taraf HİÇ mesaj göndermemişse
    // (tamamen tek taraflı yeni bir sohbet) otherUserId çözülemez ve ikonlar
    // gizlenir — bilinçli MVP sınırı, raporda belirtildi.
    onOneOnOneCallClick: (otherUserId: String, otherName: String, otherAvatarUrl: String?, isVideo: Boolean) -> Unit = { _, _, _, _ -> },
    onSessionExpired: () -> Unit,
    viewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModelFactory(conversationId),
    ),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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

    // ---- Faz 5 Dalga 1B state'leri ----
    val pinnedMessage by viewModel.pinnedMessage.collectAsState()
    val selectedMessage by viewModel.selectedMessage.collectAsState()
    val forwardTargets by viewModel.forwardTargets.collectAsState()
    val forwardTargetsLoading by viewModel.forwardTargetsLoading.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()

    // Bu ekrana ÖZGÜ, ViewModel'e taşınmayan saf UI state'i (arama modu
    // açık/kapalı, taşma menüsü, düzenleme diyaloğu, ilet sheet'i) —
    // FeedScreen'deki filtre çipleri gibi diğer ekranlardaki AYNI ayrım
    // (kalıcı/paylaşılan state ViewModel'de, geçici UI state composable'da).
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showForwardSheet by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageDto?>(null) }
    var editText by remember { mutableStateOf("") }

    // 1:1 arama ikonu için — bkz. yukarıdaki onOneOnOneCallClick parametresi
    // yorumu (backend other_user id döndürmediği için mesaj listesinden
    // türetiliyor).
    val otherUserIdForCall = remember(messages, myUserId) {
        messages.firstOrNull { it.senderId.isNotBlank() && it.senderId != myUserId }?.senderId
    }

    val actionsSheetState = rememberModalBottomSheetState()
    val forwardSheetState = rememberModalBottomSheetState()

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
            Column {
                TopAppBar(
                    title = {
                        if (searchMode) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.searchInConversation(it)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Bu sohbette ara...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                        } else {
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
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (searchMode) {
                                searchMode = false
                                searchQuery = ""
                                viewModel.clearSearchResults()
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    actions = {
                        if (!searchMode) {
                            IconButton(onClick = { searchMode = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Sohbette ara")
                            }
                            if (conversationInfo?.isGroup == true) {
                                // Grup sesli/görüntülü arama (native görev — LiveKit) —
                                // SADECE grup konuşmalarında görünür (1:1'de bu
                                // endpoint/ekran hiç kullanılmaz, bkz. CallScreen
                                // dosya yorumu).
                                IconButton(onClick = onCallClick) {
                                    Icon(Icons.Filled.VideoCall, contentDescription = "Sesli/görüntülü arama")
                                }
                                IconButton(onClick = onManageGroupClick) {
                                    Icon(Icons.Filled.Groups, contentDescription = "Grubu Yönet")
                                }
                            } else if (conversationInfo?.isGroup == false && otherUserIdForCall != null) {
                                // 1:1 sesli/görüntülü arama (native görev — WebRTC +
                                // Supabase Realtime broadcast) — grup ikonuyla AYNI
                                // satırda, birbirini DIŞLAYAN koşulla (bkz. yukarıdaki
                                // if dalı). Kullanıcı raporu ("sadece görüntülü arama
                                // var normal arama yok") üzerine TEK video-only ikon
                                // yerine web'deki gibi AYRI sesli/görüntülü butonlara
                                // geçildi — ikisi de AYNI otherUserId/name/avatar'ı
                                // taşır, sadece isVideo farklı.
                                IconButton(onClick = {
                                    onOneOnOneCallClick(
                                        otherUserIdForCall,
                                        conversationInfo?.name ?: "Kullanıcı",
                                        conversationInfo?.avatarUrl,
                                        false,
                                    )
                                }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Sesli arama")
                                }
                                IconButton(onClick = {
                                    onOneOnOneCallClick(
                                        otherUserIdForCall,
                                        conversationInfo?.name ?: "Kullanıcı",
                                        conversationInfo?.avatarUrl,
                                        true,
                                    )
                                }) {
                                    Icon(Icons.Filled.VideoCall, contentDescription = "Görüntülü arama")
                                }
                            }
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Diğer")
                            }
                            DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isMuted) "Sohbeti sessize almayı kaldır" else "Sohbeti sessize al") },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.toggleMute()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Tüm sohbetlerde ara") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNavigateToMessageSearch()
                                    },
                                )
                            }
                        }
                    },
                )
                // Sabitlenmiş mesaj banner'ı — konuşmanın "ortak hafızası" (bkz.
                // backend pin yetki notu: gönderen değil HERHANGİ bir katılımcı
                // sabitleyebilir), TopAppBar'ın hemen altında sabit kalır.
                pinnedMessage?.let { pinned ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = pinned.content?.takeIf { it.isNotBlank() } ?: "Sabitlenmiş mesaj",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!searchMode) {
                ConversationInputBar(
                    sendText = sendText,
                    onSendTextChange = viewModel::onSendTextChange,
                    onSend = { viewModel.send(context) },
                    onSendGif = { url -> viewModel.send(context, gifUrl = url) },
                    onSendSticker = { id -> viewModel.send(context, stickerId = id) },
                    replyingTo = replyingTo,
                    onCancelReply = viewModel::clearReplyingTo,
                    selectedImageUri = selectedImageUri,
                    onImageSelected = viewModel::onImageSelected,
                )
            }
        },
    ) { padding ->
        if (searchMode) {
            ConversationSearchResults(
                padding = padding,
                query = searchQuery,
                results = searchResults,
                loading = searchLoading,
                onResultClick = { result ->
                    val idx = messages.indexOfFirst { it.id == result.id }
                    searchMode = false
                    if (idx >= 0) {
                        coroutineScope.launch { listState.animateScrollToItem(idx) }
                    }
                },
            )
        } else {
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
                            onLongPress = { viewModel.selectMessage(message) },
                            onPostClick = onNavigateToPostDetail,
                        )
                    }
                }
            }
        }
    }

    // ---- Faz 5 Dalga 1B: mesaj aksiyon sheet'i ----
    // showForwardSheet iken aksiyon listesi GİZLENİR (aynı anda iki sheet
    // üst üste açılmasın diye) — selectedMessage forward tamamlanana/iptal
    // edilene kadar BİLEREK temizlenmez (ForwardTargetScreen'in hangi
    // mesajı ileteceğini bilmesi gerekiyor).
    if (selectedMessage != null && !showForwardSheet) {
        val msg = selectedMessage!!
        MessageActionsSheet(
            message = msg,
            isMine = myUserId != null && msg.senderId == myUserId,
            sheetState = actionsSheetState,
            onDismiss = { viewModel.clearSelectedMessage() },
            onReply = {
                viewModel.setReplyingTo(msg)
                viewModel.clearSelectedMessage()
            },
            onEdit = {
                editingMessage = msg
                editText = msg.content ?: ""
                viewModel.clearSelectedMessage()
            },
            onDelete = {
                viewModel.deleteMessage(msg.id)
            },
            onForward = {
                showForwardSheet = true
                viewModel.loadForwardTargets()
            },
            onPin = { viewModel.pinMessage(msg.id) },
            onReact = { emoji -> viewModel.reactToMessage(msg.id, emoji) },
        )
    }

    if (showForwardSheet && selectedMessage != null) {
        val msg = selectedMessage!!
        ForwardTargetScreen(
            targets = forwardTargets,
            loading = forwardTargetsLoading,
            sheetState = forwardSheetState,
            onDismiss = {
                showForwardSheet = false
                viewModel.clearSelectedMessage()
            },
            onTargetSelected = { target ->
                viewModel.forwardMessage(msg.id, target.id)
                showForwardSheet = false
            },
        )
    }

    // ---- Faz 5 Dalga 1B: mesaj düzenleme diyaloğu ----
    // Ayrı bir tam ekran/sheet YERİNE basit bir AlertDialog — sadece TEK
    // metin alanı düzenleniyor, ConversationInputBar'ın karmaşık (görsel/
    // yanıt) durumunu burada TEKRARLAMAYA gerek yok.
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Mesajı düzenle") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val content = editText.trim()
                        if (content.isNotEmpty()) {
                            viewModel.editMessage(msg.id, content)
                        }
                        editingMessage = null
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun ConversationSearchResults(
    padding: PaddingValues,
    query: String,
    results: List<MessageSearchResultDto>,
    loading: Boolean,
    onResultClick: (MessageSearchResultDto) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            query.trim().length < 2 -> Text(
                text = "Aramak için en az 2 karakter yazın",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
            results.isEmpty() -> Text(
                text = "Sonuç bulunamadı",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResultClick(result) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = if (result.mine) "Sen" else (result.sender ?: "?"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = result.content ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageDto,
    isMine: Boolean,
    onLongPress: () -> Unit,
    // Madde 9: VARSAYILAN DEĞERLİ ({}) - diğer opsiyonel callback'lerle AYNI
    // gerekçe (bkz. ConversationScreen fonksiyon imzası yorumu).
    onPostClick: (String) -> Unit = {},
) {
    // Madde 3 (Instagram tarzı görsel kart) — SADECE ID DEĞİL, kartın ihtiyaç
    // duyduğu not/alıntı/yazar da TEK seferde ayrıştırılır (bkz. yukarıdaki
    // SharedPostInfo/extractSharedPostInfo).
    val sharedPostInfo = extractSharedPostInfo(message.content)
    val sharedPostId = sharedPostInfo?.postId
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
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Card(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .alpha(bubbleAlpha)
                    // Uzun basınca aksiyon menüsü (Faz 5 Dalga 1B) — eskiden
                    // TEK aksiyon (yanıtla) vardı, şimdi MessageActionsSheet
                    // açılıyor (yanıtla dahil TÜM aksiyonlar orada). Madde 9:
                    // kısa tıklama artık paylaşılan-post mesajlarında post
                    // detayına gider (sharedPostId null ise ÖNCEKİ davranış -
                    // hiçbir şey olmaz - AYNEN korunur).
                    .combinedClickable(
                        onClick = { sharedPostId?.let(onPostClick) },
                        onLongClick = onLongPress,
                    ),
                shape = bubbleShape,
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (message.isForwarded) {
                        Text(
                            text = "İletildi",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
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
                    if (sharedPostInfo != null) {
                        // Madde 3 — backend paylaşırken image_url'e postun İLK
                        // görselini zaten koyuyor (bkz. sending.py share_post_multiple()),
                        // bu yüzden GENEL imageUrl bloğu (üstte, normal görsel
                        // mesajları için) burada BİLEREK atlanıyor — görsel
                        // SharedPostCard'ın KENDİ İÇİNDE, kartın üst kısmında
                        // gösterilir (web'in .shared-card-img'iyle AYNI konum).
                        if (!sharedPostInfo.note.isNullOrBlank()) {
                            Text(
                                text = sharedPostInfo.note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                modifier = Modifier.padding(
                                    top = if (replyTo != null) 6.dp else 0.dp,
                                    bottom = 6.dp,
                                ),
                            )
                        }
                        SharedPostCard(
                            imageUrl = imageUrl,
                            excerpt = sharedPostInfo.excerpt,
                            author = sharedPostInfo.author,
                            contentColor = contentColor,
                            modifier = Modifier.padding(
                                top = if (replyTo != null && sharedPostInfo.note.isNullOrBlank()) 6.dp else 0.dp,
                            ),
                        )
                    } else {
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
                        // Sticker — kullanıcı raporu: mesajlarda hiç görünmüyordu (kök
                        // neden: MessageDto.sticker bilerek Any? tipindeydi, bkz.
                        // ApiModels.kt). Görsel ekiyle AYNI desende (AsyncImage), ama
                        // gerçek bir sticker/emoji boyutunda — tam ekran görsel GİBİ
                        // BÜYÜK değil. Görsel VE sticker aynı anda gelirse (backend bunu
                        // engellemiyor) ikisi de gösterilir, çakışma riski yok.
                        val stickerUrl = message.sticker?.imageUrl
                        if (!stickerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = stickerUrl,
                                contentDescription = "Çıkartma",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .padding(top = if (replyTo != null || !imageUrl.isNullOrBlank()) 6.dp else 0.dp)
                                    .size(96.dp),
                            )
                        }
                        if (!message.content.isNullOrBlank()) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor,
                                modifier = Modifier.padding(
                                    top = if (replyTo != null || !imageUrl.isNullOrBlank() || !stickerUrl.isNullOrBlank()) 6.dp else 0.dp,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (message.editedAt != null) {
                            Text(
                                text = "düzenlendi",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Text(
                            text = if (isSending) "gönderiliyor…" else formatClockTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            // Tepki çipleri — baloncuğun ALTINDA (backend her tepkiyi {reaction,
            // count, mine} özetine indirgiyor, bkz. ApiModels.kt MessageReactionDto).
            // Yatay kaydırma FlowRow yerine tercih edildi (compose-foundation
            // sürüm bağımlılığı olmadan, her zaman kullanılabilir bir API).
            val reactions = message.reactions
            if (!reactions.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reactions.forEach { reaction -> ReactionChip(reaction) }
                }
            }
        }
    }
}

/**
 * Madde 3 (Instagram tarzı görsel kart) — PostCard.kt'deki RepostEmbedCard
 * (kenarlıklı/yuvarlak köşeli Column) ile AYNI görsel dil, ama KOPYALANMADI:
 * burada görsel varsa EN ÜSTTE (web'in `.shared-card-img`'i, `insertAdjacentElement`
 * ile kartın İÇİNE en başa eklenen görselle AYNI konum), altında yazar (küçük/
 * ikincil - web'in son hâlinde de yazar İKİNCİL) + postun alıntısı. Web'deki
 * gibi "Gönderiyi Gör" türü bir aksiyon metni YOK - MessageBubble'daki
 * `combinedClickable(onClick = { sharedPostId?.let(onPostClick) })` zaten
 * TÜM baloncuğu (bu kart dahil) tıklanabilir yapıyor, bu yüzden bu composable
 * KENDİ clickable'ını EKLEMEZ (çift jest algılayıcı çakışmasın diye).
 */
@Composable
private fun SharedPostCard(
    imageUrl: String?,
    excerpt: String?,
    author: String?,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, contentColor.copy(alpha = 0.3f), MaterialTheme.shapes.medium),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
        if (!author.isNullOrBlank() || !excerpt.isNullOrBlank()) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (!author.isNullOrBlank()) {
                    Text(
                        text = "@$author",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
                if (!excerpt.isNullOrBlank()) {
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        modifier = Modifier.padding(top = if (!author.isNullOrBlank()) 2.dp else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(reaction: MessageReactionDto) {
    val background = if (reaction.mine) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = reaction.reaction, style = MaterialTheme.typography.labelSmall)
        if (reaction.count > 1) {
            Text(
                text = " ${reaction.count}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationInputBar(
    sendText: String,
    onSendTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendGif: (String) -> Unit,
    onSendSticker: (String) -> Unit,
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

    // GIF/Sticker seçici (Faz 5 Dalga 3B) — görsel-ekle butonunun AKSİNE
    // önizleme YOK, MediaPickerSheet'te seçim yapılır yapılmaz sheet kapanır
    // ve mesaj DOĞRUDAN gönderilir (bkz. MediaPickerSheet dosya yorumu).
    var showMediaPicker by remember { mutableStateOf(false) }
    val mediaPickerSheetState = rememberModalBottomSheetState()
    if (showMediaPicker) {
        MediaPickerSheet(
            sheetState = mediaPickerSheetState,
            onDismiss = { showMediaPicker = false },
            onGifSelected = { url ->
                showMediaPicker = false
                onSendGif(url)
            },
            onStickerSelected = { id ->
                showMediaPicker = false
                onSendSticker(id)
            },
        )
    }

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
                IconButton(onClick = { showMediaPicker = true }) {
                    Icon(
                        Icons.Filled.Gif,
                        contentDescription = "GIF veya çıkartma ekle",
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
