package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * değilse mevcut colorScheme.secondary rengiyle gösterilir.
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

@Composable
fun PostCard(
    post: Post,
    onLikeClick: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onHashtagClick: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!post.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    text = post.username ?: "Bilinmeyen kullanıcı",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
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
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = { offset ->
                        annotatedContent.getStringAnnotations(tag = "hashtag", start = offset, end = offset)
                            .firstOrNull()?.let { onHashtagClick(it.item) }
                    },
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
                        .padding(top = 10.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onLikeClick(post) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = if (post.likedByMe) "Beğenmekten vazgeç" else "Beğen",
                        tint = if (post.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = " ${post.likeCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onCommentClick(post) },
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Yorum",
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
}
