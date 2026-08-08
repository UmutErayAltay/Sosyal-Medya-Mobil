package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.RepostEmbed
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // Faz 5 sonrası eksik giderme: Repost/Paylaş/Bildir — onMutePost/onBookmark
    // ile AYNI gerekçeyle VARSAYILAN DEĞERLİ (henüz bağlanmamış çağrı yerleri
    // değişmeden derlenmeye devam eder, bkz. PostActionsSheet.kt).
    onRepost: (postId: String) -> Unit = {},
    onReport: (postId: String) -> Unit = {},
    // PostCard aksiyon satırı yeniden düzenlemesi: "Gönder" ikonu artık
    // PostActionsSheet'in İÇİNDEN değil, DOĞRUDAN PostCard'ın kendi
    // PostShareSheet'inden açılıyor (aşağıdaki showShareSheet state'i) — bu
    // yüzden PostShareSheet'in zaten gerektirdiği onSessionExpired PostCard'a
    // da bir parametre olarak eklendi. VARSAYILAN DEĞERLİ ({}) — diğer
    // opsiyonel callback'lerle AYNI gerekçe, henüz bağlanmamış çağrı yerleri
    // (varsa) değişmeden derlenmeye devam eder.
    onSessionExpired: () -> Unit = {},
    // Post yönetimi (düzenle/sil/arşivle/sabitle) — SADECE kendi postumda
    // anlamlı, çağıran taraf (Feed/Discover/Hashtag/Profile/PostDetail)
    // `post.userId == kendi id'im` karşılaştırmasını KENDİSİ yapıp geçer
    // (PostCard'a ayrıca "benim id'im nedir" bilgisi taşımak yerine, diğer
    // opsiyonel callback'lerle AYNI "VARSAYILAN DEĞERLİ, henüz bağlanmamış
    // çağrı yerleri değişmeden derlenir" gerekçesi).
    isOwnPost: Boolean = false,
    onEditPost: (postId: String, newContent: String) -> Unit = { _, _ -> },
    onDeletePost: (postId: String) -> Unit = {},
    onArchivePost: (postId: String) -> Unit = {},
    onPinPost: (postId: String) -> Unit = {},
) {
    var showActionsSheet by remember { mutableStateOf(false) }
    // "Düzenle" PostActionsSheet'te tıklanınca açılır — showActionsSheet'in
    // AYNI yerel-state deseni, TextField içeriği post'un GÜNCEL content'iyle
    // her açılışta yeniden başlatılır (remember(post.content) ile).
    var showEditDialog by remember { mutableStateOf(false) }
    var editContent by remember(post.content) { mutableStateOf(post.content ?: "") }
    // "Gönder" ikonu (aksiyon satırında) tıklanınca açılır — showActionsSheet
    // ile AYNI desen, PostCard'ın KENDİ İÇİNDE tutulan yerel bir state
    // (ViewModel'e taşınmadı, PostShareSheet kendi başına yeterli).
    var showShareSheet by remember { mutableStateOf(false) }
    // Madde 2 (kullanıcı raporu: Kaydet ikonuna basılı tutunca koleksiyon
    // seçici açılsın) — PostActionsSheet/PostShareSheet'in showX state
    // deseniyle AYNI, yeni bir callback parametresi GEREKMEDİ.
    var showCollectionPicker by remember { mutableStateOf(false) }
    // BookmarkCollectionPickerSheet ViewModel'e BAĞLANMADAN doğrudan
    // BookmarksRepository çağırdığı için (bkz. o dosyanın yorumu), sheet'in
    // başarılı bir atama sonrası bildirdiği durum burada YEREL olarak
    // tutulur — aksi halde Kaydet ikonu bir sonraki tam listeleme
    // yenilemesine kadar eski (dolu/boş) rengiyle kalırdı. post.id/
    // post.bookmarkedByMe değiştiğinde (ör. liste yeniden yüklendiğinde)
    // override SIFIRLANIR, sunucudan gelen GERÇEK değer tekrar geçerli olur.
    var bookmarkedOverride by remember(post.id, post.bookmarkedByMe) { mutableStateOf<Boolean?>(null) }
    val isBookmarked = bookmarkedOverride ?: post.bookmarkedByMe

    // 2026-08-08 (performans düzeltmesi): kartın KENDİ giriş animasyonu
    // (fade+slide-up) kaldırıldı — çağıran ekranlar (FeedScreen/
    // DiscoverScreen/ProfileScreen/HashtagScreen/TrendingScreen) ZATEN
    // kendi seviyelerinde bir giriş animasyonu uyguluyordu (bkz.
    // PostFeedStaggerReveal/StaggeredPostEntry), bu ikisi ÇİFT sarmalanıp
    // hem gözle görülür "iki aşamalı" bir gecikme yaratıyordu hem de
    // LazyColumn kaydırma sırasında composition'ı disposed/recompose
    // ettiği için animasyon her scroll-geri-gelişte TEKRAR oynuyordu
    // (önceki yorumdaki "composition canlı tutulduğu sürece tekrar
    // tetiklenmez" varsayımı YANLIŞTI — LazyColumn görünür pencerenin
    // dışına çıkan item'ları gerçekten disposed eder). Kullanıcı raporu:
    // "uygulama kasıyor, yükleme çok yavaşladı". Tek animasyon artık
    // SADECE çağıran taraftaki sarmalayıcıda ve sadece gerçekten YENİ
    // post'larda oynuyor (bkz. o dosyalardaki seenKeys/isNew mantığı).
    // Beğeni kalbi + kaydet ikonu için "spring pop" — Animatable ile elle
    // yönetildi (animateXAsState value-driven değil, HER tıklamada aynı
    // spring'i baştan oynatmak gerektiği için scope.launch + animateTo
    // zinciri kullanıldı, bkz. aşağıdaki likeScale/bookmarkScale kullanımı).
    val likeScale = remember { Animatable(1f) }
    val bookmarkScale = remember { Animatable(1f) }
    val animScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            // Kullanıcı raporu: kartın boş alanına dokununca hiçbir şey
            // olmuyordu (SADECE beğeni/yorum satırları tıklanabilirdi) -
            // Instagram/Twitter alışkanlığı: kartın HERHANGİ bir yerine
            // dokununca post detayına gidilsin. Beğeni/yorum satırlarının
            // KENDİ .clickable'ları Compose'un iç-dış çakışma davranışında
            // ÖNCELİKLİDİR (içteki tüketir) - çift tetiklenme olmaz.
            .clickable { onCommentClick(post) },
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

            // Madde 5 (kullanıcı raporu: repost içeriği hiç gösterilmiyor) —
            // web'in `.repost-embed`'iyle AYNI konum: içerikten SONRA, görselden
            // ÖNCE. repostOf SADECE ağdan gelen postta dolu (Room cache'inden
            // gelende null, poll ile AYNI gerekçe — bkz. Post.kt yorumu).
            post.repostOf?.let { repost ->
                RepostEmbedCard(repost = repost, modifier = Modifier.padding(top = 12.dp))
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
                // Madde 4 (kullanıcı raporu: post resmi kırpılmış, tam gözükmüyor)
                // — sabit 4:3 aspectRatio + Crop KALDIRILDI (görselin GERÇEK
                // oranıyla uyuşmadığında kenarlarından kırpıyordu). ContentScale.Fit
                // + heightIn(max) ile görsel KENDİ oranında, en fazla 400dp
                // yükseklikte TAM gösterilir (Instagram'ın "tam görsel" davranışı).
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(top = 12.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            // Aksiyon satırı yeniden düzenlendi (kullanıcı isteği): Instagram
            // tarzı düzen — Beğen/Yorum/Yeniden-Paylaş/Gönder SATIRDA görünür,
            // Kaydet Spacer(weight(1f)) ile SAĞA yaslı AYRI bir ikon. Eskiden
            // hepsi ⋮ (PostActionsSheet) menüsünün ARKASINDAydı; artık sheet'te
            // SADECE Bildir/Sessize-Al kalıyor (bkz. PostActionsSheet.kt).
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
                        .clickable {
                            // Beğeni kalbi spring-pop: her tıklamada 1.0 → 1.3 → 1.0
                            // (kalıcı state DEĞİL, sadece dokunsal geri bildirim —
                            // gerçek beğeni durumu her zaman onLikeClick/post.likedByMe'den
                            // gelir, bu animasyon MANTIĞI etkilemez).
                            animScope.launch {
                                likeScale.snapTo(1f)
                                likeScale.animateTo(
                                    1.3f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                                likeScale.animateTo(
                                    1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                            }
                            onLikeClick(post)
                        }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = if (post.likedByMe) "Beğenmekten vazgeç" else "Beğen",
                        tint = if (post.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(likeScale.value),
                    )
                    Text(
                        text = " ${post.likeCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                val commentInteractionSource = remember { MutableInteractionSource() }
                val commentPressed by commentInteractionSource.collectIsPressedAsState()
                val commentScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (commentPressed) 0.85f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "commentIconScale",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = commentInteractionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                        ) { onCommentClick(post) }
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Yorum",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(commentScale),
                    )
                    Text(
                        text = " ${post.commentCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                // Yeniden Paylaş — PostActionsSheet'teki "Yeniden Paylaş"ın AKSİNE
                // onay diyaloğu YOK, tek-tık hızlı repost (backend content'siz
                // repost'u zaten destekliyor, alıntılı repost UI'ı kapsam dışı).
                // Basılı tutulduğunda hafif küçülme (0.85x) — IconButton'ın
                // KENDİ interactionSource'unu okuyarak, tıklama mantığı
                // (onClick) DEĞİŞMEDEN sadece görsel dokunsal geri bildirim.
                val repostInteractionSource = remember { MutableInteractionSource() }
                val repostPressed by repostInteractionSource.collectIsPressedAsState()
                val repostScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (repostPressed) 0.85f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    label = "repostIconScale",
                )
                IconButton(onClick = { onRepost(post.id) }, interactionSource = repostInteractionSource) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = "Yeniden paylaş",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(repostScale),
                    )
                }
                // Gönder — eskiden PostActionsSheet'in İÇİNDEYDİ, şimdi PostCard
                // kendi showShareSheet state'iyle DOĞRUDAN PostShareSheet'i açıyor.
                IconButton(onClick = { showShareSheet = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // Kaydet — Instagram'daki bookmark ikonunun konumuyla AYNI: satırın
                // EN SAĞINDA, diğer aksiyonlardan Spacer'la ayrılmış.
                // Madde 2: IconButton (uzun-basmayı DESTEKLEMEZ) yerine
                // combinedClickable'lı bir Box — KISA dokunuş MEVCUT davranışı
                // (onBookmark, Genel'e toggle) AYNEN korur, UZUN basma
                // koleksiyon seçiciyi açar. Kısa dokunuşta da beğeni kalbiyle
                // AYNI spring-pop (1.0 → 1.2 → 1.0, biraz daha hafif).
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                animScope.launch {
                                    bookmarkScale.snapTo(1f)
                                    bookmarkScale.animateTo(
                                        1.2f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                    bookmarkScale.animateTo(
                                        1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                }
                                onBookmark(post.id)
                            },
                            onLongClick = { showCollectionPicker = true },
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Kaydedildi" else "Kaydet",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(bookmarkScale.value),
                    )
                }
            }
        }
    }

    if (showActionsSheet) {
        PostActionsSheet(
            post = post,
            onMutePost = onMutePost,
            onReport = onReport,
            onDismiss = { showActionsSheet = false },
            isOwn = isOwnPost,
            onEdit = { showEditDialog = true },
            onDelete = onDeletePost,
            onToggleArchive = onArchivePost,
            onTogglePin = onPinPost,
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Postu düzenle") },
            text = {
                // SADECE metin — görsel/video değiştirme kapsam dışı (web'in
                // post_edit.html'iyle AYNI kısıt, bkz. app/routes/posts.py
                // edit_post() docstring'i).
                OutlinedTextField(
                    value = editContent,
                    onValueChange = { editContent = it },
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        onEditPost(post.id, editContent)
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Vazgeç") }
            },
        )
    }

    if (showShareSheet) {
        PostShareSheet(
            postId = post.id,
            onDismiss = { showShareSheet = false },
            onSessionExpired = onSessionExpired,
        )
    }

    if (showCollectionPicker) {
        BookmarkCollectionPickerSheet(
            postId = post.id,
            alreadyBookmarked = isBookmarked,
            onDismiss = { showCollectionPicker = false },
            onSessionExpired = onSessionExpired,
            onBookmarked = { bookmarkedOverride = it },
        )
    }
}

/**
 * Madde 5 (kullanıcı raporu: "gönderilen postlar çok kötü görünüyor" — kök
 * neden repostOf'un domain modele hiç taşınmaması, bkz. Post.kt yorumu) —
 * repost atılan gönderinin orijinalini KÜÇÜK bir gömülü kart içinde gösterir.
 * Web'in `.repost-embed`'inden (app/static/css/style.css ~3632-3690) ilham
 * alındı ama native Compose deyimiyle basit tutuldu: kenarlıklı/yuvarlak
 * köşeli Column içinde yazar (avatar+username) + içerik + (varsa) görsel.
 */
@Composable
private fun RepostEmbedCard(repost: RepostEmbed, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!repost.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = repost.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = repost.username ?: "Bilinmeyen kullanıcı",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (!repost.content.isNullOrBlank()) {
            Text(
                text = repost.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!repost.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = repost.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(top = 8.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
    }
}
