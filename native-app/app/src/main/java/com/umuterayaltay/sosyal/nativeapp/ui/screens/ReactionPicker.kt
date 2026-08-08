package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Web'in kullandığı emoji setiyle tutarlı, küçük bir sabit tepki listesi
 * (Faz 5 Dalga 1B — app/api_v1/messaging.py api_react_message() `reaction`
 * alanında SERBEST metin kabul ediyor, backend bir emoji whitelist'i
 * ZORLAMIYOR; burada native tarafın önerdiği set sadece UI kolaylığı,
 * backend'de ayrıca bir doğrulama YOK). */
private val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "👍", "👎")

/**
 * Animasyon turu (2026-08-08): seçici açılırken her emoji hafif gecikmeli
 * (index * 25ms) scale+fade ile "patlayarak" belirir — "popup açılış hissi".
 * [ReactionPicker] MessageActionsSheet/PostDetailScreen'de bir DropdownMenu/
 * sheet İÇİNDE her açıldığında YENİDEN composition'a girdiği için (parent
 * showX state'i false→true olunca sıfırdan kurulur), aşağıdaki `visible`
 * state'i her açılışta baştan false başlar — kapanışta parent zaten
 * composable'ı kaldırdığı için exit geçişi ayrıca TETİKLENMEK ZORUNDA değil,
 * ama yine de eklendi (ileride bir AnimatedVisibility ile sarmalanırsa
 * kapanış da düzgün oynar).
 */
@Composable
fun ReactionPicker(
    modifier: Modifier = Modifier,
    onReactionSelected: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        REACTION_EMOJIS.forEachIndexed { index, emoji ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(durationMillis = 160, delayMillis = index * 25)) +
                    scaleIn(
                        initialScale = 0.4f,
                        animationSpec = tween(durationMillis = 160, delayMillis = index * 25),
                    ),
                exit = fadeOut(animationSpec = tween(durationMillis = 100)) +
                    scaleOut(targetScale = 0.4f, animationSpec = tween(durationMillis = 100)),
            ) {
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
}
