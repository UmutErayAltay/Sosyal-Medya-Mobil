package com.umuterayaltay.sosyal.nativeapp.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Kullanici satiri - DiscoverScreen'in eski private UserResultRow'undan
 * CIKARILDI (kopyalanmadi): hem Kesfet arama sonuclari (UserSearchDto) hem
 * FollowListScreen (FollowUserDto) farkli DTO'lar kullaniyor ama GORUNUM
 * (avatar + kullanici adi + tam ad, tiklanabilir) ayni - o yuzden ortak bir
 * composable'a alindi, iki ayri DTO-spesifik sarmalayici bunu cagirir.
 *
 * Animasyon turu: tiklanabilir satirlar basilinca HAFIFCE kucultuluyor
 * (interactionSource.collectIsPressedAsState + spring) - imza DEGISMEDI,
 * SADECE ic gorsel geri bildirim eklendi (cagiran taraflar farkina varmaz).
 */
@Composable
fun UserRow(
    avatarUrl: String?,
    username: String?,
    fullName: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "user-row-press-scale",
    )

    Row(
        modifier = (
            if (onClick != null) {
                modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
            } else {
                modifier
            }
            )
            .scale(if (onClick != null) pressScale else 1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            // Avatarsız kullanıcılar için de daire çerçeve — liste taranırken
            // avatarlı/avatarsız satırlar arasında hizalama sıçraması olmaz.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = username ?: "bilinmeyen", style = MaterialTheme.typography.titleSmall)
            if (!fullName.isNullOrBlank()) {
                Text(
                    text = fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
