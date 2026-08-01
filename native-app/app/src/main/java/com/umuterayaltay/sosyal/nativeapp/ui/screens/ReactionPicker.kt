package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Web'in kullandığı emoji setiyle tutarlı, küçük bir sabit tepki listesi
 * (Faz 5 Dalga 1B — app/api_v1/messaging.py api_react_message() `reaction`
 * alanında SERBEST metin kabul ediyor, backend bir emoji whitelist'i
 * ZORLAMIYOR; burada native tarafın önerdiği set sadece UI kolaylığı,
 * backend'de ayrıca bir doğrulama YOK). */
private val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "👍", "👎")

@Composable
fun ReactionPicker(
    modifier: Modifier = Modifier,
    onReactionSelected: (String) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        REACTION_EMOJIS.forEach { emoji ->
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onReactionSelected(emoji) }
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .padding(8.dp),
            )
        }
    }
}
