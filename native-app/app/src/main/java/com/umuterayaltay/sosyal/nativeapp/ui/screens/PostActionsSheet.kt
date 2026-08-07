package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
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
 * PostCard aksiyon satırı yeniden düzenlemesi (kullanıcı isteği, Instagram
 * tarzı düzen): "Kaydet"/"Yeniden Paylaş"/"Gönder" artık PostCard'ın aksiyon
 * SATIRINDA doğrudan görünür ikonlar (bkz. PostCard.kt) — bu sheet'ten
 * KALDIRILDI, onBookmark/onRepost/onShare parametreleri de bu yüzden
 * TAMAMEN kaldırıldı (artık hiçbir çağrı yeri bunları geçmiyor). Bu menüde
 * SADECE "Postu Sessize Al / Sesini Aç" ve "Bildir" kalıyor.
 *
 * "Bildir" web'deki basit confirm-dialog'un (data-confirm) native karşılığı
 * olarak AlertDialog ile onay ister (sebep seçimi YOK, backend'de zaten
 * reason alanı yok) ve KIRMIZI/error tonunda gösterilir — web'de de bu
 * aksiyon dikkat çekici renktedir.
 *
 * Post yönetimi (düzenle/sil/arşivle/sabitle) — kendi postun için Mute/Report
 * anlamsız (kendini susturamaz/bildiremezsin), bu yüzden [isOwn] true iken
 * MENÜ TAMAMEN DEĞİŞİR: Mute/Report yerine Düzenle/Sabitle-Kaldır/Arşivle/Sil
 * gösterilir. Silme, Bildir'le AYNI "geri dönüşü olmayan aksiyon" felsefesiyle
 * AlertDialog ile onaylatılır. Düzenleme burada bir metin alanı GÖSTERMEZ —
 * [onEdit] sadece dismiss+sinyal verir, gerçek düzenleme diyaloğu PostCard'ın
 * kendi (post içeriğini zaten elinde tutan) katmanında açılır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionsSheet(
    post: Post,
    onMutePost: (String) -> Unit,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit = {},
    isOwn: Boolean = false,
    onEdit: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onToggleArchive: (String) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    var showReportConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (isOwn) {
            PostActionItem(
                icon = Icons.Filled.Edit,
                label = "Düzenle",
                onClick = {
                    onEdit(post.id)
                    onDismiss()
                },
            )
            PostActionItem(
                icon = Icons.Filled.PushPin,
                label = "Profilde Sabitle / Kaldır",
                onClick = {
                    onTogglePin(post.id)
                    onDismiss()
                },
            )
            PostActionItem(
                icon = Icons.Filled.Archive,
                label = "Arşivle / Arşivden Çıkar",
                onClick = {
                    onToggleArchive(post.id)
                    onDismiss()
                },
            )
            PostActionItem(
                icon = Icons.Filled.Delete,
                label = "Sil",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showDeleteConfirm = true },
            )
        } else {
            PostActionItem(
                icon = if (post.mutedByMe) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                label = if (post.mutedByMe) "Postu Sesini Aç" else "Postu Sessize Al",
                onClick = {
                    onMutePost(post.id)
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
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Postu sil") },
            text = { Text("Bu postu silmek istediğine emin misin? Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(post.id)
                        onDismiss()
                    },
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Vazgeç") }
            },
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
