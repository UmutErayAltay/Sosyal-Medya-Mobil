package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.SuggestedUserDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryBarEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryBarViewModel
import kotlinx.coroutines.delay

/**
 * Animasyon turu (2026-08-08, UI güzelleştirme çalışması 3. kısım) — liste
 * item'larının stagger'lı fade+slide-up girişi. FeedScreen/DiscoverScreen/
 * HashtagScreen/TrendingScreen'in LazyColumn item'larında PAYLAŞILAN
 * (`internal` görünürlük, AYNI paket — Kotlin'de top-level `private`
 * fonksiyonlar dosya sınırını aşamaz, bu yüzden AYRI AYRI KOPYALANMADI).
 *
 * 2026-08-08 (performans düzeltmesi — kullanıcı raporu "kasıyor, yükleme
 * çok yavaşladı"): İlk sürüm "composition canlı tutulduğu sürece tekrar
 * tetiklenmez" varsayıyordu — bu YANLIŞTI, LazyColumn görünür pencerenin
 * dışına kayan item'ları GERÇEKTEN disposed eder, geri kaydırınca fresh bir
 * composition kurulur ve animasyon HER seferinde yeniden oynardı (sürekli
 * kaydırmada gözle görülür "kasıma"). Artık çağıran ekran, kendi
 * `remember { mutableSetOf<String>() }` ile ekranın YAŞAM SÜRESİ boyunca
 * (composition dispose/recompose'dan ETKİLENMEYEN bir üst seviyede) hangi
 * key'lerin daha önce animasyonla girdiğini tutuyor — `seenKeys.add(key)`
 * SADECE gerçekten YENİ bir key'de `true` döner, aynı key ikinci kez
 * (scroll-geri-geliş) görüldüğünde animasyon TAMAMEN ATLANIR (AnimatedVisibility
 * bile mount edilmez). `index` en fazla 8 adıma SINIRLANIR (60ms adım = en
 * fazla 480ms).
 */
@Composable
internal fun PostFeedStaggerReveal(
    index: Int,
    itemKey: String,
    seenKeys: MutableSet<String>,
    content: @Composable () -> Unit,
) {
    val isNew = remember(itemKey) { seenKeys.add(itemKey) }
    if (!isNew) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    // 2026-08-09 (kullanıcı raporu: "posta girince kaydırma var fakat akışta
    // gezerken görsel kaydırma özelliği yok" — PostCard'daki HorizontalPager
    // carousel post detayında çalışıyor ama feed'de çalışmıyordu): kök neden
    // AnimatedVisibility'nin visible=true olduktan SONRA da SONSUZA KADAR
    // mount'lu kalması (exit dalı hiç tetiklenmiyor, çünkü `visible` bir daha
    // false'a dönmüyor) — yani her post'un HorizontalPager'ı ile LazyColumn
    // arasında KALICI bir AnimatedVisibility katmanı vardı. PostDetailScreen
    // bu wrapper'ı HİÇ kullanmıyor, bu yüzden orada carousel'in yatay
    // sürüklemesi LazyColumn'un dikey sürüklemesiyle çakışmadan çalışıyordu.
    // Giriş animasyonu bittikten SONRA `content()` DOĞRUDAN çağrılıyor,
    // AnimatedVisibility SÖKÜLÜYOR — `isNew == false` erken-dönüşüyle AYNI
    // mantık, sadece animasyon bir kere oynadıktan SONRA gerçekleşiyor.
    var animationDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val delayMs = index.coerceIn(0, 8) * 60L
        if (delayMs > 0) delay(delayMs)
        visible = true
        delay(220L)
        animationDone = true
    }
    if (animationDone) {
        content()
        return
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 220),
                initialOffsetY = { fullHeight -> fullHeight / 8 },
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onSessionExpired: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNewPostClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onTrendingClick: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    // 2026-08-09 (kullanıcı isteği: "kullanıcı isimlerine tıklayınca
    // profillere yönlendirsin") — VARSAYILAN DEĞERLİ, DiscoverScreen'in
    // onUserClick'iyle AYNI amaç.
    onNavigateToProfile: (String) -> Unit = {},
    // Faz 5 Dalga 2C: hikaye çubuğu — StoryBarViewModel FeedViewModel'e
    // KARIŞTIRILMADI (görev tanımı), ayrı `viewModel()` instance'ı.
    // 2026-08-10: StoryBar.onStoryClick ile AYNI (userId, allUserIds) imzası
    // — "birinin storyleri bitince sıradakine geçsin".
    onNavigateToStoryViewer: (String, List<String>) -> Unit = { _, _ -> },
    onNavigateToStoryCreate: () -> Unit = {},
    // Kullanıcı raporu: hikaye paylaşıldıktan sonra bu çubuk yenilenmiyordu -
    // StoryBarViewModel sekme değişiminde KORUNDUĞU için sadece bir kez
    // `init { loadBar() }` çalışıyordu. MainScaffold, NavController'ın
    // savedStateHandle'ından okuduğu "story_created" bayrağını buraya iletir;
    // true olunca çubuk yeniden yüklenir, sonra bayrak MainScaffold'da false'a
    // döndürülür (bkz. MainScaffold.kt).
    storyCreated: Boolean = false,
    onStoryBarRefreshHandled: () -> Unit = {},
    // 2026-08-09 (kullanıcı isteği: "post paylaşınca sayfayı yenilememe
    // gerek kalmadan ana sayfada da görmek istiyorum") — storyCreated ile
    // BİREBİR AYNI desen (savedStateHandle üzerinden AppNavHost.kt'nin
    // "createPost" route'undan gelir, bkz. MainScaffold.kt).
    postCreated: Boolean = false,
    onPostCreatedRefreshHandled: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
    storyBarViewModel: StoryBarViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()
    val storyBarItems by storyBarViewModel.items.collectAsState()
    // Madde 1 (sonsuz kaydırma) + madde 2 (önerilen kullanıcılar).
    val hasMore by viewModel.hasMore.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val suggestedUsers by viewModel.suggestedUsers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val context = LocalContext.current
    // Performans düzeltmesi (bkz. PostFeedStaggerReveal yorumu) — bu ekranın
    // yaşam süresi boyunca hangi post'ların zaten giriş animasyonuyla
    // gösterildiğini tutar, scroll-geri-gelişte TEKRAR animasyon oynamasın.
    val seenPostKeys = remember { mutableSetOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedEvent.SessionExpired -> onSessionExpired()
                is FeedEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        storyBarViewModel.events.collect { event ->
            when (event) {
                is StoryBarEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    // storyCreated true olunca çubuğu yeniden yükle, sonra bayrağı MainScaffold'da
    // false'a döndür - aksi halde her recomposition'da tekrar tetiklenir.
    LaunchedEffect(storyCreated) {
        if (storyCreated) {
            storyBarViewModel.loadBar()
            onStoryBarRefreshHandled()
        }
    }

    // postCreated ile AYNI desen — feed'i yeniden yükle, sonra bayrağı
    // MainScaffold'da false'a döndür.
    LaunchedEffect(postCreated) {
        if (postCreated) {
            viewModel.refresh()
            onPostCreatedRefreshHandled()
        }
    }

    // 3. deneme sonrası KESİN çözüm: nested-scroll tabanlı
    // enterAlwaysScrollBehavior() (PullToRefreshBox ile etkileşimde bar'ı TAM
    // gizletemiyordu, üstte kararmış bir şerit bırakıyordu) TAMAMEN TERK
    // EDİLDİ. Bunun yerine LazyListState'in scroll POZİSYONUNA bakan elle
    // yön tespiti (bkz. HideableTopBar.kt) — nested-scroll paylaşımı hiç
    // olmadığı için PullToRefreshBox'la çakışma ihtimali de ortadan kalkar.
    val listState = rememberLazyListState()
    val isTopBarVisible by rememberTopBarVisibility(listState)
    // Madde 1 (navbar üst-binme fix, bkz. HideableTopBar.kt) — SADECE
    // TOP_BAR_HEIGHT yerine status bar inset'ini de içeren gerçek yükseklik.
    val topBarContentPadding = rememberTopBarContentPadding()

    // Madde 1 (sonsuz kaydırma) — DiscoverScreen.kt'deki AYNI desen: son
    // görünür item listenin sonuna (son 3 item) yaklaşınca sonraki sayfa
    // istenir. loadMore() zaten hasMore/loadingMore guard'lı, tekrar tekrar
    // tetiklenmesi zararsız (early-return).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && lastVisible >= total - 3) {
                viewModel.loadMore()
            }
        }
    }

    // Madde 6 (top bar mimarisi rewrite): Scaffold'un topBar slotu TAMAMEN
    // KALDIRILDI — bar artık içeriğin ÜSTÜNE binen bir Box overlay katmanı
    // (OverlayTopBar, bkz. HideableTopBar.kt), aşağıdaki LazyColumn'un
    // contentPadding'i TOP_BAR_HEIGHT kadar sabit boşluk bırakır. Bu sayede
    // bar'ın görünürlük değişimi (yukarı/aşağı kaydırma) içerik alanının
    // YENİDEN ÖLÇÜLMESİNE (reflow) yol AÇMAZ — web'in `position: fixed`
    // navbar'ıyla AYNI davranış, kullanıcı raporundaki "kararma"/"ışınlanma"
    // hissinin kök nedeni buydu.
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                posts.isEmpty() && uiState is FeedUiState.Loading -> FullScreenMessage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = "Akış yükleniyor…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                posts.isEmpty() && uiState is FeedUiState.Error -> FullScreenMessage {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = (uiState as FeedUiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Tekrar dene")
                        }
                    }
                }
                posts.isEmpty() -> FullScreenMessage {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text = "Henüz gönderi yok",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "Takip ettiklerin paylaşım yaptığında burada görünecek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Madde 6: üst boşluk artık Scaffold'un topBar slotundan DEĞİL,
                    // burada TOP_BAR_HEIGHT ile SABİT ayrılıyor (bkz. yukarıdaki Box
                    // yorumu) — bar görünürlüğü değişse bile bu değer HİÇ değişmez.
                    contentPadding = PaddingValues(top = topBarContentPadding + 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Faz 5 Dalga 2C: hikaye çubuğu — post listesinden ÖNCE,
                    // web'in feed sayfasındaki AYNI konum (bkz. görev tanımı:
                    // "LazyColumn'un EN ÜSTÜNE, post listesinden önce").
                    item {
                        StoryBar(
                            items = storyBarItems,
                            onAddStoryClick = onNavigateToStoryCreate,
                            onStoryClick = onNavigateToStoryViewer,
                        )
                    }
                    // Madde 2 (önerilen kullanıcılar): web'in "5. post civarı" deseniyle
                    // AYNI konum — index 4 (5. post), o post'un HEMEN ALTINA tek seferlik
                    // bir yatay öneri şeridi eklenir. itemsIndexed kullanıldı (posts.size
                    // <= 5 olduğunda hiç tetiklenmez, bu KABUL EDİLEBİLİR bir sınır).
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        PostFeedStaggerReveal(index = index, itemKey = post.id, seenKeys = seenPostKeys) {
                        Column {
                            PostCard(
                                post = post,
                                onLikeClick = { viewModel.toggleLike(it.id) },
                                onCommentClick = { onNavigateToPostDetail(it.id) },
                                onHashtagClick = onNavigateToHashtag,
                                onPollVote = { postId, optionId -> viewModel.votePoll(postId, optionId) },
                                onMutePost = { postId -> viewModel.toggleMutePost(postId) },
                                onBookmark = { postId -> viewModel.toggleBookmark(postId) },
                                onRepost = { postId -> viewModel.repost(postId) },
                                onReport = { postId -> viewModel.report(postId) },
                                onSessionExpired = onSessionExpired,
                                isOwnPost = post.userId == currentUserId,
                                onEditPost = { postId, content -> viewModel.editPost(postId, content) },
                                onDeletePost = { postId -> viewModel.deletePost(postId) },
                                onArchivePost = { postId -> viewModel.toggleArchive(postId) },
                                onPinPost = { postId -> viewModel.togglePin(postId) },
                                onUsernameClick = onNavigateToProfile,
                            )
                            if (index == 4 && suggestedUsers.isNotEmpty()) {
                                SuggestedUsersRow(
                                    users = suggestedUsers,
                                    onFollowClick = { user -> viewModel.toggleSuggestedFollow(user) },
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                        }
                    }
                    // Madde 1: sonsuz kaydırma yükleme göstergesi — listenin SONUNDA,
                    // DiscoverScreen'in "Daha fazla yükle" butonunun KARŞILIĞI ama
                    // burada buton YOK (otomatik tetiklenir), sadece bir dönen gösterge.
                    if (loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
                        }
                    }
                }
            }
        }

        OverlayTopBar(
            visible = isTopBarVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            // Kullanıcı raporu (5. tur, navbar renk uyuşmazlığı/boyu) — kök
            // neden ve TopBarSurface/windowInsets=0/height(TOP_BAR_HEIGHT)
            // üçlüsünün NEDEN gerektiği HideableTopBar.kt'de detaylı.
            TopBarSurface {
                TopAppBar(
                    title = { Text("Ana Sayfa") },
                    actions = {
                        // InboxScreen'in ikon-butonları DESENİYLE tutarlı — yeni bir
                        // ikonun YANINA eklendi, var olanın yerine geçmedi.
                        IconButton(onClick = onNotificationsClick) {
                            Box {
                                Icon(Icons.Filled.Notifications, contentDescription = "Bildirimler")
                                if (unreadNotificationsCount > 0) {
                                    // ConversationRow/InboxScreen'in unread nokta rozeti
                                    // deseniyle tutarlı - basit bir kırmızı/primary nokta.
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
                                    )
                                }
                            }
                        }
                        // Gündem (Faz 4 sonrası eksik giderme SONUNCUSU) - bildirim
                        // zilinin YANINA, var olan ikonların yerine geçmeden eklendi.
                        IconButton(onClick = onTrendingClick) {
                            Icon(Icons.Filled.Tag, contentDescription = "Gündem")
                        }
                        IconButton(onClick = onNewPostClick) {
                            Icon(Icons.Filled.Add, contentDescription = "Yeni Gönderi")
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.height(TOP_BAR_HEIGHT),
                )
            }
        }
    }
}

@Composable
private fun FullScreenMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Madde 2 (önerilen kullanıcılar) — yatay kaydırılabilir öneri şeridi.
 * "Takip Et" aksiyonu FeedViewModel.toggleSuggestedFollow() üzerinden
 * ProfileRepository.toggleFollow() reuse eder (YENİ bir repository İCAT
 * EDİLMEDİ, bkz. FeedViewModel yorumu). */
@Composable
private fun SuggestedUsersRow(
    users: List<SuggestedUserDto>,
    onFollowClick: (SuggestedUserDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Önerilen kullanıcılar",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(users, key = { it.id }) { user ->
                SuggestedUserCard(user = user, onFollowClick = { onFollowClick(user) })
            }
        }
    }
}

@Composable
private fun SuggestedUserCard(user: SuggestedUserDto, onFollowClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!user.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
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
        Text(
            text = user.username ?: "bilinmeyen",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onFollowClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Takip Et", style = MaterialTheme.typography.labelSmall)
        }
    }
}
