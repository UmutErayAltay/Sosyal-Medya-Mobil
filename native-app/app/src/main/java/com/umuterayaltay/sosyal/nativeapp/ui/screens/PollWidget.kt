package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umuterayaltay.sosyal.nativeapp.repository.Poll

/**
 * Anket widget'ı — web'in `_post_card.html` `poll_widget` makrosunun (satır
 * 143-159) Compose karşılığı: her seçenek bir buton, arkasında `pct` kadar
 * genişlikte bir bar, kullanıcının oy verdiği seçenek vurgulu, altta
 * "{total_votes} oy".
 *
 * Tıklama = oy ver / oyu değiştir / oyu geri al — backend TEK uçta 3-yönlü
 * toggle uygular (app/api_v1/polls.py), client hangi durumda olduğunu bilmek
 * ZORUNDA DEĞİL. Optimistic UI YOK: yüzdeler sunucudan geldiği gibi gösterilir
 * (bkz. PollsRepository sınıf yorumu).
 *
 * Renkler MaterialTheme.colorScheme token'larından; hardcoded renk YOK
 * (ui/theme/ tek doğruluk kaynağı).
 */
@Composable
fun PollWidget(
    poll: Poll,
    onVote: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (option in poll.options) {
            val voted = poll.myVote == option.id
            // Bar genişliği DEĞİŞİMDE animasyonlu: oy verilince yeni yüzdeye kayar.
            val fraction by animateFloatAsState(
                targetValue = option.pct.coerceIn(0, 100) / 100f,
                label = "pollBar",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (voted) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onVote(option.id) }
                    .semantics {
                        contentDescription =
                            "${option.text}, yüzde ${option.pct}, ${option.votes} oy" +
                                if (voted) ", oyunuz" else ""
                    },
            ) {
                // Dolgu barı: matchParentSize() ile kutunun (metnin belirlediği)
                // yüksekliğini alır — fillMaxHeight() tek başına burada ÇALIŞMAZ,
                // çünkü Box'un yüksekliği sarmalayan (bounded olmayan) bir
                // kısıtla ölçülüyor. Önce bildirildiği için metnin ALTINDA çizilir.
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(
                                if (voted) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (voted) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "%${option.pct}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (voted) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Text(
            text = "${poll.totalVotes} oy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
