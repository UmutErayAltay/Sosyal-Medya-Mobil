package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.network.FollowUserDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowRequestsEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.FollowRequestsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowRequestsScreen(
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: FollowRequestsViewModel = viewModel(),
) {
    val requests by viewModel.requests.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Kabul/reddet tiklandigi ANDA satiri gorsel olarak gizler (fade-out) -
    // ViewModel.accept()/reject() network cagrisi bitince load() ile TUM
    // listeyi yeniden cekiyor (bkz. FollowRequestsViewModel yorumu), bu yuzden
    // gercek veri guncellemesi biraz gecikebilir; hiddenIds ANINDA tepki
    // vermek icin ayri, sadece-goruntu amacli bir yerel state.
    var hiddenIds by remember { mutableStateOf(setOf<String>()) }

    // Kendini-onaran guvenlik agi: accept()/reject() sonuc kontrol etmeden
    // load() cagiriyor (bkz. FollowRequestsViewModel yorumu) - network hatasi
    // yuzunden istek GERCEKTE reddedilmemis/kabul edilmemis olabilir. Yenilenen
    // `requests` icinde hala varsa (yani gercekten kalkmadiysa) hiddenIds'ten
    // cikarilir - satir TEKRAR gorunur (sonsuza dek gizli kalan "hayalet" satir
    // riskini onler).
    LaunchedEffect(requests) {
        val stillPresentIds = requests.map { it.id }.toSet()
        hiddenIds = hiddenIds.filter { it !in stillPresentIds }.toSet()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FollowRequestsEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Takip İstekleri") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && requests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            error != null && requests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }

            requests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Bekleyen takip isteği yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(requests, key = { _, user -> user.id }) { index, user ->
                    val visible = user.id !in hiddenIds
                    StaggeredRequestItem(index = index, visible = visible) {
                        FollowRequestRow(
                            user = user,
                            onAccept = {
                                hiddenIds = hiddenIds + user.id
                                viewModel.accept(user.id)
                            },
                            onReject = {
                                hiddenIds = hiddenIds + user.id
                                viewModel.reject(user.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowRequestRow(user: FollowUserDto, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        UserRow(avatarUrl = user.avatarUrl, username = user.username, fullName = user.fullName)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                Text("Kabul et")
            }
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                Text("Reddet")
            }
        }
    }
}

/**
 * FollowListScreen.kt'deki StaggeredListItem ile AYNI stagger-giris fikri,
 * EK olarak [visible] false olunca (kabul/reddet tiklandiginda) fade-out +
 * shrinkVertically ile satirin listeden YUMUSAKCA cikmasini saglar - gercek
 * veri guncellemesi (bkz. hiddenIds yorumu) biraz geciktiginde bile kullanici
 * ANINDA gorsel geri bildirim alir.
 */
@Composable
private fun StaggeredRequestItem(index: Int, visible: Boolean, content: @Composable () -> Unit) {
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
