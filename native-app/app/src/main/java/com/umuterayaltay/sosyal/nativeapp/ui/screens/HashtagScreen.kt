package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HashtagEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HashtagViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.HashtagViewModelFactory

/**
 * Hashtag detay ekranı — app/hashtags.py hashtag_posts()'ın native karşılığı
 * (Faz 4 sonrası eksik giderme SONUNCUSU). DiscoverScreen'in arama-sonucu post
 * listesindeki AYNI PostCard + toggleLike deseni, üstüne ProfileScreen'in
 * takip butonuyla AYNI stil (OutlinedButton takip ediliyorken, Button takip
 * edilmiyorken) bir takip/bırak butonu eklendi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagScreen(
    tag: String,
    onNavigateBack: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: HashtagViewModel = viewModel(factory = HashtagViewModelFactory(tag)),
) {
    val posts by viewModel.posts.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HashtagEvent.SessionExpired -> onSessionExpired()
                is HashtagEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Web hashtag.html'deki başlık altı "N post" alt metniyle aynı dil —
                // etiketin ne kadar aktif olduğu tek bakışta görülür.
                title = {
                    Column {
                        Text(text = "#$tag", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${posts.size} gönderi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    // ProfileScreen.kt'deki FollowActionButton ile AYNI ikon+metin
                    // dili (Check/PersonAdd) — takip durumunun görsel imzası tutarlı.
                    if (isFollowing) {
                        OutlinedButton(
                            onClick = viewModel::toggleFollow,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Takip Ediliyor")
                        }
                    } else {
                        Button(
                            onClick = viewModel::toggleFollow,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Takip Et")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && posts.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && posts.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            posts.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Tag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "Bu etikette henüz gönderi yok",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onLikeClick = { viewModel.toggleLike(it.id) },
                        onCommentClick = { onNavigateToPostDetail(it.id) },
                        onHashtagClick = { /* zaten bu etiketin sayfasındayız */ },
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
                    )
                }
            }
        }
    }
}

/** NotificationsScreen.kt'deki (file-private) CenteredMessage ile AYNI görsel —
 * Kotlin top-level `private` dosya sınırlarını aştığı için burada AYRI bir
 * kopya tutuluyor (o dosyanın da aynı gerekçeyle kendi kopyası var). */
@Composable
private fun CenteredMessage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
