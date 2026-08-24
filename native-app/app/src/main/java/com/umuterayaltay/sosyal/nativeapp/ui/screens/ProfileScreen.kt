package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileStatsDto
import com.umuterayaltay.sosyal.nativeapp.network.StickerDto
import com.umuterayaltay.sosyal.nativeapp.repository.CreateStickerResult
import com.umuterayaltay.sosyal.nativeapp.repository.DeleteStoryResult
import com.umuterayaltay.sosyal.nativeapp.repository.Highlight
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.SaveHighlightResult
import com.umuterayaltay.sosyal.nativeapp.repository.StickersResult
import com.umuterayaltay.sosyal.nativeapp.repository.Story
import com.umuterayaltay.sosyal.nativeapp.repository.StoryArchiveResult
import com.umuterayaltay.sosyal.nativeapp.ui.components.FullscreenImageViewer
import com.umuterayaltay.sosyal.nativeapp.ui.components.FullscreenVideoViewer
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.ProfileViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProfileTab(val label: String) {
    Posts("Gönderiler"),
    Media("Medya"),
    Liked("Beğenilenler"),
    Saved("Kaydedilenler"),
    // 2026-08-08 (kullanıcı raporu: "Çıkartmalarım kısmı da yok onu da
    // ekleyelim") — web'in profile.html'deki "Çıkartmalarım" tab'ının native
    // karşılığı. Backend (app/api_v1/stickers.py, TAM CRUD) ve native
    // StickersRepository ZATEN vardı, SADECE bu sekme eksikti.
    Stickers("Çıkartmalarım"),
    Archived("Arşiv"),
    // 2026-08-22 (kullanıcı isteği: "storyler 24 saat sonra siliniyor ama
    // paylaşan kişi arşivinde görsün") — Stickers ile AYNI özel-durum
    // deseni (generic post grid'i YERİNE ayrı bir composable), SADECE
    // isSelf'te (aşağıdaki tabs filtresi).
    StoryArchive("Hikaye Arşivim"),
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
    // 2026-08-22 (kullanıcı isteği: "başkasının profili boş görünüyor" — web'in
    // profile.html'deki "Mesaj gönder" butonunun karşılığı) — VARSAYILAN
    // DEĞERLİ, onNavigateToHighlights ile AYNI gerekçe (mevcut çağrı yerleri/
    // testler kırılmadan derlenir).
    onNavigateToConversation: (String) -> Unit = {},
    // Faz 5 Dalga 4B — HighlightsScreen (Dalga 2C'de zaten hazırdı) ZATEN
    // "highlights/{userId}" route'unda duruyordu, SADECE ProfileScreen'den
    // bağlanmıyordu (bkz. AppNavHost.kt eski yorumu). Varsayılan {} ile mevcut
    // çağrı yerleri (ör. testler) kırılmadan derlenmeye devam eder.
    onNavigateToHighlights: (String) -> Unit = {},
    // 2026-08-09 (kullanıcı raporu: "öne çıkarılanlara ekleyince uygulamayı
    // aç kapa yapmak zorunda kalıyorum") — storyCreated/postCreated ile AYNI
    // savedStateHandle deseni (bkz. FeedScreen.kt/MainScaffold.kt).
    highlightsChanged: Boolean = false,
    onHighlightsRefreshHandled: () -> Unit = {},
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
    val currentUserId by viewModel.currentUserId.collectAsState()
    val isSelf by viewModel.isSelf.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val isPendingRequest by viewModel.isPendingRequest.collectAsState()
    val isPrivate by viewModel.isPrivate.collectAsState()
    val isBlockedByMe by viewModel.isBlockedByMe.collectAsState()
    val isCloseFriend by viewModel.isCloseFriend.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isDeactivated by viewModel.isDeactivated.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showBlockMenu by remember { mutableStateOf(false) }
    var showBlockConfirmDialog by remember { mutableStateOf(false) }
    // 2026-08-22 (kullanıcı isteği: "takipten çıkmadan önce uyarı gelsin
    // onaylama olsun") — showBlockConfirmDialog ile AYNI desen.
    var showUnfollowConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileEvent.SessionExpired -> onSessionExpired()
                is ProfileEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is ProfileEvent.ConversationReady -> onNavigateToConversation(event.conversationId)
            }
        }
    }

    // FeedScreen.kt'deki storyCreated/postCreated ile AYNI desen — highlight
    // listesini yeniden yükle, sonra bayrağı çağıran tarafta (MainScaffold/
    // AppNavHost) false'a döndür.
    LaunchedEffect(highlightsChanged) {
        if (highlightsChanged) {
            viewModel.refreshHighlights()
            onHighlightsRefreshHandled()
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
    //
    // 2026-08-22 (kullanıcı raporu, ekran görüntüsüyle doğrulandı: "geri
    // butonu ve yanındaki isim bildirim panelinin/status bar'ın arkasında
    // kalıyor") — kök neden, [OverlayTopBar]'ın kendi `-statusBarHeight`
    // telafi offset'inin "bu ekranın Box'ı zaten bir Scaffold tarafından
    // status bar kadar aşağı itilmiş" VARSAYIMINA dayanması. Bu varsayım
    // SADECE alt-navigasyondaki "Profil" sekmesinde (MainScaffold'un
    // Scaffold'u status bar'ı ZATEN tüketiyor) doğru — ProfileScreen'in AYRI
    // bir kullanımı olan "profile/{username}" PUSH route'unda (AppNavHost'ta
    // HİÇBİR Scaffold'a SARILMADAN doğrudan composable) hiçbir şey status
    // bar'ı önceden tüketmiyor. İLK denemede bu Box'a `statusBarsPadding()`
    // eklemek YETMEDİ (kullanıcı GERÇEK cihazda test etti, sorun AYNEN
    // devam ediyordu) — o yaklaşım yerine telafiyi [OverlayTopBar]'ın KENDİ
    // `hasAncestorStatusBarInset` parametresiyle KOŞULLU hale getirmek
    // (aşağıdaki çağrı yerinde) daha güvenilir çıktı, bkz. o composable'ın
    // güncellenmiş yorumu.
    //
    // 2026-08-22 (kullanıcı raporu, 2. tur: "profil resmi o üst kısmın altında
    // kalıyor") — SADECE bar'ın KENDİ konumunu düzeltmek yetmedi, içeriğin
    // (ProfileHeader/avatar dahil) üst boşluğu da AYNI koşula göre
    // hesaplanmalı (bkz. rememberTopBarContentPadding() güncellenmiş yorumu).
    // Bu değer aşağıda FullScreenCenter/DeactivatedProfileContent/ProfileContent
    // çağrılarının HEPSİNE aktarılır — tek bir yerde unutmak (ör. sadece
    // ProfileContent'e geçip DeactivatedProfileContent'i atlamak) AYNI bug'ı
    // O DAL için geri getirirdi.
    val hasAncestorStatusBarInset = onNavigateBack == null
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            loading && profile == null -> FullScreenCenter(hasAncestorStatusBarInset) { CircularProgressIndicator() }

            error != null && profile == null -> FullScreenCenter(hasAncestorStatusBarInset) {
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

            profile == null -> FullScreenCenter(hasAncestorStatusBarInset) { Text("Profil bulunamadı.") }

            isDeactivated -> DeactivatedProfileContent(profile!!, hasAncestorStatusBarInset)

            else -> ProfileContent(
                listState = listState,
                hasAncestorStatusBarInset = hasAncestorStatusBarInset,
                profile = profile!!,
                stats = stats,
                isSelf = isSelf,
                isFollowing = isFollowing,
                isPendingRequest = isPendingRequest,
                isPrivate = isPrivate,
                isBlockedByMe = isBlockedByMe,
                isCloseFriend = isCloseFriend,
                posts = posts,
                likedPosts = likedPosts,
                bookmarkedPosts = bookmarkedPosts,
                archivedPosts = archivedPosts,
                highlights = highlights,
                onToggleFollow = viewModel::toggleFollow,
                onRequestUnfollow = { showUnfollowConfirmDialog = true },
                onToggleCloseFriend = viewModel::toggleCloseFriend,
                onMessageClick = viewModel::startConversation,
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
                currentUserId = currentUserId,
                onEditPost = { postId, content -> viewModel.editPost(postId, content) },
                onDeletePost = { postId -> viewModel.deletePost(postId) },
                onArchivePost = { postId -> viewModel.toggleArchive(postId) },
                onPinPost = { postId -> viewModel.togglePin(postId) },
            )
        }

        OverlayTopBar(
            visible = isTopBarVisible,
            // onNavigateBack != null == "profile/{username}" push route (bkz.
            // yukarıdaki Box yorumu) — SADECE o durumda ata Scaffold YOK,
            // telafi devre dışı bırakılıyor.
            hasAncestorStatusBarInset = onNavigateBack == null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            // Kullanıcı raporu (5. tur, navbar renk uyuşmazlığı/boyu) — kök
            // neden ve TopBarSurface/windowInsets=0/height(TOP_BAR_HEIGHT)
            // üçlüsünün NEDEN gerektiği HideableTopBar.kt'de detaylı.
            TopBarSurface {
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
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.height(TOP_BAR_HEIGHT),
            )
            }
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

    if (showUnfollowConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnfollowConfirmDialog = false },
            icon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
            title = { Text("Takipten çık") },
            text = {
                Text(
                    "${profile?.username?.let { "$it kullanıcısını" } ?: "Bu kullanıcıyı"} " +
                        "takipten çıkmak istediğine emin misin?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfollowConfirmDialog = false
                        viewModel.toggleFollow()
                    },
                ) {
                    Text("Takipten Çık", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowConfirmDialog = false }) {
                    Text("Vazgeç")
                }
            },
        )
    }
}

// Madde 6: padding parametresi Scaffold ile birlikte KALDIRILDI - artık
// TOP_BAR_HEIGHT ile SABİT bir üst boşluk bırakılıyor (InboxScreen.kt'deki
// CenteredMessage ile AYNI karar).
// Madde 1 (navbar üst-binme fix): TOP_BAR_HEIGHT yerine status bar inset'ini
// de içeren rememberTopBarContentPadding() kullanılıyor (bkz. HideableTopBar.kt).
@Composable
private fun FullScreenCenter(hasAncestorStatusBarInset: Boolean = true, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = rememberTopBarContentPadding(hasAncestorStatusBarInset))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DeactivatedProfileContent(profile: ProfileDto, hasAncestorStatusBarInset: Boolean = true) {
    FullScreenCenter(hasAncestorStatusBarInset) {
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
    hasAncestorStatusBarInset: Boolean,
    profile: ProfileDto,
    stats: ProfileStatsDto?,
    isSelf: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    isPrivate: Boolean,
    isBlockedByMe: Boolean,
    isCloseFriend: Boolean,
    posts: List<Post>,
    likedPosts: List<Post>,
    bookmarkedPosts: List<Post>,
    archivedPosts: List<Post>,
    highlights: List<Highlight>,
    onToggleFollow: () -> Unit,
    onRequestUnfollow: () -> Unit,
    onToggleCloseFriend: () -> Unit,
    onMessageClick: () -> Unit,
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
    // Post yönetimi (düzenle/sil/arşivle/sabitle) — isSelf İLE KARIŞTIRILMASIN:
    // "Beğenilenler"/"Kaydedilenler" sekmeleri BAŞKALARININ postlarını da
    // gösterir, bu yüzden isOwnPost her post için AYRI currentUserId
    // karşılaştırmasıyla hesaplanır (bkz. ProfileViewModel.currentUserId yorumu).
    currentUserId: String?,
    onEditPost: (String, String) -> Unit,
    onDeletePost: (String) -> Unit,
    onArchivePost: (String) -> Unit,
    onPinPost: (String) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(ProfileTab.Posts) }
    val hidden = isPrivate && !isSelf && !isFollowing

    // Kaydedilenler/Çıkartmalarım Archived ile AYNI gerekçeyle SADECE kendi
    // profilimizde gösterilir — kişisel bir liste (bkz. app/social.py
    // bookmarks yorumu, web'in profile.html'deki AYNI {% if is_own %} kısıtı
    // stickers tab'ı için de geçerli), başkasının profilinde backend zaten
    // boş dönüyor ama sekmeyi hiç göstermemek daha nettir.
    val tabs = remember(isSelf) {
        if (isSelf) {
            ProfileTab.entries.toList()
        } else {
            ProfileTab.entries.filterNot {
                it == ProfileTab.Archived || it == ProfileTab.Saved || it == ProfileTab.Stickers ||
                    it == ProfileTab.StoryArchive
            }
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
        // Box yorumu). Madde 1 (navbar üst-binme fix): TOP_BAR_HEIGHT yerine
        // status bar inset'ini de içeren rememberTopBarContentPadding()
        // kullanılıyor (bkz. HideableTopBar.kt) — ProfileContent kendisi de
        // @Composable olduğu için parametre eklemeye gerek kalmadan DOĞRUDAN
        // çağrılabiliyor.
        contentPadding = PaddingValues(top = rememberTopBarContentPadding(hasAncestorStatusBarInset), bottom = 24.dp),
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
                isCloseFriend = isCloseFriend,
                onToggleFollow = onToggleFollow,
                onRequestUnfollow = onRequestUnfollow,
                onToggleCloseFriend = onToggleCloseFriend,
                onMessageClick = onMessageClick,
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
                // genişlikli TabRow YERİNE ScrollableTabRow: 5-6 uzun Türkçe
                // etiket (kendi profilimiz) artık sıkışıp satır kaymasına yol
                // açmadan yatay kaydırılabilir.
                //
                // 2026-08-22 (kullanıcı raporu: "gönderiler-medya-beğeniler
                // sola yatık görünüyor") — başkasının profilinde SADECE 3 kısa
                // sekme var (bkz. yukarıdaki tabs filtresi), bunlar ekran
                // genişliğini doldurmadığı için ScrollableTabRow'un sol
                // hizalı (edgePadding'den başlayan) davranışı sola YASLANMIŞ/
                // dengesiz görünüyordu. Az sayıda kısa sekmede sabit genişlikli
                // TabRow (eşit dağıtılmış, ortalı) kullanılır — SADECE tab
                // sayısı azken (≤4), 5-6 sekmeli kendi profilimizde ScrollableTabRow
                // AYNEN kalır.
                if (tabs.size <= 4) {
                    TabRow(selectedTabIndex = selectedIndex) {
                        tabs.forEach { tab ->
                            Tab(
                                selected = tab == selectedTab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                } else {
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
            }

            if (selectedTab == ProfileTab.Stickers) {
                // Post listesi mantığından TAMAMEN ayrı — Stickers bir Post
                // listesi DEĞİL, kendi network/state akışı olan bağımsız bir
                // grid (bkz. ProfileStickersContent, MediaPickerSheet'in
                // StickerTab'ıyla AYNI ServiceLocator-doğrudan deseni).
                item { ProfileStickersContent(isSelf = isSelf) }
                return@LazyColumn
            }

            if (selectedTab == ProfileTab.StoryArchive) {
                // Stickers ile AYNI özel-durum deseni — 24 saat sonra
                // silinmeyen (bkz. backend _cleanup_expired_stories() no-op
                // değişikliği), sahibinin kendi arşivinde gördüğü hikayeler.
                item { ProfileStoryArchiveContent() }
                return@LazyColumn
            }

            val currentPosts = when (selectedTab) {
                ProfileTab.Posts -> posts
                ProfileTab.Media -> mediaPosts
                ProfileTab.Liked -> likedPosts
                ProfileTab.Saved -> bookmarkedPosts
                ProfileTab.Archived -> archivedPosts
                ProfileTab.Stickers -> emptyList() // yukarıda erken dönüldü, buraya HİÇ ulaşılmaz
                ProfileTab.StoryArchive -> emptyList() // yukarıda erken dönüldü, buraya HİÇ ulaşılmaz
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
                itemsIndexed(currentPosts, key = { _, post -> "${selectedTab.name}_${post.id}" }) { _, post ->
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
                        isOwnPost = post.userId == currentUserId,
                        onEditPost = onEditPost,
                        onDeletePost = onDeletePost,
                        onArchivePost = onArchivePost,
                        onPinPost = onPinPost,
                    )
                }
            }
        }
    }
}

/**
 * "Çıkartmalarım" sekmesi içeriği (2026-08-08, kullanıcı raporu) — web'in
 * profile.html #panel-stickers'ının native karşılığı. MediaPickerSheet'in
 * StickerTab'ıyla AYNI desen (ViewModel'siz, ServiceLocator'daki
 * StickersRepository'yi DOĞRUDAN çağırır — bkz. o dosyanın gerekçesi), EK
 * olarak yükleme (+ buton, PhotoPicker) ve uzun-basarak kaldırma (SADECE
 * isSelf'te) eklendi. LazyVerticalGrid, ProfileContent'in KENDİ LazyColumn'u
 * İÇİNDE tek bir `item{}` olarak yaşadığı için `userScrollEnabled = false` +
 * sticker sayısına göre HESAPLANMIŞ sabit yükseklik kullanır (nested-scroll
 * yerine dış LazyColumn'un scroll'una bırakılır — MessageSearchScreen.kt'nin
 * `animateContentSize` deseniyle AYNI "iç içe lazy sınırı" gerekçesi).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileStickersContent(isSelf: Boolean) {
    val stickersRepository = remember { ServiceLocator.stickersRepository }
    var stickers by remember { mutableStateOf<List<StickerDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<StickerDto?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun reload() {
        loading = true
        when (val result = stickersRepository.getMyStickers()) {
            is StickersResult.Success -> {
                stickers = result.stickers
                errorMsg = null
            }
            is StickersResult.Error -> errorMsg = "Çıkartmalar yüklenemedi"
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                uploading = true
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                val mime = context.contentResolver.getType(uri)
                if (bytes != null) {
                    when (stickersRepository.createSticker(bytes, mime)) {
                        is CreateStickerResult.Success -> reload()
                        is CreateStickerResult.Error ->
                            errorMsg = "Çıkartma yüklenemedi, lütfen tekrar dene"
                    }
                }
                uploading = false
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Çıkartmalarım", style = MaterialTheme.typography.titleMedium)
            if (isSelf) {
                IconButton(
                    onClick = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !uploading,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Add, contentDescription = "Yeni çıkartma yükle")
                    }
                }
            }
        }

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            errorMsg != null -> Text(
                text = errorMsg ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            stickers.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isSelf) "Henüz çıkartman yok, + ile yükle" else "Henüz çıkartması yok",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                val columns = 4
                val cellSize = 80.dp
                val spacing = 8.dp
                val rows = (stickers.size + columns - 1) / columns
                val gridHeight = (cellSize * rows) + (spacing * (rows - 1).coerceAtLeast(0))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    gridItems(stickers, key = { it.id }) { sticker ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (isSelf) {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { removeTarget = sticker },
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            AsyncImage(
                                model = sticker.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    val target = removeTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Çıkartmayı kaldır") },
            text = { Text("Bu çıkartmayı listenden kaldırmak istiyor musun?") },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    scope.launch {
                        stickersRepository.removeSticker(target.id)
                        reload()
                    }
                }) { Text("Kaldır") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Vazgeç") }
            },
        )
    }
}

/**
 * 2026-08-22 (kullanıcı isteği: "storyler 24 saat sonra siliniyor ama
 * paylaşan kişi arşivinde görsün, istediği zaman öne çıkarsın, silsin") —
 * ProfileStickersContent'in AYNI ServiceLocator-doğrudan deseni (ayrı bir
 * ViewModel İCAT EDİLMEDİ). Backend artık süresi dolmuş hikayeleri
 * SİLMİYOR (bkz. app/stories.py::_cleanup_expired_stories() no-op
 * değişikliği), `GET stories/archive` sahibinin kendi arşivini döner.
 *
 * Etkileşim: TIKLAMA tam ekran önizleme açar (görsel/video için mevcut
 * FullscreenImageViewer/FullscreenVideoViewer REUSE edilir; metin-only
 * hikayede basit bir yerinde önizleme). BASILI TUTMA (uzun basma) "Öne
 * Çıkar"/"Sil" seçeneklerini gösteren bir ModalBottomSheet açar —
 * MessageActionsSheet'teki AYNI "tıklama=görüntüle, uzun basma=aksiyon
 * menüsü" ayrımı.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileStoryArchiveContent() {
    val storiesRepository = remember { ServiceLocator.storiesRepository }
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var previewTarget by remember { mutableStateOf<Story?>(null) }
    var actionTarget by remember { mutableStateOf<Story?>(null) }
    val actionSheetState = rememberModalBottomSheetState()
    var highlightTarget by remember { mutableStateOf<Story?>(null) }
    var highlightTitle by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Story?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        when (val result = storiesRepository.getStoryArchive()) {
            is StoryArchiveResult.Success -> {
                stories = result.stories
                errorMsg = null
            }
            is StoryArchiveResult.Error -> errorMsg = "Arşiv yüklenemedi"
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)) {
        Text("Hikaye Arşivim", style = MaterialTheme.typography.titleMedium)

        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            errorMsg != null -> Text(
                text = errorMsg ?: "",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            stories.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Süresi dolmuş hikayen yok",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                val columns = 4
                val cellSize = 80.dp
                val spacing = 8.dp
                val rows = (stories.size + columns - 1) / columns
                val gridHeight = (cellSize * rows) + (spacing * (rows - 1).coerceAtLeast(0))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(gridHeight),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    gridItems(stories, key = { it.id }) { story ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(parseHexColor(story.backgroundColor) ?: MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onClick = { previewTarget = story },
                                    onLongClick = { actionTarget = story },
                                ),
                        ) {
                            when {
                                !story.imageUrl.isNullOrBlank() -> AsyncImage(
                                    model = story.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                !story.videoUrl.isNullOrBlank() -> Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                                )
                                !story.caption.isNullOrBlank() -> Text(
                                    text = story.caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = parseHexColor(story.captionColor) ?: Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewTarget?.let { story ->
        when {
            !story.imageUrl.isNullOrBlank() -> FullscreenImageViewer(
                imageUrl = story.imageUrl,
                onDismiss = { previewTarget = null },
            )
            !story.videoUrl.isNullOrBlank() -> FullscreenVideoViewer(
                videoUrl = story.videoUrl,
                onDismiss = { previewTarget = null },
            )
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(parseHexColor(story.backgroundColor) ?: Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { previewTarget = null },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!story.caption.isNullOrBlank()) {
                    StoryCaptionText(
                        text = story.caption,
                        captionStyle = story.captionStyle,
                        captionColor = story.captionColor,
                    )
                }
                IconButton(
                    onClick = { previewTarget = null },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
    }

    if (actionTarget != null) {
        ModalBottomSheet(
            sheetState = actionSheetState,
            onDismissRequest = { actionTarget = null },
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                DropdownMenuItem(
                    text = { Text("Öne Çıkanlara Ekle") },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        highlightTarget = actionTarget
                        highlightTitle = ""
                        actionTarget = null
                    },
                )
                DropdownMenuItem(
                    text = { Text("Sil", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        deleteTarget = actionTarget
                        actionTarget = null
                    },
                )
            }
        }
    }

    highlightTarget?.let { story ->
        AlertDialog(
            onDismissRequest = { if (!busy) highlightTarget = null },
            title = { Text("Öne çıkanlara ekle") },
            text = {
                OutlinedTextField(
                    value = highlightTitle,
                    onValueChange = { highlightTitle = it },
                    label = { Text("Başlık") },
                    singleLine = true,
                    enabled = !busy,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = highlightTitle.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (storiesRepository.saveHighlight(story.id, newTitle = highlightTitle)) {
                                is SaveHighlightResult.Success -> {
                                    highlightTarget = null
                                    // Öne çıkarılan hikaye arşivde KALIR — kullanıcı
                                    // isterse AYRICA silebilir (iki aksiyon BAĞIMSIZ,
                                    // öne çıkarma otomatik silme ANLAMINA GELMİYOR).
                                }
                                is SaveHighlightResult.Error -> errorMsg = "Öne çıkarılamadı, lütfen tekrar dene"
                            }
                            busy = false
                        }
                    },
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { highlightTarget = null }) { Text("Vazgeç") }
            },
        )
    }

    deleteTarget?.let { story ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteTarget = null },
            title = { Text("Hikayeyi sil") },
            text = { Text("Bu hikayeyi kalıcı olarak silmek istediğine emin misin? Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (storiesRepository.deleteStory(story.id)) {
                                is DeleteStoryResult.Success -> {
                                    stories = stories.filterNot { it.id == story.id }
                                    deleteTarget = null
                                }
                                is DeleteStoryResult.Error -> errorMsg = "Silinemedi, lütfen tekrar dene"
                            }
                            busy = false
                        }
                    },
                ) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { deleteTarget = null }) { Text("Vazgeç") }
            },
        )
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
    isCloseFriend: Boolean,
    onToggleFollow: () -> Unit,
    onRequestUnfollow: () -> Unit,
    onToggleCloseFriend: () -> Unit,
    onMessageClick: () -> Unit,
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
                    // Gorsel cila (animasyon turu): stats degerleri (takip/takipci
                    // sayaci vb.) guncellendiginde satirin boyutu YUMUSAKCA
                    // gecis yapar - ani "ziplama" yerine akici bir buyume/kuculme.
                    .animateContentSize()
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
            // 2026-08-22 (kullanıcı raporu: "başkasının profili boş görünüyor")
            // — web'in profile.html'deki "Takip Et" + "Mesaj gönder" yan yana
            // düzeninin karşılığı: eskiden SADECE tam genişlikli FollowActionButton
            // vardı, tek başına bu kadar geniş bir buton + altında boşluk
            // "eksik/boş" bir izlenim veriyordu. Engellenmiş kullanıcıya mesaj
            // GÖNDERİLEMEZ (backend zaten reddediyor, bkz. StartConversationResult
            // "blocked" kodu) — bu yüzden isBlockedByMe'de Mesaj Gönder hiç
            // gösterilmez, FollowActionButton (devre dışı "Engellendi" durumu)
            // tek başına tam genişlik kalır.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FollowActionButton(
                    isBlockedByMe = isBlockedByMe,
                    isFollowing = isFollowing,
                    isPendingRequest = isPendingRequest,
                    isCloseFriend = isCloseFriend,
                    onToggleFollow = onToggleFollow,
                    onRequestUnfollow = onRequestUnfollow,
                    onToggleCloseFriend = onToggleCloseFriend,
                    modifier = Modifier.weight(1f),
                )
                if (!isBlockedByMe) {
                    OutlinedButton(
                        onClick = onMessageClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mesaj Gönder")
                    }
                }
            }
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
 *
 * Animasyon turu: butona basinca HAFIF bir "scale-bounce" geri bildirimi
 * eklendi (kucul-buyu) - [onToggleFollow] mantigi/sirasi DEGISMEDI, sadece
 * tiklama anina gorsel bir tepki bindirildi.
 */
@Composable
private fun FollowActionButton(
    isBlockedByMe: Boolean,
    isFollowing: Boolean,
    isPendingRequest: Boolean,
    // 2026-08-22 (kullanıcı isteği: "takip ediliyor kısmında tıklayınca
    // takipten çık, yakın arkadaşlara ekle falan olsun") — web'in
    // profile.html'deki "Takip ▾" açılır menüsünün (takipten çık + yakın
    // arkadaşlara ekle/çıkar) native karşılığı.
    isCloseFriend: Boolean,
    onToggleFollow: () -> Unit,
    // Eskiden isFollowing durumunda DOĞRUDAN onToggleFollow (tek dokunuşla
    // takipten çıkma) çağrılıyordu — kullanıcı raporu: "takipten çıkmadan önce
    // uyarı gelsin" — artık SADECE menüden "Takipten Çık" seçilince bu
    // çağrılır (gerçek toggleFollow() ProfileScreen'deki onay diyaloğu
    // ONAYLANINCA tetiklenir, bkz. ProfileScreen showUnfollowConfirmDialog).
    onRequestUnfollow: () -> Unit,
    onToggleCloseFriend: () -> Unit,
    // 2026-08-22: "Mesaj Gönder" butonuyla yan yana (Row + weight(1f)) durunca
    // ARTIK tam genişlik/üst boşluk BU BİLEŞENİN kendi kararı DEĞİL, çağıran
    // Row'un — o yüzden dıştan enjekte edilebiliyor (varsayılan eski tam
    // genişlikli davranışı KORUYOR, tek başına kullanıldığında fark etmez).
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val scope = rememberCoroutineScope()
    val bounceScale = remember { Animatable(1f) }
    var showFollowingMenu by remember { mutableStateOf(false) }

    val onBouncedClick: () -> Unit = {
        scope.launch {
            bounceScale.animateTo(0.92f, animationSpec = tween(80))
            bounceScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        onToggleFollow()
    }

    val buttonModifier = modifier
        .height(46.dp)
        .scale(bounceScale.value)

    when {
        isBlockedByMe -> OutlinedButton(onClick = {}, enabled = false, modifier = buttonModifier) {
            Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Engellendi")
        }

        isFollowing -> Box(modifier = modifier) {
            OutlinedButton(
                onClick = { showFollowingMenu = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .scale(bounceScale.value),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Takip Ediliyor")
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showFollowingMenu, onDismissRequest = { showFollowingMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isCloseFriend) "Yakın Arkadaşlardan Çıkar" else "Yakın Arkadaşlara Ekle") },
                    leadingIcon = {
                        Icon(
                            if (isCloseFriend) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        showFollowingMenu = false
                        onToggleCloseFriend()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Takipten Çık") },
                    leadingIcon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
                    onClick = {
                        showFollowingMenu = false
                        onRequestUnfollow()
                    },
                )
            }
        }

        isPendingRequest -> OutlinedButton(onClick = onBouncedClick, modifier = buttonModifier) {
            Icon(Icons.Filled.HourglassEmpty, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("İstek Gönderildi")
        }

        else -> Button(onClick = onBouncedClick, modifier = buttonModifier) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Takip Et")
        }
    }
}

