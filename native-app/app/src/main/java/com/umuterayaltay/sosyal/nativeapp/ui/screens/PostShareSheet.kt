package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.SharePostResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Gönder" (post paylaşımı) hedef seçici — görev tanımı ForwardTargetScreen'in
 * (mesaj iletme) REUSE edilmesini istiyordu, ama BİLİNÇLİ SAPMA: o ekran
 * GET /messages/forward-targets'ı kullanıyor ve bu uç nokta KONUŞMA id'si
 * döndürüyor (bkz. SharesApi.kt yorumu), oysa POST /posts/{id}/share GERÇEK
 * kullanıcı id'si (user_ids) bekliyor — ikisi birbirinin yerine geçmez.
 * Bu yüzden hedef seçimi burada DiscoverRepository'nin ZATEN var olan kullanıcı
 * aramasından (DiscoverScreen'deki AYNI arama, gerçek profil id'leri) yapılır;
 * satır GÖRÜNÜMÜ ForwardTargetScreen'deki ile AYNI dilde (UserRow REUSE edilir,
 * yeni bir liste bileşeni İCAT EDİLMEDİ), SADECE veri kaynağı ve çoklu-seçim
 * (checkbox) farklı.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostShareSheet(
    postId: String,
    onDismiss: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discoverRepository = ServiceLocator.discoverRepository
    val sharesRepository = ServiceLocator.sharesRepository
    val tokenStore = ServiceLocator.tokenStore

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserSearchDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var note by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // DiscoverScreen'deki AYNI ~400ms debounce gerekçesi — her tuş vuruşunda
    // backend bombalanmasın (burada ayrı bir ViewModel/StateFlow debounce
    // İCAT EDİLMEDİ, sheet kısa ömürlü olduğu için basit bir LaunchedEffect(query) yeterli).
    LaunchedEffect(query) {
        val q = query
        if (q.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        when (val result = discoverRepository.search(q = q, type = "users", dateFrom = null, dateTo = null)) {
            is SearchResult.Success -> results = result.data.users
            is SearchResult.Error -> {
                if (result.code == "unauthorized") {
                    tokenStore.clearToken()
                    onSessionExpired()
                }
                results = emptyList()
            }
        }
        searching = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(text = "Postu paylaş", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                placeholder = { Text("Kullanıcı ara") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )

            when {
                searching -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                query.length < 2 -> Text(
                    text = "Paylaşmak için en az 2 harf yaz",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                results.isEmpty() -> Text(
                    text = "Kullanıcı bulunamadı",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(results, key = { it.id }) { user ->
                        val isSelected = selectedIds.contains(user.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isSelected) {
                                        selectedIds - user.id
                                    } else {
                                        selectedIds + user.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                UserRow(avatarUrl = user.avatarUrl, username = user.username, fullName = user.fullName)
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text("Not ekle (opsiyonel)") },
                singleLine = true,
            )

            Button(
                onClick = {
                    if (selectedIds.isEmpty() || sending) return@Button
                    sending = true
                    scope.launch {
                        when (val result = sharesRepository.sharePost(postId, selectedIds.toList(), note)) {
                            is SharePostResult.Success -> {
                                Toast.makeText(context, "Paylaşıldı (${result.sentCount})", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            is SharePostResult.Error -> {
                                if (result.code == "unauthorized") {
                                    tokenStore.clearToken()
                                    onSessionExpired()
                                } else {
                                    Toast.makeText(context, mapShareError(result.code), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        sending = false
                    }
                },
                enabled = selectedIds.isNotEmpty() && !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            ) {
                Text(
                    if (sending) "Gönderiliyor…"
                    else if (selectedIds.isEmpty()) "Gönder"
                    else "Gönder (${selectedIds.size})"
                )
            }
        }
    }
}

private fun mapShareError(code: String?): String = when (code) {
    "no_recipients" -> "En az bir kişi seç"
    "not_found" -> "Gönderi bulunamadı"
    "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
    else -> "Paylaşılamadı, lütfen tekrar dene"
}
