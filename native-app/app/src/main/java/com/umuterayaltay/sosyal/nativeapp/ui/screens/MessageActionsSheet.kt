package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import kotlinx.coroutines.delay

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

    // Animasyon turu (3. kısım) — sheet'in kendisi Material3'ün varsayılan
    // slide-up+scrim animasyonunu ZATEN kullanıyor (ModalBottomSheet), o
    // DEĞİŞTİRİLMEDİ. Ek olarak İÇERİK satırları küçük bir stagger (30ms
    // aralıklı, sağdan hafif kayarak) ile beliriyor — "hissedilir ama
    // abartısız" polish. actionIndex sıralı çağrılara göre elle artırılıyor
    // (satırlar koşullu olduğu için itemsIndexed benzeri bir liste yok).
    var actionIndex = 0
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ActionRow(icon = Icons.AutoMirrored.Filled.Reply, label = "Yanıtla", onClick = onReply, entranceIndex = actionIndex++)
        if (isMine && isTextOnly) {
            ActionRow(icon = Icons.Filled.Edit, label = "Düzenle", onClick = onEdit, entranceIndex = actionIndex++)
        }
        if (isMine) {
            ActionRow(icon = Icons.Filled.Delete, label = "Sil", onClick = onDelete, entranceIndex = actionIndex++)
        }
        ActionRow(icon = Icons.AutoMirrored.Filled.Send, label = "İlet", onClick = onForward, entranceIndex = actionIndex++)
        ActionRow(
            icon = Icons.Filled.PushPin,
            label = if (isPinned) "Sabitlemeyi kaldır" else "Sabitle",
            onClick = onPin,
            entranceIndex = actionIndex++,
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
    entranceIndex: Int = 0,
) {
    val visibleState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        delay(entranceIndex * 30L)
        visibleState.targetState = true
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 6 },
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
}
