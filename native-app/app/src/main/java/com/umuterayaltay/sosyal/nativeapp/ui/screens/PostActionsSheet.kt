package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.repository.Post

/**
 * Bir postun overflow (üç-nokta) menüsü — PostCard.kt'nin ŞU ANA KADAR hiç
 * aksiyon sheet'i YOKTU, bu Faz 5 Dalga 2A'da SIFIRDAN kuruldu.
 *
 * BİLİNÇLİ OLARAK GENİŞLETİLEBİLİR TASARLANDI: bu ilk turda İÇİNDE SADECE
 * "Postu Sessize Al / Sesini Aç" var, ama GELECEKTE (Dalga 3) Bookmark/Şikayet/
 * Sil buraya EKLENECEK — o yüzden imza şimdiden `onMutePost` DIŞINDA parametre
 * alabilecek şekilde bırakıldı (yorum aşağıda). Yeni bir aksiyon eklenince
 * sadece burada yeni bir satır + yeni bir opsiyonel callback parametresi
 * eklenir, çağıran taraflar (PostCard) DEĞİŞMEDEN derlenmeye devam eder
 * (PostCard.onMutePost'un VARSAYILAN DEĞERLİ olması ile AYNI gerekçe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostActionsSheet(
    post: Post,
    onMutePost: (String) -> Unit,
    onDismiss: () -> Unit,
    // Gelecekte (Dalga 3): onBookmark: (String) -> Unit = {}, onReport: (String) -> Unit = {}, onDelete: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        PostActionItem(
            icon = if (post.mutedByMe) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
            label = if (post.mutedByMe) "Postu Sesini Aç" else "Postu Sessize Al",
            onClick = {
                onMutePost(post.id)
                onDismiss()
            },
        )
    }
}

@Composable
private fun PostActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
