package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ReelsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ReelsViewModel

/**
 * Reels sekmesi — tam ekran dikey video akışı (TikTok/Reels tarzı, bir sefer
 * tam ekran kaydırma). Backend sözleşmesi: app/api_v1.py api_reels()
 * (DiscoverResponse ile AYNI şekil: posts/has_more/page).
 *
 * Bu turun BİLİNÇLİ SINIRI: video OLUŞTURMA yok (sadece izleme/kaydırma),
 * beğeni/yorum aksiyonları yok (Feed/Discover/Profil'deki gibi sadece sayı
 * gösterimi, tıklanabilir değil).
 */
@Composable
fun ReelsScreen(
    onSessionExpired: () -> Unit,
    viewModel: ReelsViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReelsEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            posts.isEmpty() && loading -> CircularProgressIndicator()
            posts.isEmpty() && error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "", color = MaterialTheme.colorScheme.onBackground)
                Button(
                    onClick = { viewModel.loadPage(1) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Tekrar dene") }
            }
            posts.isEmpty() -> Text(
                text = "Henüz reels yok",
                color = MaterialTheme.colorScheme.onBackground,
            )
            else -> {
                val pagerState = rememberPagerState(pageCount = { posts.size })

                // Discover'daki sonsuz-kaydırma deseniyle tutarlı, ama tetikleyici
                // LazyColumn scroll'u yerine VerticalPager'ın currentPage'i —
                // loadMore() zaten hasMore/loading guard'lı, tekrar tetiklenmesi zararsız.
                LaunchedEffect(pagerState, posts.size) {
                    snapshotFlow { pagerState.currentPage }.collect { page ->
                        if (page >= posts.size - 2) {
                            viewModel.loadMore()
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { posts[it].id },
                ) { page ->
                    ReelPage(
                        post = posts[page],
                        isActive = pagerState.currentPage == page,
                    )
                }
            }
        }
    }
}

/**
 * Tek bir reel sayfası — video + üzerinde bindirilmiş (Box overlay) kullanıcı/
 * caption/beğeni-yorum sayıları. [isActive] SADECE görünen sayfa true olur;
 * false olduğunda video duraklatılır, composition'dan kalkınca ExoPlayer
 * release edilir (bellek sızıntısı/arka planda çalan video olmaması için).
 */
@Composable
private fun ReelPage(post: Post, isActive: Boolean) {
    val videoUrl = post.videoUrl

    Box(modifier = Modifier.fillMaxSize()) {
        if (!videoUrl.isNullOrBlank()) {
            val context = LocalContext.current

            val exoPlayer = remember(post.id) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false
                    prepare()
                }
            }

            DisposableEffect(post.id) {
                onDispose { exoPlayer.release() }
            }

            LaunchedEffect(isActive) {
                if (isActive) exoPlayer.play() else exoPlayer.pause()
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Savunmacı: backend is_reel+video_url NOT NULL filtreliyor, ama
            // beklenmedik bir null gelirse sessizce boş bir zemin göster.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        ReelOverlay(post = post)
    }
}

/**
 * Video üzerine bindirilmiş bilgi katmanı — altta kullanıcı adı/avatar/caption,
 * sağda dikey beğeni/yorum sayacı sütunu. İkisi de SADECE gösterim, tıklanabilir
 * DEĞİL (bu turun bilinçli sınırı). Arkaplan olarak sabit siyah/beyaz yerine
 * colorScheme.surface (yarı saydam) + colorScheme.onSurface metin/ikon rengi
 * kullanılıyor — tasarım kısıtı gereği ("SADECE colorScheme.* renkleri") HEM
 * light HEM dark temada okunabilirlik garanti ediliyor (onSurface tanım gereği
 * surface'e karşı kontrastlı).
 */
@Composable
private fun ReelOverlay(post: Post) {
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(scrim, MaterialTheme.shapes.medium)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!post.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    text = post.username ?: "Bilinmeyen kullanıcı",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (!post.content.isNullOrBlank()) {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .background(scrim, MaterialTheme.shapes.medium)
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Beğeni",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = "${post.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = "Yorum",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = "${post.commentCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
