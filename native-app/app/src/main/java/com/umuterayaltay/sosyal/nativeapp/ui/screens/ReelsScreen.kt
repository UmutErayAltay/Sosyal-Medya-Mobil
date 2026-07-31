package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartDisplay
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
 * Bu turun BİLİNÇLİ SINIRI: video OLUŞTURMA yok (sadece izleme/kaydırma).
 * Faz 4: beğeni/yorum artık gerçek aksiyon — ReelOverlay'deki kalp ikonu
 * [ReelsViewModel.toggleLike] çağırır, yorum ikonu [onNavigateToPostDetail]
 * ile post-detay ekranına gider (Feed/Discover/Profil'deki PostCard ile AYNI
 * felsefe, sadece burada paylaşılan PostCard yerine kendi overlay'i var).
 */
@Composable
fun ReelsScreen(
    onSessionExpired: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
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
            posts.isEmpty() && loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    text = "Reels yükleniyor…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            posts.isEmpty() && error != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Button(
                    onClick = { viewModel.loadPage(1) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Tekrar dene") }
            }
            posts.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SmartDisplay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    text = "Henüz reels yok",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "Kısa videolar paylaşıldıkça burada akacak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
                        onLikeClick = { viewModel.toggleLike(it.id) },
                        onCommentClick = { onNavigateToPostDetail(it.id) },
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
private fun ReelPage(post: Post, isActive: Boolean, onLikeClick: (Post) -> Unit, onCommentClick: (Post) -> Unit) {
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

        // Alt yarıda kademeli kararma — pill'lerin DIŞINDA kalan video alanı
        // (ör. parlak/beyaz bir video karesi) de metin/ikon okunabilirliğini
        // bozmasın diye. colorScheme.surface tonu KULLANILIYOR (siyah bir
        // "scrim" yerine) — ReelOverlay'deki "onSurface HER ZAMAN surface'e
        // karşı kontrastlı" garantisi bu gradyan için de geçerli kalsın diye,
        // aksi halde light temada koyu onSurface metni neredeyse siyah bir
        // zemine karşı OKUNMAZ hale gelirdi.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        ),
                    ),
                ),
        )

        ReelOverlay(post = post, onLikeClick = onLikeClick, onCommentClick = onCommentClick)
    }
}

/**
 * Video üzerine bindirilmiş bilgi katmanı — altta kullanıcı adı/avatar/caption,
 * sağda dikey beğeni/yorum sayacı sütunu. Faz 4: ikisi de artık tıklanabilir —
 * beğeni ikonu [onLikeClick] ile toggle eder ([Post.likedByMe] true iken
 * colorScheme.error, değilse colorScheme.secondary — PostCard ile AYNI renk
 * kuralı), yorum ikonu [onCommentClick] ile post-detay ekranına gider.
 * Arkaplan olarak sabit siyah/beyaz yerine colorScheme.surface (yarı saydam) +
 * colorScheme.onSurface metin/ikon rengi kullanılıyor — tasarım kısıtı gereği
 * ("SADECE colorScheme.* renkleri") HEM light HEM dark temada okunabilirlik
 * garanti ediliyor (onSurface tanım gereği surface'e karşı kontrastlı).
 */
@Composable
private fun ReelOverlay(post: Post, onLikeClick: (Post) -> Unit, onCommentClick: (Post) -> Unit) {
    val scrim = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)

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
                .background(scrim, MaterialTheme.shapes.large)
                .padding(14.dp),
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
                .background(scrim, MaterialTheme.shapes.large)
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Beğenildiğinde ikon hafifçe büyüyüp geri iniyor — SADECE görsel
            // bir "delight" mikro-etkileşimi, [onLikeClick] mantığı DEĞİŞMEDİ.
            val likeScale by animateFloatAsState(
                targetValue = if (post.likedByMe) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.4f),
                label = "reel-like-scale",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onLikeClick(post) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = if (post.likedByMe) "Beğenmekten vazgeç" else "Beğen",
                    tint = if (post.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .size(28.dp)
                        .scale(likeScale),
                )
                Text(
                    text = "${post.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onCommentClick(post) },
            ) {
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
