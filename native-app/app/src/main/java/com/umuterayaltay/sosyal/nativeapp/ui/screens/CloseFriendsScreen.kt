package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CloseFriendsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CloseFriendsViewModel

/**
 * "Yakın Arkadaşlar" ekranı — üstte NewMessageScreen'deki DESENLE tutarlı bir
 * arama kutusu (en az 2 karakterden itibaren, mevcut kullanıcı arama endpoint'i
 * reuse edilir, bkz. CloseFriendsViewModel), altta mevcut yakın arkadaş listesi
 * (UserRow + kaldır ikonu). Arama kutusu doluyken sonuçlar gösterilir (tıklayınca
 * ekler), boşken mevcut liste gösterilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseFriendsScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: CloseFriendsViewModel = viewModel(),
) {
    val friends by viewModel.friends.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CloseFriendsEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yakın Arkadaşlar") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                placeholder = { Text("Eklemek için kullanıcı adı ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            when {
                searchQuery.length >= 2 -> {
                    when {
                        searching && searchResults.isEmpty() -> CenteredBox { CircularProgressIndicator() }
                        searchResults.isEmpty() -> CenteredBox { Text("Kullanıcı bulunamadı") }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            items(searchResults, key = { it.id }) { user ->
                                UserRow(
                                    avatarUrl = user.avatarUrl,
                                    username = user.username,
                                    fullName = user.fullName,
                                    onClick = { viewModel.add(user.id) },
                                )
                            }
                        }
                    }
                }

                loading && friends.isEmpty() -> CenteredBox { CircularProgressIndicator() }

                friends.isEmpty() -> CenteredBox { Text("Henüz yakın arkadaşın yok.") }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(friends, key = { it.id }) { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserRow(
                                avatarUrl = user.avatarUrl,
                                username = user.username,
                                fullName = user.fullName,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.remove(user.id) }) {
                                Icon(Icons.Filled.PersonRemove, contentDescription = "Yakın arkadaşlıktan çıkar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
