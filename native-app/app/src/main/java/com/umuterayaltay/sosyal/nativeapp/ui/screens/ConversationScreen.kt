package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ui.components.FullscreenImageViewer
import com.umuterayaltay.sosyal.nativeapp.ui.components.FullscreenVideoViewer
import com.umuterayaltay.sosyal.nativeapp.ui.components.LinkPreviewCard
import com.umuterayaltay.sosyal.nativeapp.ui.components.MediaPickerSheet
import com.umuterayaltay.sosyal.nativeapp.ui.components.buildUrlOnlyAnnotatedString
import com.umuterayaltay.sosyal.nativeapp.ui.components.extractFirstUrl
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageReactionDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.network.StickerDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ConversationViewModelFactory
import kotlinx.coroutines.delay
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
    // AppNavHost.kt'de "call/{conversationId}/{isVideo}" route'una bağlanır.
    // 2026-08-09 (kullanıcı raporu: "grupta sesli arama butonu yok, sadece
    // görüntülü var") — [isVideo] parametresi eklendi, 1:1'in AYNI ayrı
    // sesli/görüntülü buton çifti deseni burada da uygulanıyor.
    onCallClick: (isVideo: Boolean) -> Unit = {},
    // 1:1 sesli/görüntülü arama (native görev — WebRTC + Supabase Realtime
    // broadcast, LiveKit grup aramasından TAMAMEN AYRI) — SADECE isGroup==false
    // iken görünen İKİ AYRI ikon (sesli+görüntülü, bkz. aşağıdaki TopAppBar
    // actions — kullanıcı raporu: "sadece görüntülü arama var normal arama
    // yok", TEK ikonun her zaman video başlattığı önceki MVP kararı yerine
    // web'deki gibi ayrı butonlara geçildi). otherUserId GÖRÜNTÜLEME/kimlik
    // amaçlı KALDI (mesaj listesinden türetiliyor, bkz. aşağıdaki
    // otherUserIdForCall), ama sinyal YÖNLENDİRMESİ artık otherCallTopic ile
    // yapılıyor — backend'in ürettiği tahmin edilemez HMAC kanal adı (bkz.
    // ConversationInfoDto.otherCallTopic, app/realtime_topics.py). Supabase
    // private kanal yetkilendirmesi platform tarafında bozuk olduğu için
    // (2026-08-07) bu backend değişikliği artık ZORUNLU — önceki "backend'e
    // dokunulmayacak" MVP kısıtı bu güvenlik gerekliliğiyle geçersiz oldu.
    onOneOnOneCallClick: (
        otherUserId: String,
        otherCallTopic: String,
        otherName: String,
        otherAvatarUrl: String?,
        isVideo: Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
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
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()

    // Sesli mesaj kaydı için RECORD_AUDIO izni — OneOnOneCallScreen/CallScreen'deki
    // AYNI çalışma-zamanı izin deseni (manifest'te ZATEN deklare edilmiş, bkz.
    // AndroidManifest.xml, ama API 23+'ta ayrıca kullanıcıdan İZİN İSTENMELİ).
    // Bu ekran görüntülü arama ekranlarının AKSİNE açılışta izin İSTEMEZ —
    // SADECE mikrofon butonuna basılı tutulunca, tam o an gerekince istenir
    // (kullanıcı hiç sesli mesaj kaydetmeden sohbeti açtığında gereksiz bir
    // izin diyalogu görmesin diye).
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) viewModel.startRecording(context)
    }

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

    // 1:1 arama ikonu için GÖRÜNTÜLEME/kimlik amaçlı — sinyal YÖNLENDİRMESİ
    // artık conversationInfo.otherCallTopic ile yapılıyor (backend'den gelir,
    // bkz. onOneOnOneCallClick parametresi yorumu). Bu değer hâlâ mesaj
    // listesinden türetiliyor (backend other_user id döndürmüyor) — ama arama
    // ikonlarının gösterilip gösterilmeyeceğine artık otherCallTopic karar
    // veriyor (bkz. aşağıdaki if koşulu), bu sadece UI'da isim/görüntüleme.
    // 2026-08-09: ARTIK ÖNCE backend'in döndürdüğü other_user_id kullanılıyor.
    // Eski "mesaj listesinden türet" yolu SADECE fallback olarak duruyor
    // (backend deploy'u henüz gitmemiş olabilir): o yol, karşı taraf HENÜZ
    // HİÇ mesaj göndermemişse null dönüyordu ve arama butonları bu yüzden
    // görünmüyordu — kullanıcı raporu "aynı sohbette emülatörde arama butonu
    // var, telefonumda yok" TAM OLARAK buydu (butonun görünürlüğü, karşı
    // tarafın size mesaj atmış olmasına bağlıydı; hiç mesajlaşılmamış
    // sohbetlerde İKİ TARAFTA DA yoktu).
    val otherUserIdForCall = remember(messages, myUserId, conversationInfo) {
        conversationInfo?.otherUserId?.takeIf { it.isNotBlank() }
            ?: messages.firstOrNull { it.senderId.isNotBlank() && it.senderId != myUserId }?.senderId
    }

    // Karşı tarafın HESABI SİLİNMİŞ mi (2026-08-09, kullanıcı onayı ile
    // eklendi). Backend 1:1 sohbette other_call_topic/other_user_id'yi SADECE
    // karşı tarafın profili DURUYORSA doldurur: `conversation_participants.
    // user_id -> profiles` FK'si ON DELETE CASCADE olduğu için hesap silinince
    // hem katılımcı satırı hem o kişinin mesajları uçar, geriye tek katılımcılı
    // bir sohbet kalır. Böyle bir sohbette arama butonları ZATEN gizleniyordu
    // ama SEBEBİ hiç belli olmuyordu ("buton sebepsiz kayboldu" görünümü) —
    // artık başlıkta açıkça yazıyor ve mesaj yazma alanı kapatılıyor
    // (gönderilecek kimse yok). Mesaj GEÇMİŞİ görünmeye devam eder.
    val otherAccountDeleted = remember(conversationInfo) {
        val info = conversationInfo
        info != null && !info.isGroup && info.otherCallTopic.isNullOrBlank()
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

    // Kullanıcı raporu (2026-08-08): konuşma açılınca en üstten en alta doğru
    // GÖRÜNÜR BİR KAYMA oluyordu — çünkü LazyColumn ilk kompozisyonda index 0
    // (en üst) ile başlıyor, aşağıdaki effect ise `animateScrollToItem` ile
    // YUMUŞAK/ANİMASYONLU bir kaydırma tetikliyordu; bu ilk yüklemede de
    // (mesajlar boştan doluya geçtiğinde) çalıştığı için konuşma her açılışta
    // yukarıdan aşağı "kayarak" beliriyordu. Artık İLK doluşta `scrollToItem`
    // (ANİNDEN, animasyonsuz) kullanılıyor — kullanıcı sohbeti DOĞRUDAN en
    // alttan (en yeni mesajdan) görüyor. Sonraki her yeni mesaj (gönderilen ya
    // da polling ile gelen) yine `animateScrollToItem` ile YUMUŞAK kayıyor —
    // bu normal/istenen sohbet davranışı, SADECE ilk açılış anlık olmalı.
    var hasScrolledToInitialBottom by remember { mutableStateOf(false) }
    // Kullanıcı raporu (devam): "en alttan 2. mesajda kalıyor" — LazyColumn'da
    // `loading_older` spinner'ı (aşağıdaki `if (loadingOlder) item(...)`)
    // mesaj listesinden ÖNCE eklendiğinde TÜM mesaj index'lerini +1 kaydırır.
    // Hedef index'i buna göre HESAPLAMAK gerekiyordu — sabit `messages.size - 1`
    // spinner varken 1 KISA kalıyordu. Asıl kök neden ise aşağıdaki
    // pagination-tetikleyicisiydi (bkz. o LaunchedEffect'in yeni yorumu).
    val loadingOlderOffset = if (loadingOlder) 1 else 0
    // Anahtar SADECE son mesajın id'si, bu yüzden loadOlder()'ın listenin
    // BAŞINA eklediği eski sayfalar burayı TETİKLEMEZ.
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        val targetIndex = messages.size - 1 + loadingOlderOffset
        if (!hasScrolledToInitialBottom) {
            listState.scrollToItem(targetIndex)
            hasScrolledToInitialBottom = true
        } else {
            listState.animateScrollToItem(targetIndex)
        }
    }

    // Listenin başına (yukarı kaydırınca) yaklaşınca daha eski sayfayı yükle —
    // loadOlder() zaten hasMore/loading guard'lı, tekrar tetiklenmesi zararsız.
    // ÖNEMLİ: `hasScrolledToInitialBottom &&` guard'ı SONRADAN eklendi —
    // konuşma İLK açıldığında `listState.firstVisibleItemIndex` başlangıçta
    // 0'dır (henüz en alta kaydırmadık), bu da bu effect'i SAHTE bir
    // "kullanıcı yukarı kaydırdı" sinyali sanıp `loadOlder()`'ı YANLIŞLIKLA
    // tetikliyordu — bu da `loading_older` item'ını tam scrollToItem çalışırken
    // araya sokup hedef index'i kaydırıyor, "en alttan 2. mesajda kalma"
    // bug'ına yol açıyordu. Artık İLK kaydırma tamamlanmadan pagination HİÇ
    // tetiklenmiyor.
    LaunchedEffect(listState, hasMore, hasScrolledToInitialBottom) {
        if (!hasScrolledToInitialBottom) return@LaunchedEffect
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
                                    text = info?.name
                                        ?: if (otherAccountDeleted) "Hesap silinmiş" else "Konuşma",
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
                                // dosya yorumu). 1:1'deki AYRI sesli/görüntülü
                                // buton çiftiyle AYNI desen (kullanıcı raporu:
                                // "sadece görüntülü arama var normal arama yok").
                                IconButton(onClick = { onCallClick(false) }) {
                                    Icon(Icons.Filled.Call, contentDescription = "Sesli arama")
                                }
                                IconButton(onClick = { onCallClick(true) }) {
                                    Icon(Icons.Filled.VideoCall, contentDescription = "Görüntülü arama")
                                }
                                IconButton(onClick = onManageGroupClick) {
                                    Icon(Icons.Filled.Groups, contentDescription = "Grubu Yönet")
                                }
                            } else if (
                                conversationInfo?.isGroup == false &&
                                otherUserIdForCall != null &&
                                !conversationInfo?.otherCallTopic.isNullOrBlank()
                            ) {
                                // 1:1 sesli/görüntülü arama (native görev — WebRTC +
                                // Supabase Realtime broadcast) — grup ikonuyla AYNI
                                // satırda, birbirini DIŞLAYAN koşulla (bkz. yukarıdaki
                                // if dalı). Kullanıcı raporu ("sadece görüntülü arama
                                // var normal arama yok") üzerine TEK video-only ikon
                                // yerine web'deki gibi AYRI sesli/görüntülü butonlara
                                // geçildi — ikisi de AYNI otherUserId/topic/name/avatar'ı
                                // taşır, sadece isVideo farklı. otherCallTopic null/boş
                                // olamaz burada (koşulda elendi) — !! güvenli.
                                val callTopic = conversationInfo!!.otherCallTopic!!
                                IconButton(onClick = {
                                    onOneOnOneCallClick(
                                        otherUserIdForCall,
                                        callTopic,
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
                                        callTopic,
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
            if (!searchMode && otherAccountDeleted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Bu kullanıcının hesabı silinmiş, mesaj gönderilemez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!searchMode) {
                ConversationInputBar(
                    sendText = sendText,
                    onSendTextChange = viewModel::onSendTextChange,
                    onSend = { viewModel.send(context) },
                    onSendGif = { url -> viewModel.send(context, gifUrl = url) },
                    onSendSticker = { sticker ->
                        viewModel.send(context, stickerId = sticker.id, stickerImageUrl = sticker.imageUrl)
                    },
                    replyingTo = replyingTo,
                    onCancelReply = viewModel::clearReplyingTo,
                    selectedImageUri = selectedImageUri,
                    onImageSelected = viewModel::onImageSelected,
                    selectedVideoUri = selectedVideoUri,
                    onVideoSelected = viewModel::onVideoSelected,
                    isRecordingAudio = isRecordingAudio,
                    recordingElapsedMs = recordingElapsedMs,
                    onStartRecording = {
                        if (hasAudioPermission) {
                            viewModel.startRecording(context)
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecordingAndSend = { viewModel.stopRecordingAndSend(context) },
                    onCancelRecording = viewModel::cancelRecording,
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
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        // SADECE listenin son öğesi (yeni gönderilen/gelen mesaj)
                        // giriş animasyonuyla belirir — geçmiş sayfalar (loadOlder
                        // ile başa eklenen eski mesajlar VEYA konuşma ilk açılışında
                        // yüklenen tüm geçmiş) HER recompose'da yeniden animasyonla
                        // "titremesin" diye (bkz. AnimatedMessageBubble yorumu).
                        if (index == messages.lastIndex) {
                            AnimatedMessageBubble(
                                message = message,
                                isMine = myUserId != null && message.senderId == myUserId,
                                onLongPress = { viewModel.selectMessage(message) },
                                onPostClick = onNavigateToPostDetail,
                            )
                        } else {
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

/**
 * Animasyon turu (3. kısım, en kritik) — yeni gönderilen (optimistic "local-"
 * id) VEYA polling ile gelen yeni mesaj balonunun listenin SONUNA eklenirken
 * fade+slide-up ile belirmesi. ConversationViewModel'in optimistic-send
 * MANTIĞINA (send()/replaceOptimisticMessage() vb.) hiç dokunulmadı — bu
 * SADECE görsel bir sarmalayıcı, state/sıralama davranışı AYNEN korunuyor.
 * remember(message.id) ile id her değiştiğinde (yeni mesaj VEYA "local-"
 * geçici id'nin sunucudan dönen gerçek id ile değiştirilmesi, bkz.
 * ConversationViewModel satır ~404) animasyon YENİDEN oynar — ikinci durumda
 * bu istenen bir yan etki: "gönderiliyor…" durumundan gerçek mesaja geçişi
 * görsel olarak da hafifçe vurgular.
 */
@Composable
private fun AnimatedMessageBubble(
    message: MessageDto,
    isMine: Boolean,
    onLongPress: () -> Unit,
    onPostClick: (String) -> Unit,
) {
    val visibleState = remember(message.id) { MutableTransitionState(false) }
    LaunchedEffect(message.id) { visibleState.targetState = true }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 5 },
    ) {
        MessageBubble(
            message = message,
            isMine = isMine,
            onLongPress = onLongPress,
            onPostClick = onPostClick,
        )
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

    // 2026-08-09 (kullanıcı isteği: "sohbetteki videoya/görsele tıklayınca
    // büyüsün") — bkz. ui/components/FullscreenImageViewer.kt/
    // FullscreenVideoViewer.kt yorumu.
    var showFullscreenImage by remember { mutableStateOf(false) }
    var showFullscreenVideo by remember { mutableStateOf(false) }

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
                            videoUrl = message.videoUrl,
                            excerpt = sharedPostInfo.excerpt,
                            author = sharedPostInfo.author,
                            contentColor = contentColor,
                            modifier = Modifier.padding(
                                top = if (replyTo != null && sharedPostInfo.note.isNullOrBlank()) 6.dp else 0.dp,
                            ),
                        )
                    } else {
                        if (!imageUrl.isNullOrBlank()) {
                            // 2026-08-09 (kullanıcı isteği: "büyüsün") — `combinedClickable`
                            // (düz `clickable` DEĞİL) kullanıldı ki uzun-basma
                            // (mesaj aksiyon menüsü) dış balonun `onLongPress`'iyle
                            // AYNI şekilde görsele basılınca da ÇALIŞSIN — nested
                            // bir `clickable` bunu SESSİZCE kırardı (bkz. PostCard.kt'
                            // deki AYNI gerekçe).
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(top = if (replyTo != null) 6.dp else 0.dp)
                                    .size(200.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .combinedClickable(
                                        onClick = { showFullscreenImage = true },
                                        onLongClick = onLongPress,
                                    ),
                            )
                        }
                        // 2026-08-08: video mesajı — image_url ile AYNI desen/konum,
                        // AYRI bir kolon (video_url) olduğu için ikisi TEORİK olarak
                        // birlikte gelebilir ama pratikte tek seferde biri gönderilir
                        // (bkz. ConversationInputBar'da karşılıklı dışlama).
                        val videoUrl = message.videoUrl
                        if (!videoUrl.isNullOrBlank()) {
                            // Kullanıcı raporu (2026-08-08): "video çok küçük görünüyor,
                            // etrafı kırpılı gibi" — kök neden RESIZE_MODE_ZOOM'du: bu
                            // mod videoyu KUTUYU DOLDURACAK kadar büyütüp TAŞAN kısmı
                            // kırpar (CSS object-fit:cover gibi). Telefon videoları
                            // genelde DİKEY (9:16) çekiliyor, kutu ise YATAY (220x160)
                            // orandaydı — dikey videoyu yatay kutuya "cover" ile
                            // sığdırmak videonun SADECE dar, ortadaki bir dilimini
                            // (aşırı yakınlaştırılmış/kırpılmış) gösteriyordu. RESIZE_MODE_
                            // FIT'e geçildi (videonun TAMAMI görünür, gerekirse üst/alt
                            // boşluk bırakılır — kırpma YOK) + kutu daha dikey bir orana
                            // (200x260) çekildi, telefon videolarıyla daha az boşluk kalsın.
                            // 2026-08-09 (kullanıcı isteği: "büyüsün") — MessageVideoPlayer'ın
                            // KENDİ `useController=true` PlayerView'ı bir AndroidView
                            // olduğu için üstüne Compose clickable DOKUNAMAZ (bkz.
                            // PostCard.kt'deki AYNI gerekçe) — şeffaf bir overlay Box
                            // (matchParentSize + combinedClickable, PostCard'ın AYNI
                            // deseni) tüm dokunuşları yakalayıp tam ekran açıyor,
                            // uzun-basma dış balonun `onLongPress`'iyle AYNI.
                            Box(
                                modifier = Modifier
                                    .padding(top = if (replyTo != null || !imageUrl.isNullOrBlank()) 6.dp else 0.dp)
                                    .size(width = 200.dp, height = 260.dp)
                                    .clip(MaterialTheme.shapes.medium),
                            ) {
                                MessageVideoPlayer(videoUrl = videoUrl, modifier = Modifier.matchParentSize())
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .combinedClickable(
                                            onClick = { showFullscreenVideo = true },
                                            onLongClick = onLongPress,
                                        ),
                                )
                            }
                        }
                        // Sesli mesaj (2026-08-09) — image_url/video_url ile AYNI
                        // desen/konum, AYRI bir kolon (audio_url).
                        val audioUrl = message.audioUrl
                        if (!audioUrl.isNullOrBlank()) {
                            MessageAudioPlayer(
                                audioUrl = audioUrl,
                                modifier = Modifier.padding(
                                    top = if (replyTo != null || !imageUrl.isNullOrBlank() || !videoUrl.isNullOrBlank()) {
                                        6.dp
                                    } else {
                                        0.dp
                                    },
                                ),
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
                                    .padding(
                                        top = if (replyTo != null || !imageUrl.isNullOrBlank() || !videoUrl.isNullOrBlank()) {
                                            6.dp
                                        } else {
                                            0.dp
                                        },
                                    )
                                    .size(96.dp),
                            )
                        }
                        if (!message.content.isNullOrBlank()) {
                            val linkContext = LocalContext.current
                            // Link rengi contentColor'dan BİLİNÇLİ farklı (primary) —
                            // aksi halde link, düz metinle aynı renkte olup ayırt
                            // edilemez hale gelirdi (görsel ipucu kaybolur).
                            val linkColor = MaterialTheme.colorScheme.primary
                            val annotatedContent = remember(message.content, linkColor) {
                                buildUrlOnlyAnnotatedString(message.content, linkColor)
                            }
                            ClickableText(
                                text = annotatedContent,
                                style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                                modifier = Modifier.padding(
                                    top = if (replyTo != null || !imageUrl.isNullOrBlank() ||
                                        !videoUrl.isNullOrBlank() || !stickerUrl.isNullOrBlank()
                                    ) {
                                        6.dp
                                    } else {
                                        0.dp
                                    },
                                ),
                                onClick = { offset ->
                                    annotatedContent.getStringAnnotations(tag = "url", start = offset, end = offset)
                                        .firstOrNull()?.let {
                                            try {
                                                linkContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                                            } catch (e: ActivityNotFoundException) {
                                                // Tarayıcı yok/açılamadı — kritik değil, sessizce geç.
                                            }
                                        }
                                },
                            )
                            extractFirstUrl(message.content)?.let { url ->
                                LinkPreviewCard(url = url, modifier = Modifier.padding(top = 4.dp))
                            }
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

    if (showFullscreenImage && !message.imageUrl.isNullOrBlank()) {
        FullscreenImageViewer(imageUrl = message.imageUrl, onDismiss = { showFullscreenImage = false })
    }
    if (showFullscreenVideo && !message.videoUrl.isNullOrBlank()) {
        FullscreenVideoViewer(videoUrl = message.videoUrl, onDismiss = { showFullscreenVideo = false })
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
    // 2026-08-09 (kullanıcı raporu: "video postları mesajlarda gözükmüyor")
    // — kök neden bu composable'ın videoUrl'i HİÇ bilmemesiydi (backend de
    // ayrıca video_url'i share_post()'ta hiç taşımıyordu, o AYRI bir backend
    // düzeltmesiyle giderildi). imageUrl ile AYNI "varsa göster" deseni,
    // ikisi TEORİK olarak birlikte gelebilir ama pratikte bir post ya
    // görsel ya video taşır (bkz. Post.kt).
    videoUrl: String?,
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
        } else if (!videoUrl.isNullOrBlank()) {
            // Tam ekran/oynat-duraklat tıklaması BİLEREK YOK — MessageBubble'ın
            // KENDİ combinedClickable'ı (dış balon) zaten bu kartın TAMAMINI
            // post detayına götürüyor (bkz. composable'ın üstteki dosya
            // yorumu), ayrı bir video oynatma etkileşimi burada İSTENMEDİ.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                MessageVideoPlayer(videoUrl = videoUrl, modifier = Modifier.matchParentSize())
            }
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
    onSendSticker: (StickerDto) -> Unit,
    replyingTo: MessageDto?,
    onCancelReply: () -> Unit,
    selectedImageUri: Uri?,
    onImageSelected: (Uri?) -> Unit,
    selectedVideoUri: Uri?,
    onVideoSelected: (Uri?) -> Unit,
    // 2026-08-09: sesli mesaj — WhatsApp'ın "metin boşken mikrofon, doluyken
    // gönder" ikili buton deseni. [isRecordingAudio]/[recordingElapsedMs]
    // ConversationViewModel'in AKTİF kayıt state'i (bkz. o dosyanın yorumu).
    isRecordingAudio: Boolean,
    recordingElapsedMs: Long,
    onStartRecording: () -> Unit,
    onStopRecordingAndSend: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    // CreatePostScreen'deki AYNI Photo Picker deseni — seçilen Uri ViewModel'e
    // (ConversationViewModel.selectedImageUri) bildirilir, bu composable
    // KENDİ local state'ini TUTMAZ.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> onImageSelected(uri) }

    // 2026-08-08: video gönderme — AYNI Photo Picker, VideoOnly filtresiyle.
    // Görsel ile video AYNI anda seçilemez (ViewModel.onImageSelected/
    // onVideoSelected birbirini temizler) — tek seferde tek ek dosya, UI'da
    // KARIŞIKLIK yaratmasın diye.
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> onVideoSelected(uri) }

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
            onStickerSelected = { sticker ->
                showMediaPicker = false
                onSendSticker(sticker)
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
            if (selectedVideoUri != null) {
                Box(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Videocam,
                            contentDescription = "Video seçildi",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onVideoSelected(null) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Videoyu kaldır",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            if (isRecordingAudio) {
                // WhatsApp'ın "kayıt sürüyor" görünümü — metin/medya butonları
                // GİZLENİR, sadece kayıt göstergesi + iptal/gönder kalır.
                // Kaydırarak-iptal (slide-to-cancel) BİLEREK yok — açık "X"
                // butonu daha basit/güvenilir bir MVP (kayan parmak gesture'ı
                // bu turun kapsamı DIŞI).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(Icons.Filled.Close, contentDescription = "Kaydı iptal et")
                    }
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    val elapsedSec = recordingElapsedMs / 1000
                    Text(
                        text = "${elapsedSec / 60}:${(elapsedSec % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                    IconButton(
                        onClick = onStopRecordingAndSend,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                    }
                }
            } else {
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
                IconButton(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                        )
                    },
                ) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = "Video ekle",
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
                val canSend = sendText.isNotBlank() || selectedImageUri != null || selectedVideoUri != null
                if (canSend) {
                    IconButton(
                        onClick = onSend,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder")
                    }
                } else {
                    // 2026-08-09 (kullanıcı isteği: "sesli mesaj gibi
                    // özellikleri getirelim") — metin/medya YOKKEN Gönder
                    // butonu yerine WhatsApp'taki gibi basılı-tut mikrofon.
                    // IconButton'ın KENDİ clickable'ı (tıklama) YERİNE ham
                    // pointerInput+detectTapGestures kullanıldı — basılı
                    // TUTMA süresini (onPress/tryAwaitRelease) yakalamak
                    // için, sıradan onClick sadece tek anlık tıklamayı verir.
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        onStartRecording()
                                        val released = tryAwaitRelease()
                                        if (released) onStopRecordingAndSend() else onCancelRecording()
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Basılı tutup sesli mesaj kaydet",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Mesaj balonu içindeki video oynatıcı (2026-08-08) — ReelsScreen.kt'deki
 * `ReelPage`'in ExoPlayer kurulumuyla AYNI temel desen (composable disposed
 * olunca `release()`, bellek sızıntısı/arka planda çalan video olmasın diye),
 * ama tam ekran/otomatik-oynatma/loop YOK — burası bir sohbet balonu, tıklayıp
 * kendi kontrol çubuğuyla (`useController = true`) oynatır/duraklatır.
 * [videoUrl] hem gerçek bir CDN URL'si (sunucudan dönen mesaj) HEM de yerel
 * bir `content://` URI'si (optimistic gönderim sırasında, henüz yüklenmemiş
 * video) olabilir — ExoPlayer `MediaItem.fromUri()` ikisini de DOĞRUDAN çözer.
 */
@Composable
private fun MessageVideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                player = exoPlayer
                // Kullanıcı raporu: ZOOM (cover) dikey videoyu yatay/uyumsuz
                // orandaki kutuya sığdırırken aşırı kırpıyordu — FIT (contain)
                // videonun TAMAMINI gösterir, kırpma yapmaz.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = exoPlayer },
        modifier = modifier,
    )
}

/**
 * Sesli mesaj oynatıcı (2026-08-09) — [MessageVideoPlayer]'ın AYNI ExoPlayer
 * deseni (composable disposed olunca `release()`), ama görsel bir video
 * yüzeyi YERİNE basit bir çal/duraklat düğmesi + geçen/toplam süre metni
 * (web'in `voiceWaveform.js`'indeki dalga formu görselleştirmesi bu turun
 * kapsamı DIŞI — MVP karar, ses OYNATILABİLİYOR olması asıl gereksinim).
 * [audioUrl] hem gerçek bir CDN URL'si HEM de yerel `file://` URI'si
 * (optimistic gönderim sırasında, henüz yüklenmemiş kayıt) olabilir.
 */
@Composable
private fun MessageAudioPlayer(audioUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(audioUrl))
            prepare()
        }
    }
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var positionMs by remember(audioUrl) { mutableStateOf(0L) }
    var durationMs by remember(audioUrl) { mutableStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) durationMs = exoPlayer.duration.coerceAtLeast(0L)
                // Bitince başa sar — WhatsApp'ın AYNI davranışı, tekrar
                // basınca sondan değil baştan çalsın.
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0)
                    exoPlayer.pause()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Çalarken 300ms'de bir gerçek konumu okuyup ilerleme metnini günceller —
    // ExoPlayer'ın kendisi konum için bir Flow/StateFlow SUNMUYOR, elle
    // yoklama (polling) gerekiyor.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(300)
        }
    }

    fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
    }

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Duraklat" else "Çal",
            )
        }
        Text(
            text = "${formatMs(positionMs)} / ${formatMs(durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
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
