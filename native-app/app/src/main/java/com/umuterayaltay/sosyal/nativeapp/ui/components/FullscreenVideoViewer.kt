package com.umuterayaltay.sosyal.nativeapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Tam ekran video oynatıcı (2026-08-09, kullanıcı isteği: "videolar içinde
 * geçerli aynı şey, sohbetteki videoya tıklayınca büyüsün") — [PostCard.kt]
 * `PostVideoPlayer`'ının/`ConversationScreen.kt` `MessageVideoPlayer`'ının
 * AYNI ExoPlayer deseni (composable disposed olunca `release()`), ama tam
 * ekran + otomatik oynatma (`playWhenReady = true`) İLE. Görsellerin AKSİNE
 * (bkz. FullscreenImageViewer.kt) pinch-zoom YOK — hiçbir mainstream
 * uygulama video mesajını/postunu pinch-zoom'la büyütmüyor, tam ekran
 * oynatma zaten "büyüsün" beklentisini karşılıyor.
 */
@Composable
fun FullscreenVideoViewer(videoUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onDismiss)

        val context = LocalContext.current
        val exoPlayer = remember(videoUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                playWhenReady = true
                prepare()
            }
        }
        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
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
