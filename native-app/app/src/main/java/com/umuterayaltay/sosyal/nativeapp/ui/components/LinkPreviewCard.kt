package com.umuterayaltay.sosyal.nativeapp.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
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
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.LinkPreviewDto
import com.umuterayaltay.sosyal.nativeapp.repository.LinkPreviewResult

/**
 * Post/mesajlarda paylaşılan http(s) linkler için Open Graph önizleme kartı —
 * web'in `linkPreview.js`'i ile AYNI davranış/platform ayrımı (YouTube tıkla-
 * oynat embed, Twitter/X tweet-stili kart, diğerleri generic kart). PostCard/
 * CommentRow/MessageBubble'ın her biri bu TEK composable'ı çağırır (backend
 * çağrısı+cache tek yerde, WebView/AsyncImage detayları tekrarlanmaz).
 *
 * Fetch başarısız veya ok:false → HİÇBİR ŞEY render edilmez (backend'in
 * gifs.py/turn-credentials proxy'lerindeki graceful-degradation felsefesiyle
 * aynı — kritik olmayan bir özellik, sessizce geç).
 */
private val urlPreviewCache = HashMap<String, LinkPreviewDto?>() // url -> null (yok/hata) veya dolu — proses içi, ServiceLocator ile AYNI ömür

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
    var preview by remember(url) { mutableStateOf(urlPreviewCache[url]) }
    var fetched by remember(url) { mutableStateOf(urlPreviewCache.containsKey(url)) }

    LaunchedEffect(url) {
        if (fetched) return@LaunchedEffect
        val result = ServiceLocator.linkPreviewRepository.getPreview(url)
        val data = (result as? LinkPreviewResult.Success)?.preview
        urlPreviewCache[url] = data
        preview = data
        fetched = true
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
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (playing) {
                // matchParentSize() ŞART — Box'ın aspectRatio ile belirlenen
                // yüksekliğini DOLDURMAK için (fillMaxWidth() sadece genişliği
                // doldurur, AndroidView'ın kendi "intrinsic" yüksekliği
                // olmadığından WebView neredeyse 0 yükseklikte, GÖRÜNMEZ
                // kalırdı — kullanıcı raporu "video oynatmıyor"un kök nedeni
                // buydu, PostVideoPlayer'ın AYNI Box+matchParentSize deseni
                // burada da uygulandı).
                YouTubeEmbedWebView(videoId = videoId, modifier = Modifier.matchParentSize())
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
 * Uygulamadaki İLK WebView kullanımı — sadece SABİT, kendi ürettiğimiz
 * `youtube-nocookie.com/embed/{id}` URL'sini yükler (11 karakterlik video
 * ID'si regex ile doğrulanmış), kullanıcının/dış sitenin verdiği HAM HTML/URL
 * ASLA yüklenmez — bu yüzden JavaScript etkinleştirmek (embed player'ın
 * çalışması için ZORUNLU) güvenlik riski taşımaz. Kullanıcı play butonuna
 * BASMADAN hiçbir YouTube isteği/çerezi gitmez (tıkla-oynat), autoplay=1
 * SADECE bu tıklamadan SONRA devreye girer.
 */
@Composable
private fun YouTubeEmbedWebView(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // remember: LazyColumn recompose/scroll'da her seferinde YENİ bir WebView
    // yaratılmasın (ExoPlayer'ı PostVideoPlayer'da remember(videoUrl) ile
    // tutmakla AYNI gerekçe) — composable disposed olunca (scroll'da item
    // dışarı çıkınca) destroy() ŞART, yoksa arka planda oynamaya devam eden/
    // bellek sızdıran WebView instance'ları birikir.
    val webView = remember(videoId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // YouTube'un embed player'ı durumu (oynatma ilerlemesi vb.)
            // localStorage/sessionStorage'a yazıyor — DOM storage KAPALIYSA
            // player HİÇBİR hata vermeden sessizce başlatılamıyor (siyah
            // ekran/oynamıyor gibi görünüyor). webChromeClient de bazı
            // cihazlarda video elemanının render edilmesi için gerekiyor
            // (varsayılan WebClient'ın aksine progress/video callback'lerini
            // karşılıyor).
            settings.domStorageEnabled = true
            webChromeClient = android.webkit.WebChromeClient()
            loadUrl("https://www.youtube-nocookie.com/embed/$videoId?autoplay=1")
        }
    }
    DisposableEffect(videoId) {
        onDispose { webView.destroy() }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

// --- Tweet-benzeri kart ---------------------------------------------------
@Composable
private fun TweetLinkPreviewCard(data: LinkPreviewDto, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!data.image.isNullOrBlank()) {
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
                    modifier = Modifier.padding(start = 8.dp),
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
