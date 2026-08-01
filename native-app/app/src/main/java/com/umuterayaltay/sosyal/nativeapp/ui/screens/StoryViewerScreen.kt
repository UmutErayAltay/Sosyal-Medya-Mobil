package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryViewerViewModelFactory
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
    userId: String,
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: StoryViewerViewModel = viewModel(factory = StoryViewerViewModelFactory(userId)),
) {
    val username by viewModel.username.collectAsState()
    val avatarUrl by viewModel.avatarUrl.collectAsState()
    val isMine by viewModel.isMine.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var paused by remember { mutableStateOf(false) }
    var showReplyField by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var showSaveHighlightDialog by remember { mutableStateOf(false) }
    var highlightTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoryViewerEvent.SessionExpired -> onSessionExpired()
                is StoryViewerEvent.StoryDeleted -> onNavigateBack()
                is StoryViewerEvent.MessageSent -> Unit // conversation_id bu turda otomatik navigasyon TETİKLEMİYOR
                is StoryViewerEvent.HighlightSaved -> {
                    showSaveHighlightDialog = false
                    highlightTitle = ""
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
                    TextButton(onClick = onNavigateBack) {
                        Text("Geri Dön", color = Color.White)
                    }
                }
            }
            else -> {
                val story = stories.getOrNull(currentIndex) ?: return@Box

                StorySegment(
                    story = story,
                    paused = paused,
                    onAdvance = {
                        if (!viewModel.goNext()) onNavigateBack()
                    },
                )

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
                        onTap = { if (!viewModel.goNext()) onNavigateBack() },
                        onPausedChange = { paused = it },
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
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
                        onClose = onNavigateBack,
                        onDelete = { viewModel.deleteCurrentStory() },
                    )

                    if (story.poll != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 12.dp),
                        ) {
                            PollWidget(
                                poll = story.poll,
                                onVote = { optionId -> viewModel.votePoll(optionId) },
                            )
                        }
                    }

                    if (!story.caption.isNullOrBlank()) {
                        Text(
                            text = story.caption,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
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
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    IconButton(
                        onClick = { showSaveHighlightDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "Öne çıkanlara kaydet", tint = Color.White)
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
            }
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
                else -> AnimatedSegmentProgress(paused = paused, key = storyId)
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
                    modifier = Modifier.weight(1f),
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

private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
}
