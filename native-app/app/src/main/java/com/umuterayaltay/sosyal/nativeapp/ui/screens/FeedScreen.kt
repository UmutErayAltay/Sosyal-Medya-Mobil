package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryBarEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.StoryBarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onSessionExpired: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNewPostClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onTrendingClick: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    // Faz 5 Dalga 2C: hikaye çubuğu — StoryBarViewModel FeedViewModel'e
    // KARIŞTIRILMADI (görev tanımı), ayrı `viewModel()` instance'ı.
    onNavigateToStoryViewer: (String) -> Unit = {},
    onNavigateToStoryCreate: () -> Unit = {},
    // Kullanıcı raporu: hikaye paylaşıldıktan sonra bu çubuk yenilenmiyordu -
    // StoryBarViewModel sekme değişiminde KORUNDUĞU için sadece bir kez
    // `init { loadBar() }` çalışıyordu. MainScaffold, NavController'ın
    // savedStateHandle'ından okuduğu "story_created" bayrağını buraya iletir;
    // true olunca çubuk yeniden yüklenir, sonra bayrak MainScaffold'da false'a
    // döndürülür (bkz. MainScaffold.kt).
    storyCreated: Boolean = false,
    onStoryBarRefreshHandled: () -> Unit = {},
    viewModel: FeedViewModel = viewModel(),
    storyBarViewModel: StoryBarViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()
    val storyBarItems by storyBarViewModel.items.collectAsState()
    val context = LocalContext.current

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

    // 3. deneme sonrası KESİN çözüm: nested-scroll tabanlı
    // enterAlwaysScrollBehavior() (PullToRefreshBox ile etkileşimde bar'ı TAM
    // gizletemiyordu, üstte kararmış bir şerit bırakıyordu) TAMAMEN TERK
    // EDİLDİ. Bunun yerine LazyListState'in scroll POZİSYONUNA bakan elle
    // yön tespiti (bkz. HideableTopBar.kt) — nested-scroll paylaşımı hiç
    // olmadığı için PullToRefreshBox'la çakışma ihtimali de ortadan kalkar.
    val listState = rememberLazyListState()
    val isTopBarVisible by rememberTopBarVisibility(listState)

    Scaffold(
        topBar = {
            HideableTopBar(visible = isTopBarVisible) {
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
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    contentPadding = PaddingValues(vertical = 12.dp),
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
                    items(posts, key = { it.id }) { post ->
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
                        )
                    }
                }
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
