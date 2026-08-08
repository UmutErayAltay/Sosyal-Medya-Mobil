package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.viewmodel.GroupCreateEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.GroupCreateViewModel

/**
 * "Yeni Grup" ekranı — NewMessageScreen'in AYNI arama desenini (debounce'lı
 * DiscoverRepository.search(type="users") reuse) kullanır ama ÇOKLU-SEÇİM'e
 * genişletir: bir sonuca tıklamak hemen konuşma başlatmak yerine [SelectableUserRow]
 * ile seçili listeye ekler/çıkarır — seçilenler üstte bir chip satırında özetlenir.
 * "Oluştur" TopAppBar aksiyonunda (CreatePostScreen'in "Paylaş" deseni), grup adı
 * dolu VE en az 2 kişi seçiliyken aktif olur.
 *
 * Başarılı olunca [onGroupCreated] çağrılır — navigasyon (conversation/{id}'ye
 * geçip bu ekranı popUpTo(inclusive) ile yığından çıkarma) AppNavHost'ta,
 * NewMessageScreen'in onConversationReady deseniyle AYNI şekilde yapılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCreateScreen(
    onGroupCreated: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: GroupCreateViewModel = viewModel(),
) {
    val name by viewModel.name.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val creating by viewModel.creating.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupCreateEvent.GroupReady -> onGroupCreated(event.conversationId)
                is GroupCreateEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    val selectedIds = selected.mapTo(HashSet()) { it.id }
    val canCreate = name.isNotBlank() && selected.size >= 2 && !creating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yeni Grup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !creating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (creating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = viewModel::createGroup, enabled = canCreate) {
                            Text("Oluştur")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Grup adı") },
                singleLine = true,
                enabled = !creating,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            // Animasyon turu (3. kısım) — seçili-kişi chip satırı ekleyip
            // çıkarırken (LazyRow + özet metni birlikte belirip/kaybolurken)
            // altındaki arama alanı/sonuç listesi ANİDEN zıplamak yerine
            // animateContentSize() ile yumuşak kayıyor. Sarmalayıcı Column
            // HER ZAMAN var (koşul artık İÇERDE), animateContentSize bu
            // yüzden 0-yükseklikten gerçek yüksekliğe geçişi animasyonla
            // yakalayabiliyor.
            Column(modifier = Modifier.animateContentSize()) {
                if (selected.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(selected, key = { it.id }) { user ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeSelected(user.id) },
                                label = { Text(user.username ?: "kullanıcı") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Çıkar",
                                        modifier = Modifier.size(InputChipDefaults.IconSize),
                                    )
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    Text(
                        text = "${selected.size} kişi seçildi (en az 2 gerekli)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Eklemek için kullanıcı ara") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                enabled = !creating,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            if (error != null) {
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            when {
                loading && results.isEmpty() -> CenteredBox { CircularProgressIndicator() }
                query.length < 2 -> CenteredBox { MutedText("Aramak için en az 2 karakter yaz") }
                results.isEmpty() -> CenteredBox { MutedText("Kullanıcı bulunamadı") }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(results, key = { it.id }) { user ->
                        SelectableUserRow(
                            avatarUrl = user.avatarUrl,
                            username = user.username,
                            fullName = user.fullName,
                            selected = user.id in selectedIds,
                            enabled = !creating,
                            onClick = { viewModel.toggleSelected(user) },
                            modifier = Modifier.animateItem(),
                        )
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
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Çoklu-seçim satırı — UserRow ile AYNI görsel dil (avatar + kullanıcı adı +
 * tam ad) ama sağda bir seçili/değil göstergesi (dolu/boş daire içinde onay
 * işareti) var. GroupCreateScreen ("Oluştur") ve GroupManageScreen ("Ekle")
 * üye ekleme alt-akışında AYNEN reuse edilir — ikisi de "birden fazla kullanıcı
 * seç, sonra bir butona bas" deseninde, ayrı bir composable İCAT ETMEK yerine
 * BU dosyada TEK yerde tanımlandı (top-level, package-private değil - diğer
 * ekran dosyası da erişebilsin diye).
 */
@Composable
fun SelectableUserRow(
    avatarUrl: String?,
    username: String?,
    fullName: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = username ?: "bilinmeyen", style = MaterialTheme.typography.titleSmall)
            if (!fullName.isNullOrBlank()) {
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Seçili",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
