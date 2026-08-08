package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.CollectionDto
import com.umuterayaltay.sosyal.nativeapp.repository.CollectionsResult
import com.umuterayaltay.sosyal.nativeapp.repository.CreateCollectionResult
import com.umuterayaltay.sosyal.nativeapp.repository.SetBookmarkCollectionResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import kotlinx.coroutines.launch

/**
 * Madde 2 (kullanıcı raporu: Kaydet ikonuna basılı tutunca koleksiyon seçici
 * açılsın) — PostCard.kt'deki Kaydet [IconButton]'ının uzun-basma (long-press)
 * aksiyonuyla açılır. BookmarksRepository'deki getCollections()/
 * createCollection()/toggleBookmark()/setBookmarkCollection() fonksiyonları
 * ZATEN VARDI (Faz 5 Dalga 3A) — bu sheet SADECE bunlara bir UI ekliyor,
 * repository'de yeni bir şey YOK.
 *
 * PostShareSheet.kt'deki AYNI desen: ViewModel'e BAĞLANMADAN doğrudan
 * ServiceLocator üzerinden repository kullanılır (sheet kısa ömürlü, kendi
 * başına yeterli) — FeedViewModel/DiscoverViewModel/ProfileViewModel'in
 * HANGİSİ çağırdığına bakılmaksızın TEK bir composable 4 ekranda da reuse
 * edilebilir.
 *
 * [alreadyBookmarked] true ise post zaten Genel'e (veya başka bir koleksiyona)
 * kaydedilmiş demektir — bu durumda bir koleksiyona dokunmak
 * `setBookmarkCollection` (SADECE koleksiyonu değiştirir, bookmark'ı
 * KALDIRMAZ) çağırır; false ise `toggleBookmark(postId, collectionId)`
 * (postu O koleksiyona doğrudan kaydeder) çağrılır — görev tanımındaki
 * "post zaten kaydedilmişse setBookmarkCollection" ayrımı budur.
 *
 * [onBookmarked] PostCard'ın KENDİ İÇİNDEKİ yerel bir görsel state'i
 * (bookmark ikonunun dolu/boş rengi) güncellemek için — repository çağrısı
 * doğrudan ViewModel'in posts listesini GÜNCELLEMEDİĞİ için (ViewModel'e
 * BAĞLANMAYAN bu sheet'in bilinçli tasarımı, yukarıdaki yorum), bu callback
 * OLMADAN ikon bir sonraki tam yenilemeye kadar eski durumda görünürdü.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkCollectionPickerSheet(
    postId: String,
    alreadyBookmarked: Boolean,
    onDismiss: () -> Unit,
    onSessionExpired: () -> Unit,
    onBookmarked: (Boolean) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarksRepository = ServiceLocator.bookmarksRepository
    val tokenStore = ServiceLocator.tokenStore

    var collections by remember { mutableStateOf<List<CollectionDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val result = bookmarksRepository.getCollections()) {
            is CollectionsResult.Success -> collections = result.collections
            is CollectionsResult.Error -> {
                if (result.code == "unauthorized") {
                    tokenStore.clearToken()
                    onSessionExpired()
                }
                // diğer hatalarda sessizce yutulur - "Genel" seçeneği yine de
                // çalışır, koleksiyon listesi boş kalır.
            }
        }
        loading = false
    }

    // Görev tanımındaki ayrım: post ZATEN kaydedilmişse setBookmarkCollection
    // (SADECE hangi koleksiyona ait olduğunu değiştirir), DEĞİLSE
    // toggleBookmark(postId, collectionId) (doğrudan o koleksiyona kaydeder).
    fun assignToCollection(collectionId: String?) {
        scope.launch {
            if (alreadyBookmarked) {
                when (val result = bookmarksRepository.setBookmarkCollection(postId, collectionId)) {
                    is SetBookmarkCollectionResult.Success -> {
                        onBookmarked(true)
                        onDismiss()
                    }
                    is SetBookmarkCollectionResult.Error -> {
                        if (result.code == "unauthorized") {
                            tokenStore.clearToken()
                            onSessionExpired()
                        } else {
                            Toast.makeText(context, "İşlem başarısız, tekrar dene", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                when (val result = bookmarksRepository.toggleBookmark(postId, collectionId)) {
                    is ToggleBookmarkResult.Success -> {
                        onBookmarked(result.bookmarked)
                        onDismiss()
                    }
                    is ToggleBookmarkResult.Error -> {
                        if (result.code == "unauthorized") {
                            tokenStore.clearToken()
                            onSessionExpired()
                        } else {
                            Toast.makeText(context, "İşlem başarısız, tekrar dene", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Kaydet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // "Genel" (collection_id=null) — görev tanımı gereği HER ZAMAN
                // İLK sırada, koleksiyon listesinin bir PARÇASI değil.
                item {
                    CollectionPickerRow(
                        icon = Icons.Filled.Bookmark,
                        label = "Genel",
                        onClick = { assignToCollection(null) },
                    )
                }
                items(collections, key = { it.id }) { collection ->
                    CollectionPickerRow(
                        icon = Icons.Filled.Folder,
                        label = collection.name,
                        onClick = { assignToCollection(collection.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    CollectionPickerRow(
                        icon = Icons.Filled.Add,
                        label = "Yeni Koleksiyon",
                        onClick = { showCreateDialog = true },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!creating) showCreateDialog = false },
            title = { Text("Yeni koleksiyon") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    placeholder = { Text("Koleksiyon adı") },
                    singleLine = true,
                    enabled = !creating,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newCollectionName.trim()
                        if (name.isEmpty() || creating) return@TextButton
                        creating = true
                        scope.launch {
                            when (val result = bookmarksRepository.createCollection(name)) {
                                is CreateCollectionResult.Success -> {
                                    collections = collections + CollectionDto(id = result.id, name = result.name)
                                    creating = false
                                    showCreateDialog = false
                                    newCollectionName = ""
                                    // Başarılı oluşturma sonrası YENİ koleksiyona
                                    // hemen kaydet (görev tanımı: "oluşturulan
                                    // koleksiyona kaydedilir").
                                    assignToCollection(result.id)
                                }
                                is CreateCollectionResult.Error -> {
                                    creating = false
                                    if (result.code == "unauthorized") {
                                        tokenStore.clearToken()
                                        showCreateDialog = false
                                        onSessionExpired()
                                    } else {
                                        Toast.makeText(context, "Koleksiyon oluşturulamadı", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = !creating,
                ) {
                    Text(if (creating) "Oluşturuluyor…" else "Oluştur")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!creating) showCreateDialog = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun CollectionPickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
