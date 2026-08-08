package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.network.CommentReactionDto
import com.umuterayaltay.sosyal.nativeapp.network.MentionSuggestionDto
import com.umuterayaltay.sosyal.nativeapp.ui.components.MediaPickerSheet
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
    val currentUserId by viewModel.currentUserId.collectAsState()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PostDetailEvent.SessionExpired -> onSessionExpired()
                is PostDetailEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is PostDetailEvent.NavigateBack -> onNavigateBack()
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
                    onSend = { viewModel.addComment() },
                    onSendGif = { url -> viewModel.addComment(gifUrl = url) },
                    onSendSticker = { id -> viewModel.addComment(stickerId = id) },
                    replyingTo = replyingTo,
                    onCancelReply = viewModel::clearReplyingTo,
                    mentionSuggestions = mentionSuggestions,
                    onMentionSelected = viewModel::selectMention,
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
                        onMutePost = { viewModel.toggleMutePost() },
                        onBookmark = { viewModel.toggleBookmark() },
                        onRepost = { viewModel.repost() },
                        onReport = { viewModel.report() },
                        onSessionExpired = onSessionExpired,
                        isOwnPost = post!!.userId == currentUserId,
                        onEditPost = { _, content -> viewModel.editPost(content) },
                        onDeletePost = { viewModel.deletePost() },
                        onArchivePost = { viewModel.toggleArchive() },
                        onPinPost = { viewModel.togglePin() },
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
                        // Animasyon turu (2026-08-08): yeni bir yorum eklenince (ya da
                        // hiyerarşi/sıra değişince) LazyColumn'un yerleşik `animateItem()`
                        // yerleştirme animasyonu — hafif slide+fade ile giriş/konum
                        // değişimi. Elle bir "yeni mi eski mi" bayrağı TUTULMADI, bu
                        // API zaten SADECE gerçekten eklenen/kaldırılan/taşınan item'lar
                        // için devreye girer (mevcut item'lar sabit kalınca hiçbir şey
                        // OYNAMAZ).
                        Column(modifier = Modifier.animateItem()) {
                            CommentRow(
                                comment = comment,
                                indent = 0.dp,
                                showReply = true,
                                onReplyClick = { viewModel.setReplyingTo(comment) },
                                isOwn = comment.userId == currentUserId,
                                onLikeClick = { viewModel.toggleCommentLike(comment.id) },
                                onReactionSelected = { emoji -> viewModel.reactToComment(comment.id, emoji) },
                                onDeleteClick = { viewModel.deleteComment(comment.id) },
                            )
                            comment.replies?.forEach { reply ->
                                CommentRow(
                                    comment = reply,
                                    indent = 32.dp,
                                    showReply = false,
                                    onReplyClick = {},
                                    isReply = true,
                                    isOwn = reply.userId == currentUserId,
                                    onLikeClick = { viewModel.toggleCommentLike(reply.id) },
                                    onReactionSelected = { emoji -> viewModel.reactToComment(reply.id, emoji) },
                                    onDeleteClick = { viewModel.deleteComment(reply.id) },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(
    comment: CommentDto,
    indent: Dp,
    showReply: Boolean,
    onReplyClick: () -> Unit,
    isReply: Boolean = false,
    // Yorum mutasyonları — isOwn SADECE Sil'i göstermek için (Düzenle bu
    // turun kapsamı dışı, PostCard'ın post-düzenleme desenindeki gibi bir
    // ihtiyaç raporu YOK), beğeni/tepki HERKESİN yorumunda kullanılabilir.
    // VARSAYILAN DEĞERLİ ({} / false) — diğer PostCard callback'leriyle AYNI
    // gerekçe.
    isOwn: Boolean = false,
    onLikeClick: () -> Unit = {},
    onReactionSelected: (String) -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    val avatarSize = if (isReply) 26.dp else 32.dp
    var showActionsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box {
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
            // Uzun basma: emoji tepki seçici + (kendi yorumumsa) Sil menüsü
            // açar — PostCard'ın "üç nokta menüsü" yerine, mesaj balonlarındaki
            // (ConversationScreen) "uzun bas" deseniyle TUTARLI (yorum satırı
            // zaten dar, ayrı bir ikon için yer sıkışık).
            .combinedClickable(onClick = {}, onLongClick = { showActionsMenu = true })
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
            // Sticker — kullanıcı raporu: yorumlarda hiç görünmüyordu (CommentDto.sticker
            // doğru tipliydi ama render eden kod hiç yazılmamıştı). ConversationScreen'in
            // MessageBubble'ındaki AYNI boyut/desen (gerçek bir çıkartma boyutu, tam
            // ekran görsel gibi büyük değil).
            comment.sticker?.imageUrl?.takeIf { it.isNotBlank() }?.let { stickerUrl ->
                AsyncImage(
                    model = stickerUrl,
                    contentDescription = "Çıkartma",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(if (isReply) 64.dp else 80.dp),
                )
            }
            // Madde 3 (kullanıcı raporu: yorumlarda GIF gözükmüyor) — sticker ile
            // AYNI kapsam-dışı-bırakma deseni: CommentDto.gifUrl doğru tipliydi
            // ama render eden kod hiç yazılmamıştı. GIF'ler genelde sticker'dan
            // daha geniş olduğu için fillMaxWidth + heightIn(max) kullanılır
            // (sabit kare boyut YERİNE, GIF'in kendi oranı korunur).
            comment.gifUrl?.takeIf { it.isNotBlank() }?.let { gifUrl ->
                AsyncImage(
                    model = gifUrl,
                    contentDescription = "GIF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .heightIn(max = if (isReply) 120.dp else 180.dp)
                        .clip(MaterialTheme.shapes.small),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // Kalp ikonu artık tıklanabilir (ÖNCEDEN sayı sadece
                // gösteriliyordu, comment_likes'a hiç aksiyon yoktu) —
                // PostCard'ın beğeni ikonuyla AYNI görsel dil (dolu/boş kalp).
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = if (comment.likedByMe) "Beğeniyi geri al" else "Beğen",
                    tint = if (comment.likedByMe) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onLikeClick),
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
            // Tepki çipleri — ConversationScreen'deki ReactionChip ile AYNI
            // görsel desen (ayrı bir composable, MessageReactionDto/
            // CommentReactionDto farklı tipler olduğu için tekrar YAZILDI,
            // paylaşılan bir tip İCAT edilmedi).
            val reactions = comment.reactions
            if (!reactions.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    reactions.forEach { reaction -> CommentReactionChip(reaction) }
                }
            }
        }
    }

    // Uzun basınca: emoji tepki seçici + (kendi yorumumsa) Sil — PostActionsSheet'in
    // AlertDialog onay deseniyle AYNI, sadece burada tam bir ModalBottomSheet
    // yerine daha hafif bir DropdownMenu kullanıldı (satır zaten dar/sık).
    DropdownMenu(
        expanded = showActionsMenu,
        onDismissRequest = { showActionsMenu = false },
    ) {
        ReactionPicker(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            onReactionSelected = { emoji ->
                showActionsMenu = false
                onReactionSelected(emoji)
            },
        )
        if (isOwn) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showActionsMenu = false
                    showDeleteConfirm = true
                },
            )
        }
    }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Yorumu sil") },
            text = { Text("Bu yorumu silmek istediğine emin misin? Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
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
private fun CommentReactionChip(reaction: CommentReactionDto) {
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
private fun CommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSendGif: (String) -> Unit,
    onSendSticker: (String) -> Unit,
    replyingTo: CommentDto?,
    onCancelReply: () -> Unit,
    // @etiketleme otomatik tamamlama — web'in mentionAutocomplete.js dropdown'ıyla
    // AYNI amaç, PostDetailViewModel.onCommentTextChange() zaten debounce'lı
    // arama tetikliyor, burada SADECE sonuçları göstermek/seçmek var.
    mentionSuggestions: List<MentionSuggestionDto> = emptyList(),
    onMentionSelected: (String) -> Unit = {},
) {
    // GIF/Sticker seçici (Faz 5 Dalga 3B) — ConversationScreen'deki AYNI
    // "seç=gönder" deseni (MediaPickerSheet dosya yorumuna bkz.): seçim
    // yapılır yapılmaz sheet kapanır ve yorum DOĞRUDAN gönderilir.
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

    // Surface + tonalElevation: input bar'ı üstteki yorum listesinden görsel
    // olarak AYIRIYOR (ConversationScreen'deki mesaj input bar'ıyla tutarlı
    // bir "sabit alt çubuk" hissi) — işlev (onSend/onTextChange) DEĞİŞMEDİ.
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            if (mentionSuggestions.isNotEmpty()) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    mentionSuggestions.forEach { user ->
                        val username = user.username ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMentionSelected(username) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!user.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = user.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
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
                IconButton(onClick = { showMediaPicker = true }) {
                    Icon(
                        Icons.Filled.Gif,
                        contentDescription = "GIF veya çıkartma ekle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
