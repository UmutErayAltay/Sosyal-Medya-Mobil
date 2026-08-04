package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.repository.Post

/**
 * Bir postun overflow (üç-nokta) menüsü — PostCard.kt'nin ŞU ANA KADAR hiç
 * aksiyon sheet'i YOKTU, bu Faz 5 Dalga 2A'da SIFIRDAN kuruldu.
 *
 * BİLİNÇLİ OLARAK GENİŞLETİLEBİLİR TASARLANDI: Dalga 2A'da SADECE "Postu
 * Sessize Al / Sesini Aç" vardı, Dalga 3A'da "Kaydet / Kaydedildi" eklendi,
 * Faz 5 sonrası eksik giderme turunda "Yeniden Paylaş"/"Gönder"/"Bildir"
 * eklendi (Sil hâlâ kapsam dışı) — o yüzden imza şimdiden yeni parametreler
 * alabilecek şekilde bırakıldı. Yeni 3 callback de VARSAYILAN DEĞERLİ ({}) —
 * onMutePost/onBookmark'ın PostCard'daki AYNI gerekçesiyle, henüz bağlanmamış
 * çağrı yerleri DEĞİŞMEDEN derlenmeye devam eder. İlk turda collection_id
 * her zaman null (Genel'e kaydedilir) — koleksiyon seçimi kaydedilenler
 * ekranında yapılır, bu basit toggle'a KARIŞTIRILMAZ.
 *
 * "Yeniden Paylaş" bu turda alıntı-yorumsuz (Twitter retweet gibi) hızlı
 * repost — backend content'siz repost'u destekliyor, alıntılı repost UI'ı
 * kapsam dışı. "Bildir" web'deki basit confirm-dialog'un (data-confirm)
 * native karşılığı olarak AlertDialog ile onay ister (sebep seçimi YOK,
 * backend'de zaten reason alanı yok) ve KIRMIZI/error tonunda gösterilir —
 * web'de de bu aksiyon dikkat çekici renktedir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionsSheet(
    post: Post,
    onMutePost: (String) -> Unit,
    onDismiss: () -> Unit,
    onBookmark: (String) -> Unit = {},
    onRepost: (String) -> Unit = {},
    onShare: (String) -> Unit = {},
    onReport: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    var showReportConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PostActionItem(
            icon = if (post.bookmarkedByMe) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            label = if (post.bookmarkedByMe) "Kaydedildi" else "Kaydet",
            onClick = {
                onBookmark(post.id)
                onDismiss()
            },
        )
        PostActionItem(
            icon = if (post.mutedByMe) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
            label = if (post.mutedByMe) "Postu Sesini Aç" else "Postu Sessize Al",
            onClick = {
                onMutePost(post.id)
                onDismiss()
            },
        )
        PostActionItem(
            icon = Icons.Filled.Repeat,
            label = "Yeniden Paylaş",
            onClick = {
                onRepost(post.id)
                onDismiss()
            },
        )
        PostActionItem(
            icon = Icons.AutoMirrored.Filled.Send,
            label = "Gönder",
            onClick = {
                onShare(post.id)
                onDismiss()
            },
        )
        PostActionItem(
            icon = Icons.Filled.Flag,
            label = "Bildir",
            tint = MaterialTheme.colorScheme.error,
            onClick = { showReportConfirm = true },
        )
    }

    // Sheet KAPANDIKTAN sonra değil, ÜSTÜNDE gösterilir (ModalBottomSheet zaten
    // ayrı bir pencere/overlay) — web'deki data-confirm ile AYNI "geri dönüşü
    // olmayan bir aksiyondan önce bir kez daha sor" felsefesi.
    if (showReportConfirm) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            icon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Gönderiyi bildir") },
            text = { Text("Bu gönderiyi şikayet etmek istediğine emin misin?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReportConfirm = false
                        onReport(post.id)
                        onDismiss()
                    },
                ) {
                    Text("Bildir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirm = false }) { Text("Vazgeç") }
            },
        )
    }
}

@Composable
private fun PostActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    // "Bildir" gibi dikkat çekici aksiyonlar için — varsayılan diğer aksiyonlarla
    // AYNI nötr onSurfaceVariant tonu.
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val resolvedTint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = resolvedTint.takeIf { tint != null } ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
