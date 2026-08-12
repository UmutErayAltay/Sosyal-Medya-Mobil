package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.MessageSearchEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.MessageSearchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * TÜM sohbetlerde mesaj arama (Faz 5 Dalga 1B) — NewMessageScreen ile AYNI
 * TextField+LazyColumn deseni. Sonuca tıklanınca [onNavigateToConversation]
 * ile ilgili sohbete gidilir (backend her satırda conversation_id döner,
 * bkz. app/api_v1/messaging.py _serialize_search_row() with_conversation=True
 * dalı). Sohbet-İÇİ arama BU EKRAN DEĞİL — ConversationScreen'in kendi arama
 * modu (ConversationViewModel.searchInConversation) kullanılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: MessageSearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val hasNext by viewModel.hasNext.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MessageSearchEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val listState = rememberLazyListState()
    // Performans düzeltmesi (2026-08-12) — FeedScreen.kt/DiscoverScreen.kt'deki
    // AYNI kök neden ve düzeltme: snapshotFlow { listState.layoutInfo } HER
    // frame'de dedupe'siz çalışıyordu. HideableTopBar.kt:92'deki DOĞRU deseni
    // takip eder — sadece Int emit edilir. `results.size` karşılaştırması
    // (total item sayısı yerine) BURADA BİLEREK KORUNUR.
    LaunchedEffect(listState, hasNext) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (hasNext && lastVisible >= results.size - 3) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesajlarda Ara") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Mesajlarda ara...") },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            when {
                loading && results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                error != null && results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                query.trim().length >= 2 && results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Sonuç bulunamadı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(results, key = { it.id }) { result ->
                        // Animasyon turu (3. kısım) — kullanıcı yazdıkça sonuç
                        // listesi filtrelenirken satırların animateItem() ile
                        // yumuşak yer değiştirmesi/belirmesi/kaybolması (bkz.
                        // NewMessageScreen.kt AYNI desen — Compose 1.7+
                        // LazyItemScope API'si, yeni bağımlılık gerekmiyor).
                        Column(modifier = Modifier.animateItem()) {
                            MessageSearchResultRow(
                                result = result,
                                onClick = { result.conversationId?.let(onNavigateToConversation) },
                            )
                            HorizontalDivider()
                        }
                    }
                    if (loadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(modifier = Modifier.padding(4.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchResultRow(result: MessageSearchResultDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (result.mine) "Sen" else (result.sender ?: "?"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = result.content ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
