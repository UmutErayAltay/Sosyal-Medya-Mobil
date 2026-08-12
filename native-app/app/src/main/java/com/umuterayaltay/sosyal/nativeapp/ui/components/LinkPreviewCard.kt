package com.umuterayaltay.sosyal.nativeapp.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.LinkPreviewDto

/**
 * Post/mesajlarda paylaşılan http(s) linkler için Open Graph önizleme kartı —
 * web'in `linkPreview.js`'i ile AYNI davranış/platform ayrımı (YouTube tıkla-
 * oynat embed, Twitter/X tweet-stili kart, diğerleri generic kart). PostCard/
 * CommentRow/MessageBubble'ın her biri bu TEK composable'ı çağırır (backend
 * çağrısı+cache tek yerde, YouTubePlayerView/AsyncImage detayları tekrarlanmaz).
 *
 * Fetch başarısız veya ok:false → HİÇBİR ŞEY render edilmez (backend'in
 * gifs.py/turn-credentials proxy'lerindeki graceful-degradation felsefesiyle
 * aynı — kritik olmayan bir özellik, sessizce geç).
 *
 * Batch C4 (Akış kaydırma performansı): fetch dedupe + negatif cache artık
 * BURADA değil, LinkPreviewRepository.previewOrNull()'da (`Deferred`-anahtarlı,
 * repository'nin kendi coroutine scope'unda) tutuluyor — dosya-seviyesi
 * `urlPreviewCache` HashMap'i (item scroll'dan çıkıp LaunchedEffect iptal
 * olunca hiçbir şey cache'lemiyordu) BİLİNÇLİ olarak KALDIRILDI.
 */
private val URL_REGEX = Regex("""https?://[^\s<>"']+""")

private val URL_TRAILING_PUNCT = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

fun extractFirstUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val match = URL_REGEX.find(text) ?: return null
    // Sondaki yaygın noktalama linke dahil edilmez — web'in linkify_urls'teki
    // AYNI trailing-strip kuralı (app/link_preview.py).
    var end = match.range.last + 1
    while (end > match.range.first && text[end - 1] in URL_TRAILING_PUNCT) end--
    return text.substring(match.range.first, end).takeIf { it.isNotBlank() }
}

/**
 * Sadece URL linkify — CommentRow/MessageBubble için (PostCard'ın AKSİNE
 * hashtag YOK, kapsam dışı bırakıldı: web tarafında da yorum/mesaj'da hashtag
 * filtresi kullanılmıyor, sadece linkify_urls/linkify_mentions — bkz.
 * app/templates/post_detail.html yorum satırı).
 */
fun buildUrlOnlyAnnotatedString(content: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        var lastEnd = 0
        for (m in URL_REGEX.findAll(content)) {
            var end = m.range.last
            while (end >= m.range.first && content[end] in URL_TRAILING_PUNCT) end--
            if (end < m.range.first) continue
            append(content.substring(lastEnd, m.range.first))
            val start = length
            val url = content.substring(m.range.first, end + 1)
            withStyle(SpanStyle(color = linkColor)) { append(url) }
            addStringAnnotation(tag = "url", annotation = url, start = start, end = length)
            lastEnd = end + 1
        }
        append(content.substring(lastEnd))
    }

@Composable
fun LinkPreviewCard(url: String, modifier: Modifier = Modifier) {
    var preview by remember(url) { mutableStateOf<LinkPreviewDto?>(null) }

    LaunchedEffect(url) {
        preview = ServiceLocator.linkPreviewRepository.previewOrNull(url)
    }

    val data = preview ?: return
    val context = LocalContext.current
    val onOpen: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.url ?: url)))
        } catch (e: ActivityNotFoundException) {
            // Tarayıcı yok/açılamadı — kritik değil, sessizce geç.
        }
    }

    val ytId = extractYouTubeId(data.url) ?: extractYouTubeId(url)
    when {
        ytId != null -> YouTubeLinkPreviewCard(data = data, videoId = ytId, onOpen = onOpen, modifier = modifier)
        isTweetPreview(data) -> TweetLinkPreviewCard(data = data, onOpen = onOpen, modifier = modifier)
        else -> GenericLinkPreviewCard(data = data, onOpen = onOpen, modifier = modifier)
    }
}

// --- YouTube tespiti ------------------------------------------------------
// youtube.com/watch?v=ID, youtu.be/ID, youtube.com/shorts/ID (www./m. opsiyonel).
private val YT_ID_RE = Regex("^[\\w-]{11}$")
private fun extractYouTubeId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val uri = try { Uri.parse(url) } catch (e: Exception) { return null }
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    return when (host) {
        "youtu.be" -> uri.pathSegments.firstOrNull()?.takeIf { YT_ID_RE.matches(it) }
        "youtube.com" -> {
            if (uri.path == "/watch") {
                uri.getQueryParameter("v")?.takeIf { YT_ID_RE.matches(it) }
            } else if (uri.pathSegments.firstOrNull() == "shorts") {
                uri.pathSegments.getOrNull(1)?.takeIf { YT_ID_RE.matches(it) }
            } else {
                null
            }
        }
        else -> null
    }
}

// --- Twitter/X tespiti -----------------------------------------------------
private val TWEET_DOMAINS = setOf("twitter.com", "x.com", "mobile.twitter.com")
private fun isTweetPreview(data: LinkPreviewDto): Boolean {
    val domain = data.domain?.lowercase()
    if (domain in TWEET_DOMAINS) return true
    val siteName = data.siteName?.lowercase() ?: return false
    return siteName.contains("twitter") || siteName.contains("x (formerly twitter)")
}

private fun stripTweetTitleSuffix(title: String): String =
    title.replace(Regex("\\s+on\\s+(x|twitter)\\s*$", RegexOption.IGNORE_CASE), "").trim()

// --- Generic kart ------------------------------------------------------
@Composable
private fun GenericLinkPreviewCard(data: LinkPreviewDto, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
    ) {
        if (!data.image.isNullOrBlank()) {
            AsyncImage(
                model = data.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            if (!data.title.isNullOrBlank()) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!data.description.isNullOrBlank()) {
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val domainText = data.siteName ?: data.domain
            if (!domainText.isNullOrBlank()) {
                Text(
                    text = domainText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// --- YouTube kartı — tıkla-oynat gömülü video -----------------------------
@Composable
private fun YouTubeLinkPreviewCard(data: LinkPreviewDto, videoId: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var playing by remember(videoId) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // .clip() SADECE oynatmıyorken uygulanır — video render eden
            // View'ı (SurfaceView/TextureView tabanlı olabilir) saran her
            // kırpma/graphics-layer katmanının donanım video yolunu
            // bozabileceği teorisine karşı ZARARSIZ bir önlem (çizim-fazı
            // modifier, ölçüm/yerleşime dokunmaz — layout regresyonu riski
            // YOK). Asıl düzeltme YouTubeEmbedPlayer'ın çıplak WebView yerine
            // android-youtube-player kütüphanesini kullanması (bkz. o
            // composable'ın yorumu + build.gradle.kts bağımlılık notu).
            .then(if (playing) Modifier else Modifier.clip(MaterialTheme.shapes.medium))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (playing) {
                // matchParentSize() ŞART — Box'ın aspectRatio ile belirlenen
                // yüksekliğini DOLDURMAK için (fillMaxWidth() sadece genişliği
                // doldurur, AndroidView'ın kendi "intrinsic" yüksekliği
                // olmadığından YouTubePlayerView neredeyse 0 yükseklikte,
                // GÖRÜNMEZ kalırdı — bu dersi ilk WebView denemesinde
                // öğrenmiştik, PostVideoPlayer'ın AYNI Box+matchParentSize
                // deseni burada da uygulandı).
                YouTubeEmbedPlayer(videoId = videoId, modifier = Modifier.matchParentSize())
            } else {
                AsyncImage(
                    model = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clickable { playing = true },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "▶", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (!data.title.isNullOrBlank()) {
            Column(modifier = Modifier.padding(10.dp).clickable(onClick = onOpen)) {
                Text(text = data.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = "YouTube", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * ÖNCE çıplak WebView + loadUrl (top-level embed navigasyonu), SONRA
 * loadDataWithBaseURL ile gömülü iframe olarak denendi — ikisi de gerçek
 * cihazda siyah ekranla sonuçlandı (kullanıcı raporu, iki ayrı düzeltme
 * turu sonrası da doğrulandı). Çıplak WebView'i cihaz erişimi olmadan daha
 * fazla debug etmek yerine, YouTube'un resmi IFrame Player API'sini DOĞRU
 * origin/postMessage yönetimiyle uygulayan, binlerce üretim uygulamasında
 * kullanılan `android-youtube-player` kütüphanesine geçildi (bkz.
 * build.gradle.kts bağımlılık yorumu). PostVideoPlayer'daki (bu dosyanın
 * ait olduğu paketin bir üst dizininde, PostCard.kt) ExoPlayer+AndroidView
 * deseniyle BİREBİR aynı: remember + DisposableEffect(release).
 */
@Composable
private fun YouTubeEmbedPlayer(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerView = remember(videoId) {
        YouTubePlayerView(context).apply {
            addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    // videoId çağıran yerde YT_ID_RE ("^[\\w-]{11}$") ile
                    // DOĞRULANMIŞ — enjeksiyon riski yok.
                    youTubePlayer.loadVideo(videoId, 0f)
                }
            })
        }
    }
    DisposableEffect(videoId) {
        onDispose { playerView.release() }
    }
    AndroidView(factory = { playerView }, modifier = modifier)
}

// --- Tweet-benzeri kart ---------------------------------------------------
@Composable
private fun TweetLinkPreviewCard(data: LinkPreviewDto, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Tek bir `image` alanı var: YA avatar YA medya karesi, ikisi asla
    // birlikte gelmez (X'in og:image'i tweet türüne göre birini döndürür) —
    // bu yüzden medya varyantında avatar "kaldırılmıyor", zaten hiç yok.
    val hasMedia = data.imageIsMedia && !data.image.isNullOrBlank()
    val hasAvatar = !data.imageIsMedia && !data.image.isNullOrBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
    ) {
        if (hasMedia) {
            // X, tweet videosunu OYNATILABİLİR sunmuyor (og:video/public embed
            // API YOK) — bu yüzden inline oynatma YOK, sadece gerçek önizleme
            // karesi; karta dokunmak tweet'i tarayıcıda/X uygulamasında açar.
            AsyncImage(
                model = data.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasAvatar) {
                    AsyncImage(
                        model = data.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                    )
                }
                if (!data.title.isNullOrBlank()) {
                    Text(
                        text = stripTweetTitleSuffix(data.title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = if (hasAvatar) 8.dp else 0.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!data.description.isNullOrBlank()) {
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = if (data.domain?.lowercase() == "x.com") "X" else "Twitter",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
