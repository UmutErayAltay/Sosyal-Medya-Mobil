package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileStatsDto
import com.umuterayaltay.sosyal.nativeapp.repository.Highlight
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModelFactory

private enum class ProfileTab(val label: String) {
    Posts("Gönderiler"),
    Media("Medya"),
    Liked("Beğenilenler"),
    Saved("Kaydedilenler"),
    Archived("Arşiv"),
}

/**
 * Profil ekrani - kendi profilim (username=null, alt navigasyondaki "Profil"
 * sekmesi, onNavigateBack=null -> geri oku YOK) veya baskasinin profili
 * (username dolu, "profile/{username}" push route'u, onNavigateBack dolu ->
 * TopAppBar'da geri oku) icin AYNI composable kullanilir.
 *
 * NOT (spesifikasyon sapmasi): onNavigateToProfile parametresi imzada
 * TUTULDU ama bu ekranin govdesinde HICBIR YERDEN cagrilmiyor - kapsam disi
 * birakilan "post'a tiklama" (bkz. spesifikasyon: "Bir post'a tiklaninca
 * simdilik hicbir sey olmasin") yuzunden postun yazarina tiklanabilir bir
 * satir yok; ileride post-yazarina tiklama eklenince kullanilmasi icin hazir
 * birakildi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String?,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToFollowers: (String) -> Unit,
    onNavigateToFollowing: (String) -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToFollowRequests: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    // Faz 5 Dalga 4B — HighlightsScreen (Dalga 2C'de zaten hazırdı) ZATEN
    // "highlights/{userId}" route'unda duruyordu, SADECE ProfileScreen'den
    // bağlanmıyordu (bkz. AppNavHost.kt eski yorumu). Varsayılan {} ile mevcut
    // çağrı yerleri (ör. testler) kırılmadan derlenmeye devam eder.
    onNavigateToHighlights: (String) -> Unit = {},
    onSessionExpired: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(username)),
) {
    val profile by viewModel.profile.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val likedPosts by viewModel.likedPosts.collectAsState()
    val bookmarkedPosts by viewModel.bookmarkedPosts.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val archivedPosts by viewModel.archivedPosts.collectAsState()
    val isSelf by viewModel.isSelf.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isPendingRequest by viewModel.isPendingRequest.collectAsState()
    val isPrivate by viewModel.isPrivate.collectAsState()
    val isBlockedByMe by viewModel.isBlockedByMe.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isDeactivated by viewModel.isDeactivated.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showBlockMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.SessionExpired -> onSessionExpired()
                is ProfileEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // FeedScreen.kt'deki AYNI karar (bkz. HideableTopBar.kt) — nested-scroll
    // tabanlı enterAlwaysScrollBehavior() TERK EDİLDİ, listState ProfileContent'e
    // aktarılıp asıl LazyColumn'a (ProfileContent içinde) bağlanır, görünürlük
    // burada (Scaffold/TopAppBar seviyesinde) hesaplanır. SADECE "Profil"
    // bottom-nav sekmesi ve push edilen "profile/{username}" ikisi de bu TEK
    // composable'ı paylaştığı için tek yerde eklemek yetiyor.
    val listState = rememberLazyListState()
    val isTopBarVisible by rememberTopBarVisibility(listState)

    // Madde 6 (top bar mimarisi rewrite): FeedScreen.kt'deki AYNI karar -
    // Scaffold'un topBar slotu TAMAMEN KALDIRILDI, bar artık bir Box overlay
    // katmanı (OverlayTopBar), içerik TOP_BAR_HEIGHT kadar SABİT üst boşluk
    // bırakır (bar'ın görünürlüğü bu boşluğu ETKİLEMEZ).
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && profile == null -> FullScreenCenter { CircularProgressIndicator() }

            error != null && profile == null -> FullScreenCenter {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = error ?: "",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Button(onClick = { viewModel.loadProfile() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }

            profile == null -> FullScreenCenter { Text("Profil bulunamadı.") }

            isDeactivated -> DeactivatedProfileContent(profile!!)

            else -> ProfileContent(
                listState = listState,
                profile = profile!!,
                stats = stats,
                isSelf = isSelf,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                isPrivate = isPrivate,
                isBlockedByMe = isBlockedByMe,
                posts = posts,
                likedPosts = likedPosts,
                bookmarkedPosts = bookmarkedPosts,
                archivedPosts = archivedPosts,
                highlights = highlights,
                onToggleFollow = viewModel::toggleFollow,
                onNavigateToFollowers = { profile?.username?.let(onNavigateToFollowers) },
                onNavigateToFollowing = { profile?.username?.let(onNavigateToFollowing) },
                // HighlightsScreen KENDİSİ zaten tam grid+görüntüleme deneyimi
                // (Dalga 2C'den hazır) — hangi bubble'a tıklanırsa tıklansın
                // AYNI "highlights/{userId}" listesine gidilir, o ekranda
                // kullanıcı istediği highlight'ı seçer.
                onHighlightClick = { profile?.id?.let(onNavigateToHighlights) },
                onLikeClick = { viewModel.toggleLike(it.id) },
                onCommentClick = { onNavigateToPostDetail(it.id) },
                onHashtagClick = onNavigateToHashtag,
                onPollVote = { postId, optionId -> viewModel.votePoll(postId, optionId) },
                onMutePost = { postId -> viewModel.toggleMutePost(postId) },
                onBookmarkPost = { postId -> viewModel.toggleBookmark(postId) },
                onRepost = { postId -> viewModel.repost(postId) },
                onReport = { postId -> viewModel.report(postId) },
                onSessionExpired = onSessionExpired,
            )
        }

        OverlayTopBar(
            visible = isTopBarVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            TopAppBar(
                title = { Text(profile?.username ?: "Profil") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                },
                actions = {
                    if (isSelf && !isDeactivated) {
                        IconButton(onClick = onNavigateToFollowRequests) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Takip İstekleri")
                        }
                        IconButton(onClick = onNavigateToInsights) {
                            Icon(Icons.Filled.BarChart, contentDescription = "İstatistikler")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Ayarlar")
                        }
                    } else if (!isSelf && !isDeactivated) {
                        // Başkasının profili: engelle/engeli kaldır aksiyonu üç-nokta
                        // overflow menüde - "Takip Et" butonuyla aynı hizada ayrı bir
                        // buton yerine, yanlışlıkla dokunmayı zorlaştıran bilinçli tercih.
                        IconButton(onClick = { showBlockMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Diğer seçenekler")
                        }
                        DropdownMenu(expanded = showBlockMenu, onDismissRequest = { showBlockMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isBlockedByMe) "Engeli Kaldır" else "Engelle") },
                                leadingIcon = { Icon(Icons.Filled.Block, contentDescription = null) },
                                onClick = {
                                    showBlockMenu = false
                                    if (isBlockedByMe) {
                                        // Engeli kaldırma geri dönüşü kolay bir aksiyon -
                                        // ekstra onay diyaloğu istenmedi (CloseFriendsScreen'in
                                        // "Kaldır" ikonuyla AYNI düşük-sürtünme yaklaşımı).
                                        viewModel.toggleBlock()
                                    } else {
                                        showBlockConfirmDialog = true
                                    }
                                },
                            )
                            // Faz 5 Dalga 2A: AYNI overflow menüde ikinci bir aksiyon —
                            // ayrı bir buton İCAT EDİLMEDİ. Engelleme'nin AKSİNE onay
                            // diyaloğu yok (geri dönüşü kolay, düşük riskli bir aksiyon).
                            DropdownMenuItem(
                                text = { Text(if (isMuted) "Sesi Aç" else "Sessize Al") },
                                leadingIcon = {
                                    Icon(
                                        if (isMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showBlockMenu = false
                                    viewModel.toggleUserMute()
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    if (showBlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBlockConfirmDialog = false },
            icon = { Icon(Icons.Filled.Block, contentDescription = null) },
            title = { Text("Kullanıcıyı engelle") },
            text = {
                Text(
                    "${profile?.username?.let { "$it kullanıcısını" } ?: "Bu kullanıcıyı"} " +
                        "engellemek istediğine emin misin? Engellenince birbirinizin " +
                        "gönderilerini göremezsiniz ve varsa takip ilişkiniz kopar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirmDialog = false
                        viewModel.toggleBlock()
                    },
                ) {
                    Text("Engelle", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirmDialog = false }) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

// Madde 6: padding parametresi Scaffold ile birlikte KALDIRILDI - artık
// TOP_BAR_HEIGHT ile SABİT bir üst boşluk bırakılıyor (InboxScreen.kt'deki
// CenteredMessage ile AYNI karar).
@Composable
private fun FullScreenCenter(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_HEIGHT)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DeactivatedProfileContent(profile: ProfileDto) {
    FullScreenCenter {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(profile.avatarUrl, size = 80.dp)
            Text(
                text = profile.username ?: "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Bu hesap deaktif",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Gorsel cila: sabit boyutlu daire icinde her zaman surfaceVariant zemin +
 * ince outline halkasi - hem gercek avatar hem placeholder ikon icin AYNI
 * cerceve, boylece profil resmi olmayan kullanicilarda da duzenli/tutarli
 * bir daire gorunur (onceki halde placeholder ikon serbest boyuttaydi).
 */
@Composable
private fun ProfileAvatar(avatarUrl: String?, size: Dp = 64.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    listState: LazyListState,
    profile: ProfileDto,
    stats: ProfileStatsDto?,
    isSelf: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    isPrivate: Boolean,
    isBlockedByMe: Boolean,
    posts: List<Post>,
    likedPosts: List<Post>,
    bookmarkedPosts: List<Post>,
    archivedPosts: List<Post>,
    highlights: List<Highlight>,
    onToggleFollow: () -> Unit,
    onNavigateToFollowers: () -> Unit,
    onNavigateToFollowing: () -> Unit,
    onHighlightClick: () -> Unit,
    onLikeClick: (Post) -> Unit,
    onCommentClick: (Post) -> Unit,
    onHashtagClick: (String) -> Unit,
    onPollVote: (String, String) -> Unit,
    onMutePost: (String) -> Unit,
    onBookmarkPost: (String) -> Unit,
    onRepost: (String) -> Unit,
    onReport: (String) -> Unit,
    // PostCard'ın kendi PostShareSheet'i için — bkz. PostCard.kt yorumu.
    onSessionExpired: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ProfileTab.Posts) }
    val hidden = isPrivate && !isSelf && !isFollowing

    // Kaydedilenler Archived ile AYNI gerekçeyle SADECE kendi profilimizde
    // gösterilir — kişisel bir liste (bkz. app/social.py bookmarks yorumu),
    // başkasının profilinde backend zaten boş dönüyor ama sekmeyi hiç
    // göstermemek daha nettir.
    val tabs = remember(isSelf) {
        if (isSelf) {
            ProfileTab.entries.toList()
        } else {
            ProfileTab.entries.filterNot { it == ProfileTab.Archived || it == ProfileTab.Saved }
        }
    }

    // Medya sekmesi client-side filtrelenir - PostDto.imageUrls (coklu gorsel)
    // repository.Post domain modeline TASINMADI (sadece imageUrl var), bu
    // yuzden burada spesifikasyondaki "imageUrl != null || !imageUrls.isNullOrEmpty()"
    // kontrolunun SADECE imageUrl yarisi uygulanabildi (bkz. rapor sapmasi).
    val mediaPosts = remember(posts) { posts.filter { !it.imageUrl.isNullOrBlank() } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Madde 6: üst boşluk artık Scaffold'un DEĞİŞKEN padding'inden DEĞİL,
        // TOP_BAR_HEIGHT ile SABİT ayrılıyor (bkz. yukarıdaki ProfileScreen
        // Box yorumu).
        contentPadding = PaddingValues(top = TOP_BAR_HEIGHT, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ProfileHeader(
                profile = profile,
                stats = stats,
                highlights = highlights,
                onHighlightClick = onHighlightClick,
                isSelf = isSelf,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                isBlockedByMe = isBlockedByMe,
                onToggleFollow = onToggleFollow,
                onNavigateToFollowers = onNavigateToFollowers,
                onNavigateToFollowing = onNavigateToFollowing,
            )
        }

        if (hidden) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "Bu hesap gizli — gönderileri görmek için takip etmelisin",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        } else {
            item {
                val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
                // Madde 7 (kullanıcı raporu: "Gönderiler, Medya, Beğenilenler,
                // Kaydedilenler, Arşiv" sekmeleri taşıyor/sıkışıyor) - sabit
                // genişlikli TabRow YERİNE ScrollableTabRow: 5 uzun Türkçe etiket
                // artık sıkışıp satır kaymasına yol açmadan yatay kaydırılabilir.
                ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 16.dp) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }

            val currentPosts = when (selectedTab) {
                ProfileTab.Posts -> posts
                ProfileTab.Media -> mediaPosts
                ProfileTab.Liked -> likedPosts
                ProfileTab.Saved -> bookmarkedPosts
                ProfileTab.Archived -> archivedPosts
            }

            if (currentPosts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Burada henüz bir şey yok.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            } else {
                items(currentPosts, key = { "${selectedTab.name}_${it.id}" }) { post ->
                    PostCard(
                        post = post,
                        onLikeClick = onLikeClick,
                        onCommentClick = onCommentClick,
                        onHashtagClick = onHashtagClick,
                        onPollVote = onPollVote,
                        onMutePost = onMutePost,
                        onBookmark = onBookmarkPost,
                        onRepost = onRepost,
                        onReport = onReport,
                        onSessionExpired = onSessionExpired,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileDto,
    stats: ProfileStatsDto?,
    highlights: List<Highlight>,
    onHighlightClick: () -> Unit,
    isSelf: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    isBlockedByMe: Boolean,
    onToggleFollow: () -> Unit,
    onNavigateToFollowers: () -> Unit,
    onNavigateToFollowing: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile.avatarUrl, size = 88.dp)
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = profile.username ?: "bilinmeyen", style = MaterialTheme.typography.titleLarge)
                if (!profile.fullName.isNullOrBlank()) {
                    Text(
                        text = profile.fullName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // Faz 5 Dalga 4B — web'in profile.html .highlight-bar'ının AYNI konumu
        // (bio'dan SONRA, istatistik satırından ÖNCE). Liste boşsa hiç
        // gösterilmez (web'in `{% if highlights %}`'ıyla AYNI).
        if (highlights.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(highlights, key = { it.id }) { highlight ->
                    HighlightBubble(highlight = highlight, onClick = onHighlightClick)
                }
            }
        }

        if (stats != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatColumn(label = "Gönderi", value = stats.posts)
                StatColumn(label = "Takipçi", value = stats.followers, onClick = onNavigateToFollowers)
                StatColumn(label = "Takip", value = stats.following, onClick = onNavigateToFollowing)
                StatColumn(label = "Beğeni", value = stats.likes)
            }
        }

        if (!isSelf) {
            FollowActionButton(
                isBlockedByMe = isBlockedByMe,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                onToggleFollow = onToggleFollow,
            )
        }
    }
}

/** Bir highlight balonu — web'in `.highlight-item`/`.highlight-cover` görsel
 * dilinin AYNI native karşılığı (dairesel kapak + altında başlık). Kapak
 * yoksa [ProfileAvatar]'ın avatarsız-kullanıcı hâliyle AYNI nötr daire. */
@Composable
private fun HighlightBubble(highlight: Highlight, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!highlight.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = highlight.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            text = highlight.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun StatColumn(label: String, value: Int, onClick: (() -> Unit)? = null) {
    Column(
        modifier = (
            if (onClick != null) {
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onClick)
            } else {
                Modifier
            }
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "$value", style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Gorsel cila: 4 takip-durumu butonu artik AYNI yukseklik + oncu ikonla
 * tutarli (onceki halde sadece metin farkliydi, OutlinedButton/Button
 * ayrimi disinda gorsel bir hiyerarsi yoktu). Davranis/callback AYNI kaldi.
 */
@Composable
private fun FollowActionButton(
    isBlockedByMe: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    onToggleFollow: () -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp)
        .height(46.dp)

    when {
        isBlockedByMe -> OutlinedButton(onClick = {}, enabled = false, modifier = buttonModifier) {
            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Engellendi")
        }

        isFollowing -> OutlinedButton(onClick = onToggleFollow, modifier = buttonModifier) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Takip Ediliyor")
        }

        isPendingRequest -> OutlinedButton(onClick = onToggleFollow, modifier = buttonModifier) {
            Icon(Icons.Filled.HourglassEmpty, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("İstek Gönderildi")
        }

        else -> Button(onClick = onToggleFollow, modifier = buttonModifier) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Takip Et")
        }
    }
}
