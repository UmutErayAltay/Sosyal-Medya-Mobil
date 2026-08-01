package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto

/**
 * Mesaj balonuna uzun basınca açılan aksiyon menüsü — ConversationScreen'in
 * eskiden TEK aksiyonu (uzun-bas = yanıtla) olan combinedClickable'ının
 * yerine geçer (Faz 5 Dalga 1B, native Android). [message]'ın kendi alanları
 * hangi satırların gösterileceğine karar verir:
 * - Düzenle/Sil: SADECE [isMine] (backend zaten sender_id=me filtresiyle
 *   uyguluyor, burada AYRICA görünürlük kontrolü yapılır ki kullanıcı
 *   deneyip 404/403 almasın).
 * - Düzenle AYRICA: sadece SALT-METİN mesajda (görsel/sticker'da yok — ek
 *   ASLA değişmez, bkz. app/api_v1/messaging.py api_edit_message() docstring'i).
 * - Sabitle: yetki GÖNDEREN değil konuşmanın HERHANGİ bir katılımcısı, bu
 *   yüzden isMine şartı ARANMAZ.
 * - Yanıtla/İlet/Tepki ver: her zaman görünür.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: MessageDto,
    isMine: Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onReact: (String) -> Unit,
) {
    // Görsel/sticker eklisi olan mesajda düzenleme YOK (ek asla değişmez) —
    // MessageDto şu an audio_url modellemiyor, bu yüzden sadece imageUrl/
    // sticker kontrolü yeterli (backend'deki AYNI kural).
    val isTextOnly = message.imageUrl.isNullOrBlank() && message.sticker == null
    val isPinned = message.pinnedAt != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ActionRow(icon = Icons.AutoMirrored.Filled.Reply, label = "Yanıtla", onClick = onReply)
        if (isMine && isTextOnly) {
            ActionRow(icon = Icons.Filled.Edit, label = "Düzenle", onClick = onEdit)
        }
        if (isMine) {
            ActionRow(icon = Icons.Filled.Delete, label = "Sil", onClick = onDelete)
        }
        ActionRow(icon = Icons.AutoMirrored.Filled.Send, label = "İlet", onClick = onForward)
        ActionRow(
            icon = Icons.Filled.PushPin,
            label = if (isPinned) "Sabitlemeyi kaldır" else "Sabitle",
            onClick = onPin,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEmotions,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Tepki ver",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        ReactionPicker(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            onReactionSelected = onReact,
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
