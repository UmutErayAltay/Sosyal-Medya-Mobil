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
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
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
            // .clip() SADECE oynatmıyorken uygulanır — WebView'ı (Chromium
            // draw-functor) saran her kırpma/graphics-layer katmanı donanım
            // video yolunun kompozisyon modunu bozup SİYAH KAREYE
            // düşürebiliyor (SurfaceView/offscreen-compositing çakışması,
            // kullanıcı raporu "siyah ekran kalıyor"un kök nedeni). .clip()
            // ÇİZİM-fazı modifier'ı (graphicsLayer) — ölçüm/yerleşime
            // dokunmaz, bu yüzden koşullu hale getirmek layout regresyonu
            // YARATMAZ (matchParentSize düzeltmesindeki riskten FARKLI).
            // Yuvarlak görünüm zaten .border() ile çiziliyor, .clip()'e bağlı
            // DEĞİL. Asıl düzeltme aşağıdaki embed yükleme değişikliği (aynı
            // commit'te, bilinçli olarak birlikte) — bkz. YouTubeEmbedWebView.
            .then(if (playing) Modifier else Modifier.clip(MaterialTheme.shapes.medium))
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
 * Uygulamadaki İLK WebView kullanımı. `videoId` çağıran yerde (`extractYouTubeId`)
 * 11 karakterlik `YT_ID_RE` ile DOĞRULANMIŞ — HTML/URL enjeksiyonu mümkün
 * değil, bu yüzden aşağıdaki string interpolasyonu güvenli.
 *
 * Embed, `webView.loadUrl(embedUrl)` ile ÜST SEVİYE doküman olarak DEĞİL,
 * kendi ürettiğimiz küçük bir HTML sayfasına gömülü `<iframe>` olarak,
 * `loadDataWithBaseURL("https://www.youtube.com", ...)` ile yükleniyor — web
 * tarafında ZATEN ÇALIŞAN yaklaşımın (linkPreview.js'te sayfaya gömülü
 * `<iframe>`) native karşılığı. Bare `loadUrl()` embed sayfasını gerçek bir
 * YouTube-güvenilir origin/referrer OLMADAN top-level navigasyon gibi
 * açıyordu — kullanıcı raporundaki "siyah ekran" büyük ölçüde buna bağlıydı.
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
        // Sadece debug build'de chrome://inspect ile WebView içi incelenebilsin
        // — RetrofitClient.kt'deki BuildConfig.DEBUG deseniyle AYNI. Statik
        // metod, tekrar çağrılması zararsız.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            webChromeClient = android.webkit.WebChromeClient()
            // loadDataWithBaseURL ile yüklenen sayfada iframe içi
            // navigasyonların WebView'da KALMASI için ŞART (client YOKKEN
            // bazı navigasyonlar harici tarayıcıya/YouTube app'ine kaçar).
            webViewClient = android.webkit.WebViewClient()
            setBackgroundColor(android.graphics.Color.BLACK)

            val html = """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}
                iframe{border:0;display:block;width:100%;height:100%}</style>
                </head><body>
                <iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&fs=0&rel=0&modestbranding=1"
                        allow="autoplay; encrypted-media; picture-in-picture"></iframe>
                </body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
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
