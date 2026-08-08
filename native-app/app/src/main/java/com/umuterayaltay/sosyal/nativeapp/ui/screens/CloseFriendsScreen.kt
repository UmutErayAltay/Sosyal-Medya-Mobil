package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CloseFriendsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CloseFriendsViewModel
import kotlinx.coroutines.delay

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

    // FollowRequestsScreen.kt'deki AYNI fikir: "Çıkar" tiklaninca satiri ANINDA
    // fade-out ile gizler - CloseFriendsViewModel.remove() network cagrisi
    // bitince load() ile listeyi yeniden cektigi icin gercek veri guncellemesi
    // biraz gecikebilir (bkz. FollowRequestsScreen'deki hiddenIds yorumu).
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }

    // FollowRequestsScreen.kt'deki AYNI kendi-kendini-onaran guvenlik agi:
    // remove() basarisiz olursa (network hatasi) satir `friends` icinde hala
    // vardir - hiddenIds'ten cikarilip TEKRAR gorunur olur.
    LaunchedEffect(friends) {
        val stillPresentIds = friends.map { it.id }.toSet()
        hiddenIds = hiddenIds.filter { it !in stillPresentIds }.toSet()
    }

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
                        searchResults.isEmpty() -> CenteredBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Kullanıcı bulunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            itemsIndexed(searchResults, key = { _, user -> user.id }) { index, user ->
                                StaggeredCloseFriendItem(index = index, visible = true) {
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
                }

                loading && friends.isEmpty() -> CenteredBox { CircularProgressIndicator() }

                friends.isEmpty() -> CenteredBox {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Stars,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Henüz yakın arkadaşın yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(friends, key = { _, user -> user.id }) { index, user ->
                        StaggeredCloseFriendItem(index = index, visible = user.id !in hiddenIds) {
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
                                // BlockedUsersScreen'in "Engeli Kaldır" TextButton'ıyla AYNI
                                // görsel dil — daha önce ikon-tek IconButton'du, etikete
                                // dönüştürüldü (davranış AYNI, sadece görünüm).
                                TextButton(
                                    onClick = {
                                        hiddenIds = hiddenIds + user.id
                                        viewModel.remove(user.id)
                                    },
                                ) {
                                    Text("Çıkar", color = MaterialTheme.colorScheme.error)
                                }
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

/**
 * FollowListScreen.kt/FollowRequestsScreen.kt'deki AYNI stagger-giris +
 * fade-out-cikis fikri: satir ilk kez composition'a girdiginde index'e bagli
 * KISA bir gecikmeyle belirir, [visible] false olunca (ör. "Çıkar" tiklaninca)
 * fade-out + shrinkVertically ile YUMUSAKCA listeden cikar.
 */
@Composable
private fun StaggeredCloseFriendItem(index: Int, visible: Boolean, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 10) * 30L)
        entered = true
    }
    AnimatedVisibility(
        visible = entered && visible,
        enter = fadeIn(animationSpec = tween(200)) +
            slideInVertically(animationSpec = tween(200)) { it / 8 },
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200)),
    ) {
        content()
    }
}
