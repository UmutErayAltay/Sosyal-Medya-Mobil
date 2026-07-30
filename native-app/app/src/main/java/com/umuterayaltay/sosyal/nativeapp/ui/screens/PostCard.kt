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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
 */
@Composable
fun PostCard(post: Post, onLikeClick: (Post) -> Unit, onCommentClick: (Post) -> Unit) {
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
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 10.dp),
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
