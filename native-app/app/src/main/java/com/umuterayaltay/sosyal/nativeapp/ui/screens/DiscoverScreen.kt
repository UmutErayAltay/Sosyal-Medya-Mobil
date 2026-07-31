package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.network.SavedSearchItemDto
import com.umuterayaltay.sosyal.nativeapp.network.SearchHistoryItemDto
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.viewmodel.DiscoverEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.DiscoverViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.SearchType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Keşfet sekmesi — arama (kullanıcı/post/hashtag, tip filtresi, tarih aralığı,
 * son/kayıtlı aramalar) + algoritmik keşfet akışı (sonsuz kaydırma) TEK
 * kaydırılabilir liste olarak birleştirildi (backend sözleşmesi: app/api_v1.py
 * discover()/search() — bkz. DiscoverViewModel docstring'i).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onSessionExpired: () -> Unit,
    onUserClick: (String) -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToHashtag: (String) -> Unit,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val query by viewModel.searchQuery.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val dateFrom by viewModel.dateFrom.collectAsState()
    val dateTo by viewModel.dateTo.collectAsState()
    val searchUsers by viewModel.searchUsers.collectAsState()
    val searchPosts by viewModel.searchPosts.collectAsState()
    val searchHashtags by viewModel.searchHashtags.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val savedSearches by viewModel.savedSearches.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val discoverPosts by viewModel.discoverPosts.collectAsState()
    val discoverLoading by viewModel.discoverLoading.collectAsState()
    val discoverHasMore by viewModel.discoverHasMore.collectAsState()
    val discoverError by viewModel.discoverError.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val listState = rememberLazyListState()
    // Sonsuz kaydırma: son görünür öğe listenin sonuna yaklaşınca sonraki sayfa
    // istenir — loadMoreDiscover() zaten hasMore/loading guard'lı, tekrar tekrar
    // tetiklenmesi zararsız (early-return).
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (total > 0 && lastVisible >= total - 3) {
                viewModel.loadMoreDiscover()
            }
        }
    }

    val isSearchActive = query.length >= 2

    Scaffold(
        topBar = { TopAppBar(title = { Text("Keşfet") }) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SearchBarRow(
                    query = query,
                    onQueryChange = viewModel::onQueryChange,
                    onSave = { viewModel.saveCurrentSearch() },
                    showSaveButton = isSearchActive,
                )
            }

            if (isSearchActive) {
                item {
                    SearchTypeFilterRow(selected = searchType, onSelect = viewModel::onSearchTypeChange)
                }
                if (searchType == SearchType.All || searchType == SearchType.Posts) {
                    item {
                        DateRangeRow(
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            onChange = viewModel::onDateRangeChange,
                        )
                    }
                }

                val noResults = searchUsers.isEmpty() && searchPosts.isEmpty() && searchHashtags.isEmpty()
                when {
                    searchLoading && noResults -> item { CenteredBox { CircularProgressIndicator() } }
                    searchError != null -> item {
                        CenteredBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(searchError ?: "")
                                Button(
                                    onClick = { viewModel.search(query) },
                                    modifier = Modifier.padding(top = 12.dp),
                                ) { Text("Tekrar dene") }
                            }
                        }
                    }
                    noResults -> item {
                        CenteredBox { Text("Sonuç bulunamadı") }
                    }
                    else -> {
                        if (searchType == SearchType.All || searchType == SearchType.Users) {
                            if (searchUsers.isNotEmpty()) {
                                item { SectionHeader("Kullanıcılar") }
                                items(searchUsers, key = { "user_${it.id}" }) { user ->
                                    UserResultRow(user, onClick = { user.username?.let(onUserClick) })
                                }
                            }
                        }
                        if (searchType == SearchType.All || searchType == SearchType.Hashtags) {
                            if (searchHashtags.isNotEmpty()) {
                                item { SectionHeader("Hashtag'ler") }
                                items(searchHashtags, key = { "tag_${it.tag}" }) { tag ->
                                    HashtagResultRow(tag, onClick = { onNavigateToHashtag(tag.tag) })
                                }
                            }
                        }
                        if (searchType == SearchType.All || searchType == SearchType.Posts) {
                            if (searchPosts.isNotEmpty()) {
                                item { SectionHeader("Postlar") }
                                items(searchPosts, key = { "post_${it.id}" }) { post ->
                                    PostCard(
                                        post = post,
                                        onLikeClick = { viewModel.toggleLike(it.id) },
                                        onCommentClick = { onNavigateToPostDetail(it.id) },
                                        onHashtagClick = onNavigateToHashtag,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                if (recentSearches.isNotEmpty()) {
                    item {
                        SectionHeaderWithAction(title = "Son Aramalar", actionLabel = "Temizle") {
                            viewModel.clearHistory()
                        }
                    }
                    item {
                        RecentSearchesRow(
                            historyItems = recentSearches,
                            onSelect = viewModel::selectRecentSearch,
                            onDelete = viewModel::deleteHistoryItem,
                        )
                    }
                }

                if (savedSearches.isNotEmpty()) {
                    item { SectionHeader("Kayıtlı Aramalar") }
                    items(savedSearches, key = { "saved_${it.id}" }) { saved ->
                        SavedSearchRow(
                            item = saved,
                            onSelect = viewModel::selectSavedSearch,
                            onDelete = viewModel::deleteSavedSearch,
                        )
                    }
                }

                item { SectionHeader("Keşfet") }

                when {
                    discoverPosts.isEmpty() && discoverLoading -> item { CenteredBox { CircularProgressIndicator() } }
                    discoverPosts.isEmpty() && discoverError != null -> item {
                        CenteredBox {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(discoverError ?: "")
                                Button(
                                    onClick = { viewModel.loadDiscoverPage(1) },
                                    modifier = Modifier.padding(top = 12.dp),
                                ) { Text("Tekrar dene") }
                            }
                        }
                    }
                    discoverPosts.isEmpty() -> item {
                        CenteredBox { Text("Şu an gösterilecek bir şey yok.") }
                    }
                    else -> {
                        items(discoverPosts, key = { "discover_${it.id}" }) { post ->
                            PostCard(
                                post = post,
                                onLikeClick = { viewModel.toggleLike(it.id) },
                                onCommentClick = { onNavigateToPostDetail(it.id) },
                                onHashtagClick = onNavigateToHashtag,
                            )
                        }
                        item {
                            when {
                                discoverLoading -> CenteredBox { CircularProgressIndicator() }
                                discoverHasMore -> Button(
                                    onClick = { viewModel.loadMoreDiscover() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                ) { Text("Daha fazla yükle") }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSave: () -> Unit,
    showSaveButton: Boolean,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        placeholder = { Text("Kullanıcı, gönderi veya #hashtag ara") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Aramayı temizle")
                    }
                }
                if (showSaveButton) {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Aramayı kaydet")
                    }
                }
            }
        },
    )
}

@Composable
private fun SearchTypeFilterRow(selected: SearchType, onSelect: (SearchType) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchType.entries) { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeRow(
    dateFrom: String?,
    dateTo: String?,
    onChange: (String?, String?) -> Unit,
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = { showFromPicker = true },
            label = { Text(dateFrom ?: "Başlangıç tarihi") },
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = { showToPicker = true },
            label = { Text(dateTo ?: "Bitiş tarihi") },
            modifier = Modifier.weight(1f),
        )
        if (dateFrom != null || dateTo != null) {
            IconButton(onClick = { onChange(null, null) }) {
                Icon(Icons.Filled.Close, contentDescription = "Tarih filtresini temizle")
            }
        }
    }

    if (showFromPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    showFromPicker = false
                    if (millis != null) onChange(millisToIsoDate(millis), dateTo)
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Vazgeç") } },
        ) {
            DatePicker(state = state)
        }
    }
    if (showToPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    showToPicker = false
                    if (millis != null) onChange(dateFrom, millisToIsoDate(millis))
                }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Vazgeç") } },
        ) {
            DatePicker(state = state)
        }
    }
}

private fun millisToIsoDate(millis: Long): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC).toString()

@Composable
private fun RecentSearchesRow(
    historyItems: List<SearchHistoryItemDto>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(historyItems, key = { it.id }) { historyItem ->
            InputChip(
                selected = false,
                onClick = { onSelect(historyItem.query) },
                label = { Text(historyItem.query) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Sil",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDelete(historyItem.id) },
                    )
                },
            )
        }
    }
}

@Composable
private fun SavedSearchRow(
    item: SavedSearchItemDto,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(item.query) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = item.label?.takeIf { it.isNotBlank() } ?: item.query,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!item.label.isNullOrBlank()) {
                Text(
                    text = item.query,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onDelete(item.id) }) {
            Icon(Icons.Filled.Close, contentDescription = "Kaydedilen aramayı sil")
        }
    }
}

/** Profil ekranina navigasyon simdi gercek - UserRow (ui/screens/UserRow.kt)
 * paylasilan composable'i sarmalar. */
@Composable
private fun UserResultRow(user: UserSearchDto, onClick: () -> Unit) {
    UserRow(
        avatarUrl = user.avatarUrl,
        username = user.username,
        fullName = user.fullName,
        onClick = onClick,
    )
}

/** Faz 4 sonrası eksik giderme SONUNCUSU: hashtag detay ekranı eklendi,
 * tıklanınca artık [onClick] ile "hashtag/{tag}" route'una gidilir. */
@Composable
private fun HashtagResultRow(hashtag: HashtagSearchDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = "#${hashtag.tag}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Text(
            text = "${hashtag.count}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionHeaderWithAction(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
