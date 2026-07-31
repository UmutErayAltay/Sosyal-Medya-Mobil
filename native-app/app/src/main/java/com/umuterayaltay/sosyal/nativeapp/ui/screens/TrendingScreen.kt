package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.TrendingEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.TrendingViewModel

/**
 * Gündem ekranı — app/hashtags.py trending_all()/_trending_hashtags()'in native
 * karşılığı (Faz 4 sonrası eksik giderme SONUNCUSU). FeedScreen'in TopAppBar'ındaki
 * "#" ikonundan erişilir, bir etikete tıklanınca [onNavigateToHashtag] ile
 * HashtagScreen'e gidilir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: TrendingViewModel = viewModel(),
) {
    val tags by viewModel.tags.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TrendingEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gündem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading && tags.isEmpty() -> CenteredMessage(padding) { CircularProgressIndicator() }
            error != null && tags.isEmpty() -> CenteredMessage(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: "")
                    Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Tekrar dene")
                    }
                }
            }
            tags.isEmpty() -> CenteredMessage(padding) { Text("Şu an gündemde bir şey yok") }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(tags, key = { it.tag }) { tag ->
                    TrendingRow(tag = tag, onClick = { onNavigateToHashtag(tag.tag) })
                }
            }
        }
    }
}

/** NotificationsScreen.kt'deki (file-private) CenteredMessage ile AYNI görsel —
 * Kotlin top-level `private` dosya sınırlarını aştığı için burada AYRI bir
 * kopya tutuluyor. */
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

/** DiscoverScreen.kt'deki (file-private, tıklanamaz) HashtagResultRow ile GÖRSEL
 * olarak tutarlı — buradaki AYRI kopya tıklanabilir (onClick var), gündem
 * listesinde tıklamak artık HashtagScreen'e gider. */
@Composable
private fun TrendingRow(tag: HashtagSearchDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = "#${tag.tag}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Text(
            text = "${tag.count} gönderi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
