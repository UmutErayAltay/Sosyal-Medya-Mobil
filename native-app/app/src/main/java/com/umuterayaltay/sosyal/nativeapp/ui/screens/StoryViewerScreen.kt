package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Story
import com.umuterayaltay.sosyal.nativeapp.repository.StoryOverlayElement
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerViewModelFactory
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewersUiState
import kotlinx.coroutines.delay

private const val IMAGE_DURATION_MS = 5000
private val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "🔥", "👏")

/**
 * Hikaye görüntüleyici — web'in `static/js/stories.js`'indeki davranışın
 * Compose karşılığı (view tracking dahil app/api_v1/stories.py
 * api_user_stories()'in AYNI mantığı, `StoryViewerViewModel.init` içinde bir
 * kez çağrılır). Görsel segment [IMAGE_DURATION_MS] sonra, video segment
 * `ended` olunca (ExoPlayer STATE_ENDED) otomatik ilerler. Ekranın sağ/sol
 * yarısına dokununca ileri/geri, basılı tutunca duraklatır (web'in
 * mousedown/touchstart ile "pause" davranışının AYNISI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryViewerScreen(
    // 2026-08-10 (kullanıcı isteği: "birinin storyleri bitince sıradakine
    // geçsin") — tek bir userId YERİNE hikaye çubuğundaki TÜM kullanıcıların
    // sıralı listesi + tıklanan kullanıcının o listedeki index'i (bkz.
    // StoryViewerViewModel sınıf yorumu).
    userIds: List<String>,
    startIndex: Int,
    // 2026-08-09 (kullanıcı raporu: "öne çıkarılanlara ekleyince uygulamayı
    // aç kapa yapmak zorunda kalıyorum") — bu ekran highlight kaydedince
    // KAPANMAZ (kullanıcı hikayeleri izlemeye devam edebilir), bu yüzden
    // "değişiklik oldu mu" bilgisi burada TUTULUP ekran GERÇEKTEN kapanırken
    // (onNavigateBack çağrılırken) taşınıyor — AppNavHost bunu
    // previousBackStackEntry'ye (ProfileScreen'in okuduğu "highlight_changed"
    // bayrağı) yazıyor, storyCreated/postCreated ile AYNI desen.
    onNavigateBack: (highlightChanged: Boolean) -> Unit,
    onSessionExpired: () -> Unit,
    // 2026-08-11 (kullanıcı isteği: "@bahsetme ve #hashtag sticker'ı") —
    // sticker'lara dokununca profile/hashtag sayfasına gidebilsin diye,
    // PostCard.kt'deki onUsernameClick/onHashtagClick ile AYNI VARSAYILAN
    // DEĞERLİ opsiyonel callback deseni.
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToHashtag: (String) -> Unit = {},
    viewModel: StoryViewerViewModel = viewModel(
        factory = StoryViewerViewModelFactory(userIds, startIndex),
    ),
) {
    val username by viewModel.username.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val isMine by viewModel.isMine.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val viewersState by viewModel.viewers.collectAsState()

    var paused by remember { mutableStateOf(false) }
    var showReplyField by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var showSaveHighlightDialog by remember { mutableStateOf(false) }
    var highlightTitle by remember { mutableStateOf("") }
    var highlightChanged by remember { mutableStateOf(false) }
    // 2026-08-11 (kullanıcı isteği: "hikayeyi kim izledi listesi").
    var showViewersSheet by remember { mutableStateOf(false) }

    // Sistem geri tuşu, ekrandaki "X"/TapZone çağrılarının AKSİNE varsayılan
    // olarak NavController'ın kendi navigateUp()'ına gider — bizim
    // onNavigateBack(highlightChanged) callback'imizden GEÇMEZ, bu yüzden
    // highlightChanged bayrağı KAYBOLURDU. BackHandler AÇIKÇA bunu ele alıp
    // AYNI callback'e yönlendiriyor.
    BackHandler(onBack = { onNavigateBack(highlightChanged) })

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoryViewerEvent.SessionExpired -> onSessionExpired()
                is StoryViewerEvent.StoryDeleted -> onNavigateBack(highlightChanged)
                is StoryViewerEvent.MessageSent -> Unit // conversation_id bu turda otomatik navigasyon TETİKLEMİYOR
                is StoryViewerEvent.HighlightSaved -> {
                    showSaveHighlightDialog = false
                    highlightTitle = ""
                    highlightChanged = true
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            loading && stories.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            stories.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error ?: "Aktif hikaye yok",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = { onNavigateBack(highlightChanged) }) {
                        Text("Geri Dön", color = Color.White)
                    }
                }
            }
            else -> {
                val story = stories.getOrNull(currentIndex) ?: return@Box

                // Hikaye gecisinde crossfade — YENI segment, StorySegment
                // (video/gorsel/metin) DEGISMEDEN, KISA bir alpha animasyonuyla
                // belirir. Bilincli olarak eski segmenti AYRI bir composition'da
                // TUTMUYORUZ (ör. Crossfade/AnimatedContent ile): otomatik
                // ilerleme zamanlayicisi (LaunchedEffect(imageUrl, paused))
                // kisa bir donem arka planda calismaya devam edip ikinci bir
                // onAdvance/onTimeUp tetiklemesi riski dogurabilirdi. Bunun
                // yerine SADECE yeni segment fade-in yapar - basili-tutunca
                // duraklatma mantigina DOKUNULMADI.
                val segmentTransitionAlpha = remember(story.id) { Animatable(0f) }
                LaunchedEffect(story.id) {
                    segmentTransitionAlpha.animateTo(1f, animationSpec = tween(220))
                }

                Box(modifier = Modifier.fillMaxSize().alpha(segmentTransitionAlpha.value)) {
                    StorySegment(
                        story = story,
                        paused = paused,
                        onAdvance = {
                            if (!viewModel.goNext()) onNavigateBack(highlightChanged)
                        },
                    )
                }

                // Sol/sağ yarıya dokunma (ileri/geri) + basılı tutunca duraklatma —
                // web'in mousedown/touchstart pause deseninin native karşılığı.
                Row(modifier = Modifier.fillMaxSize()) {
                    TapZone(
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.goPrevious() },
                        onPausedChange = { paused = it },
                    )
                    TapZone(
                        modifier = Modifier.weight(1f),
                        onTap = { if (!viewModel.goNext()) onNavigateBack(highlightChanged) },
                        onPausedChange = { paused = it },
                    )
                }

                // Kullanıcı raporu (2026-08-10): "telefonun üst kısmındaki
                // bildirim kısmı şeffaf olduğu için fotoğrafın üst kısmı
                // taşıyormuş gibi hissettiriyor" — kök neden edge-to-edge
                // (MainActivity.enableEdgeToEdge()) altında status bar'ın
                // GERÇEKTEN şeffaf olması VE bu ekranın ilerleme çubuğu/
                // header'ının statusBarsPadding() UYGULAMAMASI: hem sistem
                // saat/pil ikonları fotoğrafın DOĞRUDAN üstüne biniyordu
                // hem de fotoğraf hiçbir yumuşatma OLMADAN ekranın en üst
                // pikseline kadar uzanıyordu (Instagram'ın AYNI ekranda
                // kullandığı ince siyah gradyan YOKTU). İki parçalı düzeltme:
                // (1) aşağıdaki gradyan scrim status bar bölgesinde fotoğrafı
                // yumuşatıyor, (2) Column'a eklenen statusBarsPadding()
                // ilerleme çubuğunu/header'ı sistem ikonlarının ALTINA değil
                // ARDINA (yani onlarla ÇAKIŞMAYACAK şekilde) itiyor.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    SegmentProgressRow(
                        count = stories.size,
                        currentIndex = currentIndex,
                        paused = paused,
                        storyId = story.id,
                    )

                    StoryHeader(
                        username = username,
                        avatarUrl = avatarUrl,
                        isMine = isMine,
                        onClose = { onNavigateBack(highlightChanged) },
                        onDelete = { viewModel.deleteCurrentStory() },
                    )
                }

                // 2026-08-09 (kullanıcı isteği: "instagram'a benzer hikaye
                // editörü, anketi/yazıyı çekerek istediğimiz yere koyabilelim")
                // — poll/caption artık SABİT bir Column akışında DEĞİL, web'in
                // stories.js'deki sürüklenebilir widget'larıyla AYNI normalize
                // (0..1) position_x/position_y (+ poll için scale) üzerinden
                // TAM EKRANA göre konumlanıyor (StoryCreateScreen'deki editör
                // BUNU zaten üretiyor). BoxWithConstraints ile ekranın piksel
                // boyutu okunuyor, her öğe KENDİ ölçülmüş boyutunun yarısı
                // kadar geri kaydırılıyor (CSS'in translate(-50%,-50%) ile
                // AYNI merkezleme fikri — graphicsLayer lambda'sı DRAW anında
                // çalıştığı için `size` orada döngüsel ölçüm sorunu YARATMADAN
                // kullanılabiliyor).
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val canvasWidthPx = with(density) { maxWidth.toPx() }
                    val canvasHeightPx = with(density) { maxHeight.toPx() }

                    if (story.poll != null) {
                        val posX = (story.poll.positionX ?: 0.5).toFloat()
                        val posY = (story.poll.positionY ?: 0.75).toFloat()
                        val scale = (story.poll.scale ?: 1.0).toFloat()
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .graphicsLayer {
                                    translationX = posX * canvasWidthPx - size.width / 2f
                                    translationY = posY * canvasHeightPx - size.height / 2f
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .padding(horizontal = 24.dp),
                        ) {
                            PollWidget(
                                poll = story.poll,
                                onVote = { optionId -> viewModel.votePoll(optionId) },
                            )
                        }
                    }

                    if (!story.caption.isNullOrBlank()) {
                        val posX = story.captionPositionX.toFloat()
                        val posY = story.captionPositionY.toFloat()
                        StoryCaptionText(
                            text = story.caption,
                            captionStyle = story.captionStyle,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .graphicsLayer {
                                    translationX = posX * canvasWidthPx - size.width / 2f
                                    translationY = posY * canvasHeightPx - size.height / 2f
                                },
                        )
                    }

                    // 2026-08-10 (kullanıcı raporu: "2.ye tıklayınca öncekini
                    // siliyor") — TEKİL overlay yerine LİSTE (en fazla 3, bkz.
                    // ApiModels.kt StoryOverlayElementDto yorumu), caption/
                    // poll ile AYNI normalize konum + ölçek deseni.
                    story.overlayElements.forEach { element ->
                        val posX = element.positionX.toFloat()
                        val posY = element.positionY.toFloat()
                        val scale = element.scale.toFloat()
                        val positionModifier = Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                translationX = posX * canvasWidthPx - size.width / 2f
                                translationY = posY * canvasHeightPx - size.height / 2f
                                scaleX = scale
                                scaleY = scale
                            }
                        // 2026-08-11 (kullanıcı isteği: "@bahsetme ve #hashtag
                        // sticker'ı") — mention/hashtag TIKLANABİLİR (profile/
                        // hashtag sayfasına gider, PostCard'daki AYNI davranış),
                        // GIF/sticker (Image) tıklamaya tepki VERMEZ (zaten
                        // sadece dekoratif bir görsel).
                        when (element) {
                            is StoryOverlayElement.Image -> AsyncImage(
                                model = element.url,
                                contentDescription = null,
                                modifier = positionModifier.size(120.dp),
                            )
                            is StoryOverlayElement.Mention -> StoryStickerPill(
                                text = "@${element.username}",
                                modifier = positionModifier.clickable { onNavigateToProfile(element.username) },
                            )
                            is StoryOverlayElement.Hashtag -> StoryStickerPill(
                                text = "#${element.tag}",
                                modifier = positionModifier.clickable { onNavigateToHashtag(element.tag) },
                            )
                        }
                    }
                }

                if (!isMine) {
                    StoryFooter(
                        showReplyField = showReplyField,
                        replyText = replyText,
                        onReplyTextChange = { replyText = it },
                        onToggleReplyField = { showReplyField = !showReplyField },
                        onSendReply = {
                            if (replyText.isNotBlank()) {
                                viewModel.replyToStory(replyText)
                                replyText = ""
                                showReplyField = false
                            }
                        },
                        onReact = { emoji -> viewModel.reactToStory(emoji) },
                        // Madde 8 (kullanıcı raporu: yanıt yazarken süre durmuyor) —
                        // basılı-tutma gesture'ıyla AYNI `paused` değişkeni, reply
                        // alanı odaklanınca/odağı kaybedince de güncellenir.
                        onReplyFocusChanged = { focused -> paused = focused },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 2026-08-11 (kullanıcı isteği: "hikayeyi kim izledi
                        // listesi") — story_views tablosu ZATEN her
                        // görüntülemede yazılıyordu (halka rengi için), sadece
                        // bunu OKUYAN bir ekran yoktu. Lazy: sheet ilk açılışta
                        // yükleniyor (bkz. ViewModel.loadViewers() yorumu).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .clickable {
                                    paused = true
                                    showViewersSheet = true
                                    viewModel.loadViewers()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = "İzleyenler",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "İzleyenler",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        IconButton(onClick = { showSaveHighlightDialog = true }) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Öne çıkanlara kaydet", tint = Color.White)
                        }
                    }
                }

                if (showSaveHighlightDialog) {
                    SaveHighlightBar(
                        title = highlightTitle,
                        onTitleChange = { highlightTitle = it },
                        onCancel = { showSaveHighlightDialog = false },
                        onSave = {
                            if (highlightTitle.isNotBlank()) viewModel.saveToHighlight(newTitle = highlightTitle)
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (showViewersSheet) {
                    StoryViewersSheet(
                        state = viewersState,
                        onDismiss = {
                            showViewersSheet = false
                            paused = false
                            viewModel.resetViewers()
                        },
                    )
                }
            }
        }
    }
}

/**
 * "İzleyenler" bottom sheet — 2026-08-11 (kullanıcı isteği: "hikayeyi kim
 * izledi listesi"). ModalBottomSheet MediaPickerSheet/StoryPollEditorSheet
 * ile AYNI desen; boş liste durumu ("henüz kimse görmedi") AYRI bir dal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryViewersSheet(state: StoryViewersUiState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = when (state) {
                    is StoryViewersUiState.Success -> "İzleyenler (${state.count})"
                    else -> "İzleyenler"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            when (state) {
                is StoryViewersUiState.Loading, StoryViewersUiState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                is StoryViewersUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                is StoryViewersUiState.Success -> {
                    if (state.viewers.isEmpty()) {
                        Text(
                            text = "Henüz kimse görmedi",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            state.viewers.forEach { viewer ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                ) {
                                    if (!viewer.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = viewer.avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(40.dp).clip(CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Filled.Person, contentDescription = null)
                                        }
                                    }
                                    Text(
                                        text = viewer.username,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StorySegment(story: Story, paused: Boolean, onAdvance: () -> Unit) {
    when {
        !story.videoUrl.isNullOrBlank() -> VideoSegment(videoUrl = story.videoUrl, paused = paused, onEnded = onAdvance)
        !story.imageUrl.isNullOrBlank() -> {
            ImageSegment(imageUrl = story.imageUrl, paused = paused, onTimeUp = onAdvance)
        }
        else -> {
            // Salt-metin hikaye (background_color) — medya yok.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(parseHexColor(story.backgroundColor) ?: Color.DarkGray),
            )
            ImageSegment(imageUrl = null, paused = paused, onTimeUp = onAdvance)
        }
    }
}

@Composable
private fun ImageSegment(imageUrl: String?, paused: Boolean, onTimeUp: () -> Unit) {
    if (!imageUrl.isNullOrBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }

    LaunchedEffect(imageUrl, paused) {
        var elapsed = 0
        while (elapsed < IMAGE_DURATION_MS) {
            if (!paused) {
                delay(100)
                elapsed += 100
            } else {
                delay(100)
            }
        }
        onTimeUp()
    }
}

@Composable
private fun VideoSegment(videoUrl: String, paused: Boolean, onEnded: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(videoUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(paused) {
        exoPlayer.playWhenReady = !paused
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exoPlayer
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = exoPlayer },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Sol/sağ yarıya dokunma = ileri/geri, basılı tutma = duraklat — web'in
 * mousedown/touchstart(pause)+mouseup/touchend(resume) davranışının Compose
 * karşılığı (detectTapGestures'ın onPress/awaitRelease'i). */
@Composable
private fun TapZone(modifier: Modifier, onTap: () -> Unit, onPausedChange: (Boolean) -> Unit) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPausedChange(true)
                        try {
                            awaitRelease()
                        } finally {
                            onPausedChange(false)
                        }
                    },
                    onTap = { onTap() },
                )
            },
    )
}

@Composable
private fun SegmentProgressRow(count: Int, currentIndex: Int, paused: Boolean, storyId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { index ->
            val progress = when {
                index < currentIndex -> 1f
                index > currentIndex -> 0f
                else -> {
                    // Gorsel cila (animasyon turu): ham ilerleme her 100ms'de
                    // bir "ziplayarak" guncelleniyordu (bkz. AnimatedSegmentProgress
                    // dongusu) — animateFloatAsState ile bu adimlar arasi da
                    // YUMUSAKCA (60fps) interpole edilerek cubuk akici doluyor.
                    // Duraklatildiginda ANINDA donuyor (snap), yeni bir tween
                    // baslatip "geriden yetismeye" calismasin diye.
                    val rawProgress = AnimatedSegmentProgress(paused = paused, key = storyId)
                    val smoothProgress by animateFloatAsState(
                        targetValue = rawProgress,
                        animationSpec = if (paused) snap() else tween(durationMillis = 100, easing = LinearEasing),
                        label = "story-progress-smooth",
                    )
                    smoothProgress
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

/** Aktif segmentin ilerleme çubuğu — IMAGE_DURATION_MS'e göre dolduruluyor
 * (video segmentlerde de yaklaşık bir gösterge olarak kullanılır, tam
 * senkronize video pozisyonu İCAT EDİLMEDİ — sadece görsel bir ipucu). */
@Composable
private fun AnimatedSegmentProgress(paused: Boolean, key: String): Float {
    var progress by remember(key) { mutableStateOf(0f) }
    LaunchedEffect(key, paused) {
        var elapsed = progress * IMAGE_DURATION_MS
        while (elapsed < IMAGE_DURATION_MS) {
            if (!paused) {
                delay(100)
                elapsed += 100
                progress = (elapsed / IMAGE_DURATION_MS).coerceIn(0f, 1f)
            } else {
                delay(100)
            }
        }
    }
    return progress
}

@Composable
private fun StoryHeader(
    username: String,
    avatarUrl: String?,
    isMine: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Text(
            text = username,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        if (isMine) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hikayeyi Sil", tint = Color.White)
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = Color.White)
        }
    }
}

@Composable
private fun StoryFooter(
    showReplyField: Boolean,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    onToggleReplyField: () -> Unit,
    onSendReply: () -> Unit,
    onReact: (String) -> Unit,
    // Madde 8 (kullanıcı raporu: hikayeye yanıt yazarken süre durmuyor) —
    // reply alanı odaklanınca/odağı kaybedince StoryViewerScreen'deki `paused`
    // state'ini günceller (basılı-tutma gesture'ıyla AYNI değişken, iki
    // kaynaktan biri true olduğunda duraklat mantığı zaten doğal çalışır).
    onReplyFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (showReplyField) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = onReplyTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState -> onReplyFocusChanged(focusState.isFocused) },
                    placeholder = { Text("Yanıt gönder...", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSendReply() }),
                )
                IconButton(onClick = onSendReply, enabled = replyText.isNotBlank()) {
                    Icon(Icons.Filled.Send, contentDescription = "Gönder", tint = Color.White)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onToggleReplyField() }) },
                ) {
                    Text(text = "Yanıt gönder...", color = Color.White.copy(alpha = 0.8f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    REACTION_EMOJIS.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .padding(2.dp)
                                .pointerInput(emoji) { detectTapGestures(onTap = { onReact(emoji) }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveHighlightBar(
    title: String,
    onTitleChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Öne çıkanlar başlığı", color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
        )
        TextButton(onClick = onCancel) { Text("Vazgeç", color = Color.White) }
        TextButton(onClick = onSave, enabled = title.isNotBlank()) { Text("Kaydet", color = Color.White) }
    }
}

// StoryCreateScreen.kt'nin de (AYNI paket) kullanabilmesi için internal —
// Kotlin'de top-level `private` fonksiyonlar dosya sınırını aşamaz
// (bkz. FeedScreen.kt'deki PostFeedStaggerReveal yorumu, AYNI gerekçe),
// bu yüzden KOPYALANMADI.
internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}

/**
 * Hikaye caption'ının stilize render'ı — StoryCreateScreen.kt'nin editör
 * önizlemesi İLE StoryViewerScreen'in GERÇEK görüntüleyicisi TARAFINDAN
 * paylaşılan TEK yer (2026-08-11, kullanıcı isteği: "metin stili/rengi
 * seçenekleri" — WYSIWYG: ikisi FARKLI stil/padding kullanırsa editörde
 * gördüğün son paylaşılanla EŞLEŞMEZ). `captionStyle` null ise klasik
 * (düz beyaz yazı, arka plansız); "pill_light"/"pill_dark" backend'in kabul
 * ettiği TAM OLARAK bu iki değer (bkz. StoriesApi.createStory yorumu),
 * TANIMSIZ bir değer klasike düşer (backend zaten fail-open null'a
 * düşürüyor, ama native ekstra bir savunma katmanı — sunucu tanımadığı bir
 * stil string'i gönderse bile çökme/yanlış render YOK).
 */
@Composable
internal fun StoryCaptionText(text: String, captionStyle: String?, modifier: Modifier = Modifier) {
    when (captionStyle) {
        "pill_light" -> Text(
            text = text,
            color = Color.Black,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
        "pill_dark" -> Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
        else -> Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/**
 * @mention/#hashtag sticker'ının görsel pili — StoryCreateScreen.kt'nin
 * editör önizlemesi İLE StoryViewerScreen'in GERÇEK görüntüleyicisi
 * TARAFINDAN paylaşılan TEK yer (2026-08-11, kullanıcı isteği: "@bahsetme
 * ve #hashtag sticker'ı"). Instagram'daki mention/hashtag sticker'larının
 * AYNI "renkli pil" hissi — GIF/sticker (Image) overlay'inden görsel
 * olarak AYRIŞTIRILABİLSİN diye kasıtlı farklı (bir görsel değil, metin).
 */
@Composable
internal fun StoryStickerPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
