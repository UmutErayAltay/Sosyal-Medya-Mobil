package com.umuterayaltay.sosyal.nativeapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Tam ekran, sıkıştırma-yakınlaştırma (pinch-zoom) + kaydırma (pan) destekli
 * görsel görüntüleyici (2026-08-09, kullanıcı isteği: "fotoğraflara tıklayınca
 * büyüsün"). Projede ZATEN var olan bir zoom kütüphanesi YOKTU (`build.gradle.kts`
 * kontrol edildi) — yeni bir bağımlılık EKLEMEK yerine (projenin genel disiplini,
 * bkz. WebRtcCallManager.kt "KTX modülü BİLİNÇLİ olarak eklenmedi" yorumu)
 * Compose'un KENDİ `detectTransformGestures` API'siyle elle yazıldı.
 *
 * BİLİNÇLİ SINIR: çift-dokunuşla yakınlaştırma (double-tap-to-zoom) YOK —
 * `detectTapGestures` + `detectTransformGestures`'ı AYNI Box'a iki ayrı
 * `pointerInput` olarak eklemek jest çakışmasına (ikisi de aynı pointer
 * event akışını tüketmeye çalışır) yol açabilirdi, bu yüzden kapatma SADECE
 * açık "X" butonu + sistem geri tuşuyla (BackHandler) yapılıyor — pinch-zoom
 * jesti YALNIZ BAŞINA, çakışmasız çalışıyor.
 *
 * Hem `PostCard.kt` (feed/profil/keşfet/post detayı AYNI component) hem
 * `ConversationScreen.kt` (sohbet balonundaki görsel) tarafından kullanılır —
 * MediaPickerSheet.kt ile AYNI "gerçek paylaşılan bileşen" kararı (küçük
 * yardımcıların aksine, bkz. o dosyanın da bu paket altında olması).
 */
@Composable
fun FullscreenImageViewer(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)

        var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
        var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageUrl) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            // 1x'e dönünce kaydırma da SIFIRLANIR — aksi halde
                            // yakınlaştırmayı bırakınca görsel kadraj DIŞINDA
                            // kalmış gibi görünebilirdi.
                            offset = if (newScale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(40.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = Color.White)
            }
        }
    }
}
