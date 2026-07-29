package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListKind
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowListViewModelFactory

/**
 * Takipci/takip edilen listesi - DiscoverScreen'deki UserResultRow ile TUTARLI
 * gorunum icin paylasilan UserRow composable'i (ui/screens/UserRow.kt) kullanir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    username: String,
    kind: FollowListKind,
    onUserClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: FollowListViewModel = viewModel(
        factory = FollowListViewModelFactory(username, kind),
    ),
) {
    val users by viewModel.users.collectAsState()
    val title by viewModel.title.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FollowListEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val defaultTitle = if (kind == FollowListKind.Followers) "Takipçiler" else "Takip Edilenler"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: defaultTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && users.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && users.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            users.isEmpty() -> CenteredMessage(padding) { Text("Henüz kimse yok.") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    UserRow(
                        avatarUrl = user.avatarUrl,
                        username = user.username,
                        fullName = user.fullName,
                        onClick = { user.username?.let(onUserClick) },
                    )
                }
            }
        }
    }
}

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
