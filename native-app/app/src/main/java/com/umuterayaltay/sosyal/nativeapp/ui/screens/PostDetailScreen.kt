package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.PostDetailEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.PostDetailViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.PostDetailViewModelFactory

/**
 * Bir post'a tıklanınca açılan ekran — üstte postun kendisi (PostCard REUSE
 * edilir), altında hiyerarşik yorum listesi (LazyColumn) + alt input bar
 * (ConversationScreen'deki ConversationInputBar ile TUTARLI bir desen).
 * Backend sözleşmesi: app/api_v1.py api_post_detail()/api_add_comment()
 * (bkz. PostDetailViewModel docstring'i).
 *
 * BİLİNÇLİ SINIR (backend gerçeğiyle tutarlılık için): backend SADECE tek
 * seviye yanıt destekliyor (api_post_detail()'de `tc["replies"]` filtresi
 * SADECE parent_comment_id == ÜST-seviye yorum id'sine bakar) — bu yüzden
 * "Yanıtla" butonu SADECE üst-seviye yorumlarda gösterilir, bir yanıta
 * yanıt verme UI'da YOK (yoksa gönderilen yorum backend'de var olur ama
 * bu ekranda hiçbir yerde görünmez, "kaybolmuş" gibi görünürdü).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onNavigateBack: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModelFactory(postId)),
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentText by viewModel.commentText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PostDetailEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gönderi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
        bottomBar = {
            if (post != null) {
                CommentInputBar(
                    text = commentText,
                    onTextChange = viewModel::onCommentTextChange,
                    onSend = viewModel::addComment,
                    replyingTo = replyingTo,
                    onCancelReply = viewModel::clearReplyingTo,
                )
            }
        },
    ) { padding ->
        when {
            loading && post == null -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Gönderi yükleniyor…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            error != null && post == null -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            post == null -> CenteredMessage(padding) { Text("Gönderi bulunamadı.") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    PostCard(
                        post = post!!,
                        onLikeClick = { viewModel.toggleLike() },
                        onCommentClick = {},
                        onHashtagClick = onNavigateToHashtag,
                        onPollVote = { postId, optionId -> viewModel.votePoll(postId, optionId) },
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Text(
                        text = if (comments.isEmpty()) "Yorumlar" else "Yorumlar (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (comments.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ChatBubbleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "Henüz yorum yok",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Text(
                                text = "İlk yorumu sen yaz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        Column {
                            CommentRow(
                                comment = comment,
                                indent = 0.dp,
                                showReply = true,
                                onReplyClick = { viewModel.setReplyingTo(comment) },
                            )
                            comment.replies?.forEach { reply ->
                                CommentRow(
                                    comment = reply,
                                    indent = 32.dp,
                                    showReply = false,
                                    onReplyClick = {},
                                    isReply = true,
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentDto,
    indent: Dp,
    showReply: Boolean,
    onReplyClick: () -> Unit,
    isReply: Boolean = false,
) {
    val avatarSize = if (isReply) 26.dp else 32.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // IntrinsicSize.Min: Row'u kendi içeriğinin (avatar/metin sütunu)
            // intrinsic yüksekliğine SINIRLAR — aksi halde LazyColumn item'ı
            // Row'a sınırsız (Infinity) yükseklik constraint'i verir ve
            // aşağıdaki hiyerarşi çizgisinin fillMaxHeight() çağrısı
            // "Infinity height" çökmesine yol açardı (standart Compose
            // "kardeşle eşit yükseklik" deseni).
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp + indent, end = 16.dp, top = 6.dp, bottom = 6.dp),
    ) {
        // Yanıtların üst-seviye yorumla ilişkisini görsel olarak açıkça
        // gösteren dikey bir "hiyerarşi çizgisi" — sadece [isReply] true
        // iken çizilir, indent boşluğunun sol kenarına yerleşir.
        if (isReply) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            )
        }
        if (!comment.profiles?.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = comment.profiles?.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(avatarSize * 0.65f),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = comment.profiles?.username ?: "Bilinmeyen kullanıcı",
                style = MaterialTheme.typography.labelLarge,
            )
            if (!comment.content.isNullOrBlank()) {
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // Yorum beğenme bu turun kapsamı dışı (comment_likes'a aksiyon
                // yok) - sayı SADECE gösterilir, tıklanabilir DEĞİL.
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Beğeni sayısı",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = " ${comment.likeCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "  ${formatClockTime(comment.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (showReply) {
                    TextButton(onClick = onReplyClick, modifier = Modifier.padding(start = 4.dp)) {
                        Text("Yanıtla", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: CommentDto?,
    onCancelReply: () -> Unit,
) {
    // Surface + tonalElevation: input bar'ı üstteki yorum listesinden görsel
    // olarak AYIRIYOR (ConversationScreen'deki mesaj input bar'ıyla tutarlı
    // bir "sabit alt çubuk" hissi) — işlev (onSend/onTextChange) DEĞİŞMEDİ.
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            HorizontalDivider()
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val replyUsername = replyingTo.profiles?.username ?: "kullanıcı"
                    Text(
                        text = "$replyUsername kullanıcısına yanıt veriyorsun",
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
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Bir yorum yaz...") },
                    shape = MaterialTheme.shapes.large,
                    maxLines = 4,
                )
                IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (text.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
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
