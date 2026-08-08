package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.StoryBarItem

/**
 * Feed'in üstündeki hikaye çubuğu — web'in feed sayfasındaki hikaye
 * şeridinin Compose karşılığı (bkz. app/stories.py active_stories_bar()).
 * Yatay `LazyRow`, her item bir avatar halkası: görülmemiş hikayesi olan
 * kullanıcı RENKLİ (primary) halka, hepsi görülmüş olan SOLUK/gri halka —
 * web'in `all_seen` mantığıyla AYNI (Instagram deseni). İlk öğe HER ZAMAN
 * "Hikaye Ekle" (+) butonu — web'in de kendi hikayeni ÖNE aldığı davranışının
 * (active_stories_bar() `result.sort` satırı) native tarafta karşılığı: kendi
 * hikayen zaten backend'den ilk sırada geliyor, buradaki "+" onun YANINDA/
 * ÖNÜNDE sabit bir giriş noktası.
 */
@Composable
fun StoryBar(
    items: List<StoryBarItem>,
    onAddStoryClick: () -> Unit,
    onStoryClick: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AddStoryItem(onClick = onAddStoryClick)
        }
        items(items, key = { it.userId }) { item ->
            StoryBarAvatar(item = item, onClick = { onStoryClick(item.userId) })
        }
    }
}

@Composable
private fun AddStoryItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Hikaye Ekle",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Hikaye Ekle",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StoryBarAvatar(item: StoryBarItem, onClick: () -> Unit) {
    // Animasyon turu (3. kısım) — okunmamış -> okunmuş halka rengi ARTIK
    // animateColorAsState ile YUMUŞAK geçiyor (web'in all_seen anlık
    // renk değişiminin AKSİNE, native'de bir hikaye izlenince halka
    // kademeli soluyor). Ayrıca halka listede belirirken hafif bir
    // scale-in (0.6f -> 1f, hafif zıplamalı spring) uygulanıyor —
    // remember(item.userId) SADECE bu avatar ilk kez composition'a
    // girdiğinde tetiklenir, allSeen güncellenip aynı öğe recompose
    // olduğunda scale TEKRAR oynatılmaz (sadece renk geçer).
    val targetRingColor = if (item.allSeen) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ringColor by animateColorAsState(
        targetValue = targetRingColor,
        animationSpec = tween(300),
        label = "storyRingColor",
    )

    var appeared by remember(item.userId) { mutableStateOf(false) }
    LaunchedEffect(item.userId) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "storyRingScale",
    )

    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(scale)
                .clip(CircleShape)
                .border(width = 2.dp, color = ringColor, shape = CircleShape)
                .padding(3.dp),
        ) {
            if (!item.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = item.username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
