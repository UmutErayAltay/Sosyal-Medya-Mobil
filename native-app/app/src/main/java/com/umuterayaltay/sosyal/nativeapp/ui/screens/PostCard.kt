package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Post

/**
 * Paylaşılan gönderi kartı — FeedScreen.kt'den ÇIKARILDI (Faz 3, Keşfet ekranı),
 * kopyalanmadı: hem Ana Sayfa hem Keşfet hem Profil AYNI composable'ı kullanır.
 * Girdi tipi repository.Post (FeedRepository/DiscoverRepository/ProfileRepository'nin
 * hepsi PostDto'yu PostDto.toDomain() ile bu tipe eşler) — iki farklı Post
 * modeli YARATILMADI.
 *
 * Faz 4: [onLikeClick]/[onCommentClick] ile artık gerçek aksiyona bağlı —
 * kalp ikonu [Post.likedByMe] true iken dolu/kırmızı (colorScheme.error),
 * değilse nötr colorScheme.onSurfaceVariant rengiyle gösterilir (görsel cila
 * turu, 2026-07-31: aktif/pasif ayrımını netleştirmek için secondary'den
 * değiştirildi — sadece renk, [onLikeClick] mantığı AYNI).
 *
 * Hashtag+gündem (Faz 4 sonrası eksik giderme SONUNCUSU): içerikteki #etiket'ler
 * [onHashtagClick] ile tıklanabilir — app/hashtags.py HASHTAG_RE'nin (`#(\w+)`,
 * Unicode farkında) AYNI davranışı burada `\p{L}`/`\p{N}` Unicode kategori
 * kaçışlarıyla kopyalanır (Kotlin/Java `\w` VARSAYILAN olarak Unicode farkında
 * DEĞİL — düz `\w` kullansaydık ç/ğ/ı/ö/ş/ü içeren etiketler yanlış
 * ayrıştırılırdı; `\p{L}`/`\p{N}` ise HER ZAMAN Unicode farkında, ekstra bayrak
 * gerekmez).
 */
private val HASHTAG_REGEX = Regex("#([\\p{L}\\p{N}_]+)")

private fun buildContentAnnotatedString(content: String, hashtagColor: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var lastEnd = 0
        for (match in HASHTAG_REGEX.findAll(content)) {
            append(content.substring(lastEnd, match.range.first))
            val start = length
            withStyle(SpanStyle(color = hashtagColor)) {
                append(match.value)
            }
            addStringAnnotation(
                tag = "hashtag",
                annotation = match.groupValues[1].lowercase(),
                start = start,
                end = length,
            )
            lastEnd = match.range.last + 1
        }
        append(content.substring(lastEnd))
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
    post: Post,
    onLikeClick: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onHashtagClick: (String) -> Unit = {},
    // Faz 5 (Dalga 1A): VARSAYILAN DEĞERLİ — anket bağlamayan çağrı yerleri
    // (henüz bağlanmamış ekranlar) değişmeden derlenmeye devam eder.
    onPollVote: (postId: String, optionId: String) -> Unit = { _, _ -> },
    // Faz 5 Dalga 2A: VARSAYILAN DEĞERLİ ŞART — mevcut çağrı yerleri (henüz
    // bağlanmamış ekranlar) bu parametre olmadan da değişmeden derlenmeye
    // devam eder (onPollVote ile AYNI gerekçe).
    onMutePost: (postId: String) -> Unit = {},
    // Faz 5 Dalga 3A: onMutePost ile AYNI gerekçeyle VARSAYILAN DEĞERLİ.
    onBookmark: (postId: String) -> Unit = {},
) {
    var showActionsSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!post.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    )
                } else {
                    // Avatarsız kullanıcılar için de dolgusuz çıplak ikon yerine
                    // dairesel bir zemin — böylece boş alan bir "boşluk" gibi
                    // değil, bilinçli bir yer tutucu gibi görünür.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = post.username ?: "Bilinmeyen kullanıcı",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val timeLabel = formatClockTime(post.createdAt)
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { showActionsSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Diğer seçenekler",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!post.content.isNullOrBlank()) {
                val hashtagColor = MaterialTheme.colorScheme.primary
                val contentColor = MaterialTheme.colorScheme.onSurface
                val annotatedContent = remember(post.content, hashtagColor) {
                    buildContentAnnotatedString(post.content, hashtagColor)
                }
                ClickableText(
                    text = annotatedContent,
                    style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = { offset ->
                        annotatedContent.getStringAnnotations(tag = "hashtag", start = offset, end = offset)
                            .firstOrNull()?.let { onHashtagClick(it.item) }
                    },
                )
            }

            // Anket — web'in _post_card.html'indeki AYNI konum: içerikten SONRA,
            // görselden ÖNCE. Room cache'inden gelen postta poll null olduğu için
            // offline'da widget hiç çizilmez (bkz. PostEntity.toDomain() yorumu).
            post.poll?.let { poll ->
                PollWidget(
                    poll = poll,
                    onVote = { optionId -> onPollVote(post.id, optionId) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (!post.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .padding(top = 12.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onLikeClick(post) }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = if (post.likedByMe) "Beğenmekten vazgeç" else "Beğen",
                        tint = if (post.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = " ${post.likeCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onCommentClick(post) }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Yorum",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = " ${post.commentCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    if (showActionsSheet) {
        PostActionsSheet(
            post = post,
            onMutePost = onMutePost,
            onBookmark = onBookmark,
            onDismiss = { showActionsSheet = false },
        )
    }
}
