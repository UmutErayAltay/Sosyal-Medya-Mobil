package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.umuterayaltay.sosyal.nativeapp.repository.CallEndReason
import com.umuterayaltay.sosyal.nativeapp.repository.CallPhase
import com.umuterayaltay.sosyal.nativeapp.viewmodel.OneOnOneCallViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.OneOnOneCallViewModelFactory
import io.getstream.webrtc.android.compose.VideoRenderer
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

/**
 * 1:1 WebRTC sesli/görüntülü arama ekranı — LiveKit'in CallScreen.kt'siyle
 * (grup araması) AYNI görsel dili (dolgu renkler, ikon seçimi, kontrol
 * çubuğu düzeni) paylaşır ama BAĞIMSIZ bir composable ağacı, LiveKit'e HİÇ
 * bağımlılık YOK (görev spesifikasyonu). Gerçek durum HER ZAMAN
 * [OneOnOneCallViewModel] üzerinden [CallSessionManager]'da (ServiceLocator
 * singleton) — bu ekran sadece o durumu render eder + kullanıcı aksiyonlarını
 * (kabul/reddet burada DEĞİL, gelen-arama overlay'inde — bkz. AppNavHost.kt;
 * mikrofon/kamera/kapat BURADA) iletir.
 *
 * İki çağrı yeri (AppNavHost.kt):
 *  - Arayan: "oneOnOneCall/{conversationId}/{otherUserId}/{otherCallTopic}/{isVideo}/..."
 *    — conversationId/otherUserId/otherCallTopic NON-NULL, ekran açılır
 *    açılmaz startOutgoingCall() tetiklenir.
 *  - Aranan (kabul sonrası): "oneOnOneCallIncoming" — conversationId/otherUserId
 *    NULL, ekran izin verildikten SONRA acceptIncoming() tetikler (offer/otherId
 *    zaten CallSessionManager'da bekliyor — bkz. o sınıfın pendingOfferSdp'si).
 */
@Composable
fun OneOnOneCallScreen(
    conversationId: String? = null,
    otherUserId: String? = null,
    // Hedefin arama kanalı adı — SADECE arayan tarafta (nav route'tan) gelir,
    // aranan tarafta IncomingRinging fazından zaten biliniyor (bkz. AppNavHost
    // "oneOnOneCallIncoming" rotası, argümansız).
    otherCallTopic: String? = null,
    isVideo: Boolean = false,
    otherName: String? = null,
    otherAvatar: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: OneOnOneCallViewModel = viewModel(factory = OneOnOneCallViewModelFactory()),
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()
    val localVideoTrack by viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isMicEnabled by viewModel.isMicEnabled.collectAsState()
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsState()

    val isCallerMode = conversationId != null && otherUserId != null && otherCallTopic != null

    // Kamera + ses izin durumu — CallScreen.kt'deki (LiveKit) AYNI desen.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }
    val hasAllPermissions = hasCameraPermission && hasAudioPermission
    LaunchedEffect(Unit) {
        if (!hasAllPermissions) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(hasAllPermissions, phase) {
        if (!hasAllPermissions) return@LaunchedEffect
        if (isCallerMode) {
            viewModel.startOutgoingCall(
                context = context,
                conversationId = conversationId!!,
                otherUserId = otherUserId!!,
                otherCallTopic = otherCallTopic!!,
                isVideo = isVideo,
                otherName = otherName ?: "Kullanıcı",
                otherAvatar = otherAvatar,
            )
        } else if (phase is CallPhase.IncomingRinging) {
            viewModel.acceptIncoming(context)
        }
    }

    // Ended fazı kısa süre gösterilip otomatik geri dönülür — web'in
    // endCall() sonrası ANINDA idle'ına karşılık, burada kullanıcı NEDEN
    // kapandığını (reddedildi/cevapsız/bağlantı sorunu) kısaca görebilsin
    // diye bilinçli bir ara adım (MVP kararı, rapor edildi).
    LaunchedEffect(phase) {
        val currentPhase = phase
        if (currentPhase is CallPhase.Ended) {
            delay(1400)
            viewModel.resetToIdle()
            onNavigateBack()
        } else if (currentPhase is CallPhase.Idle && !isCallerMode) {
            onNavigateBack()
        }
    }

    BackHandler(enabled = true) {
        val currentPhase = phase
        if (currentPhase is CallPhase.Active || currentPhase is CallPhase.OutgoingRinging) {
            viewModel.hangup()
        }
        onNavigateBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            !hasAllPermissions -> PermissionBlockedContent(
                onRequestPermission = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                onClose = onNavigateBack,
            )
            else -> when (val currentPhase = phase) {
                is CallPhase.Idle -> Unit // yukarıdaki LaunchedEffect zaten geri dönüyor
                is CallPhase.OutgoingRinging -> OutgoingRingingContent(
                    name = currentPhase.otherName,
                    avatarUrl = currentPhase.otherAvatar,
                    onCancel = {
                        viewModel.hangup()
                        onNavigateBack()
                    },
                )
                is CallPhase.IncomingRinging -> ConnectingContent()
                is CallPhase.Active -> ActiveCallContent(
                    phaseData = currentPhase,
                    localVideoTrack = localVideoTrack,
                    remoteVideoTrack = remoteVideoTrack,
                    isMicEnabled = isMicEnabled,
                    isCameraEnabled = isCameraEnabled,
                    eglBaseContext = viewModel.eglBaseContext(),
                    onToggleMic = viewModel::toggleMic,
                    onToggleCamera = viewModel::toggleCamera,
                    onEndCall = {
                        viewModel.hangup()
                        onNavigateBack()
                    },
                )
                is CallPhase.Ended -> EndedContent(reason = currentPhase.reason)
            }
        }
    }
}

@Composable
private fun PermissionBlockedContent(onRequestPermission: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Kamera ve mikrofon izni gerekli",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Sesli/görüntülü aramaya katılmak için kamera ve mikrofon iznini verin.",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Button(onClick = onRequestPermission) { Text("İzin Ver") }
        OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
            Text("Vazgeç", color = Color.White)
        }
    }
}

@Composable
private fun ConnectingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = "Bağlanıyor...",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun EndedContent(reason: CallEndReason) {
    val message = when (reason) {
        CallEndReason.REJECTED -> "Arama reddedildi"
        CallEndReason.NO_ANSWER -> "Cevap yok"
        CallEndReason.FAILED -> "Bağlantı kurulamadı"
        CallEndReason.ERROR -> "Arama başlatılamadı"
        CallEndReason.REMOTE_HANGUP, CallEndReason.LOCAL_HANGUP -> "Arama sona erdi"
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun OutgoingRingingContent(name: String, avatarUrl: String?, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RemoteAvatar(avatarUrl = avatarUrl, size = 96.dp)
        Text(
            text = name,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Aranıyor...",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )
        IconButton(
            onClick = onCancel,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White,
            ),
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Aramayı iptal et")
        }
    }
}

@Composable
private fun RemoteAvatar(avatarUrl: String?, size: androidx.compose.ui.unit.Dp) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

private val noopRendererEvents = object : RendererCommon.RendererEvents {
    override fun onFirstFrameRendered() {}
    override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) {}
}

@Composable
private fun ActiveCallContent(
    phaseData: CallPhase.Active,
    localVideoTrack: VideoTrack?,
    remoteVideoTrack: VideoTrack?,
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    eglBaseContext: org.webrtc.EglBase.Context?,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: () -> Unit,
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(phaseData.startedAtMs) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - phaseData.startedAtMs
            delay(1000)
        }
    }
    val totalSec = elapsedMs / 1000
    val durationText = "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            // Karşı tarafın videosu tam ekran (görüntülü aramada VE gerçekten
            // bir remote video track geldiyse) — sesli aramada / kamera henüz
            // gelmemişse gerçek avatar + ad + süre gösterilir (web'in AYNI
            // davranışı, bkz. call.js showCallUI()).
            if (phaseData.isVideo && remoteVideoTrack != null && eglBaseContext != null) {
                VideoRenderer(
                    videoTrack = remoteVideoTrack,
                    eglBaseContext = eglBaseContext,
                    rendererEvents = noopRendererEvents,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RemoteAvatar(avatarUrl = phaseData.otherAvatar, size = 96.dp)
                    Text(
                        text = phaseData.otherName,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }

            // Kendi görüntümüz — sağ üst köşede küçük PiP (sadece görüntülü
            // aramada VE kameramız açıkken). Sürüklenebilir mini-PiP web'de
            // vardı ama bu turun kapsamı DIŞI (görev notu: netleşmemiş bir
            // tasarım detayına takılma, MVP karar ver) — sabit köşe yeterli.
            if (phaseData.isVideo && localVideoTrack != null && isCameraEnabled && eglBaseContext != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 96.dp, height = 128.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E)),
                ) {
                    VideoRenderer(
                        videoTrack = localVideoTrack,
                        eglBaseContext = eglBaseContext,
                        rendererEvents = noopRendererEvents,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = durationText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        CallControlsBar(
            isMicEnabled = isMicEnabled,
            isCameraEnabled = isCameraEnabled,
            isVideoCall = phaseData.isVideo,
            onToggleMic = onToggleMic,
            onToggleCamera = onToggleCamera,
            onEndCall = onEndCall,
        )
    }
}

@Composable
private fun CallControlsBar(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    isVideoCall: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onEndCall: () -> Unit,
) {
    Surface(color = Color(0xFF1C1C1E)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CallControlButton(
                icon = if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = if (isMicEnabled) "Mikrofonu kapat" else "Mikrofonu aç",
                onClick = onToggleMic,
            )
            // Kamera butonu web'in call.js#onCameraButton()'ıyla AYNI ikili
            // işlev — sesli aramada bile görünür (görüntülüye geçiş SONRAKİ
            // bir tur, bu turun kapsamı DIŞI — bkz. görev notu "upgrade-offer
            // KAPSAM DIŞI"), burada SADECE görüntülü aramada aç/kapat.
            if (isVideoCall) {
                CallControlButton(
                    icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = if (isCameraEnabled) "Kamerayı kapat" else "Kamerayı aç",
                    onClick = onToggleCamera,
                )
            }
            IconButton(
                onClick = onEndCall,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Aramayı sonlandır")
            }
        }
    }
}

@Composable
private fun RowScope.CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
        ),
        modifier = Modifier
            .clip(CircleShape)
            .size(56.dp),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}
