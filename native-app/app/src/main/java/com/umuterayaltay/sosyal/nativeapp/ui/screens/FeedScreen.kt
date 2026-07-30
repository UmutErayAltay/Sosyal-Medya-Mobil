package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FeedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onSessionExpired: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNewPostClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    viewModel: FeedViewModel = viewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val unreadNotificationsCount by viewModel.unreadNotificationsCount.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
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
                    IconButton(onClick = onNewPostClick) {
                        Icon(Icons.Filled.Add, contentDescription = "Yeni Gönderi")
                    }
                },
            )
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
                    CircularProgressIndicator()
                }
                posts.isEmpty() && uiState is FeedUiState.Error -> FullScreenMessage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((uiState as FeedUiState.Error).message)
                        Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Tekrar dene")
                        }
                    }
                }
                posts.isEmpty() -> FullScreenMessage {
                    Text("Henüz gönderi yok. Takip ettiklerin paylaşım yaptığında burada görünecek.")
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(it.id) },
                            onCommentClick = { onNavigateToPostDetail(it.id) },
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
