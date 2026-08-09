package com.umuterayaltay.sosyal.nativeapp.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.twilio.audioswitch.AudioDevice
import com.umuterayaltay.sosyal.nativeapp.ui.components.AudioOutputButton
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CallEvent
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CallUiState
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CallViewModel
import com.umuterayaltay.sosyal.nativeapp.viewmodel.CallViewModelFactory
import io.livekit.android.compose.state.rememberParticipantTrackReferences
import io.livekit.android.compose.state.rememberParticipants
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.Track

/**
 * Grup sesli/görüntülü arama ekranı — LiveKit `Room` nesnesi [CallViewModel]'de
 * tutulur/bağlanır (bkz. o dosyanın sınıf yorumu), burada SADECE Compose
 * state composable'larına (`rememberParticipants`/`rememberParticipantTrackReferences`)
 * `passedRoom` olarak beslenir — RoomScope/rememberLiveKitRoom KULLANILMADI
 * (bunlar KENDİ bağlantısını kurar, halbuki token backend'den ASENKRON
 * alınıyor). Kütüphane paket adları context7 MCP dokümantasyonuyla ÇELİŞKİLİYDİ
 * (bazı örnekler `livekit.components.compose.*`, bazıları `io.livekit.android.
 * compose.*` gösteriyordu) — gerçek `io.livekit:livekit-android-compose-
 * components:2.4.0` .aar'ı indirilip classes.jar'ı açılarak GERÇEK paket
 * yolları (`io.livekit.android.compose.state`/`.ui`) doğrulandı, tahmin
 * edilmedi.
 */
@Composable
fun CallScreen(
    conversationId: String,
    // 2026-08-09 — AppNavHost'taki "call/{conversationId}/{isVideo}" rota
    // argümanından gelir, ConversationScreen'in ayrı sesli/görüntülü buton
    // çiftiyle AYNI gerekçe (bkz. CallViewModel.initialVideo yorumu).
    isVideo: Boolean = true,
    onNavigateBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: CallViewModel = viewModel(
        factory = CallViewModelFactory(conversationId, isVideo),
    ),
) {
    val context = LocalContext.current
    // 2026-08-09 (kullanıcı raporu: "görüntülü aramadayken telefon kendini
    // uyku moduna alıyordu") — OneOnOneCallScreen.kt'deki AYNI gerekçe/desen
    // (findActivity() extension'ı da AYNI dosyada KOPYALANDI, bkz. o
    // dosyadaki yorum).
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val isMicEnabled by viewModel.isMicEnabled.collectAsState()
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val selectedAudioDevice by viewModel.selectedAudioDevice.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CallEvent.SessionExpired -> onSessionExpired()
            }
        }
    }

    // Kamera + ses izin durumu — StoryCreateScreen'deki AYNI desen (izin
    // ekranın KENDİSİNDE isteniyor, MainActivity'de DEĞİL). LiveKit izinsiz
    // track publish edemeyeceği için mic-only fallback YOK — izin verilmeden
    // arama başlatılmaz, açık bir izin isteme ekranı gösterilir.
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
    LaunchedEffect(hasAllPermissions) {
        if (hasAllPermissions) {
            viewModel.start(context)
        }
    }

    // "not_configured" — LiveKit sunucuda henüz yapılandırılmamış (görev notu:
    // BU DURUM ÇOK MUHTEMEL şu an) — kapatıcı bir hata ekranı YERİNE kısa bir
    // Toast + otomatik geri navigasyon (diğer ekranlardaki Toast.makeText
    // deseniyle AYNI, bkz. PostShareSheet.kt/HighlightsScreen.kt).
    val state = uiState
    LaunchedEffect(state) {
        if (state is CallUiState.Error && state.notConfigured) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when {
            !hasAllPermissions -> PermissionRationale(
                onRequestPermission = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                onClose = onNavigateBack,
            )
            state is CallUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            state is CallUiState.Error -> {
                // notConfigured=true zaten yukarıdaki LaunchedEffect'te ele
                // alınıp geri navigasyona gidiyor — burası SADECE diğer
                // hatalar (not_found/network_error/vb.) için tam ekran mesaj.
                if (!state.notConfigured) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(state.message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onNavigateBack) { Text("Geri dön") }
                    }
                }
            }
            state is CallUiState.Connected -> CallContent(
                room = state.room,
                isMicEnabled = isMicEnabled,
                isCameraEnabled = isCameraEnabled,
                isFrontCamera = isFrontCamera,
                availableAudioDevices = availableAudioDevices,
                selectedAudioDevice = selectedAudioDevice,
                onToggleMic = viewModel::toggleMic,
                onToggleCamera = viewModel::toggleCamera,
                onSwitchCamera = viewModel::switchCamera,
                onSelectAudioDevice = viewModel::selectAudioDevice,
                onEndCall = {
                    viewModel.endCall()
                    onNavigateBack()
                },
            )
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit, onClose: () -> Unit) {
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
private fun CallContent(
    room: Room,
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    isFrontCamera: Boolean,
    availableAudioDevices: List<AudioDevice>,
    selectedAudioDevice: AudioDevice?,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSelectAudioDevice: (AudioDevice) -> Unit,
    onEndCall: () -> Unit,
) {
    val participants by rememberParticipants(passedRoom = room)

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (participants.size <= 1) 1 else 2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            items(participants, key = { it.identity.toString() }) { participant ->
                ParticipantTile(
                    room = room,
                    participant = participant,
                    isFrontCamera = isFrontCamera,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp),
                )
            }
        }

        CallControls(
            isMicEnabled = isMicEnabled,
            isCameraEnabled = isCameraEnabled,
            availableAudioDevices = availableAudioDevices,
            selectedAudioDevice = selectedAudioDevice,
            onToggleMic = onToggleMic,
            onToggleCamera = onToggleCamera,
            onSwitchCamera = onSwitchCamera,
            onSelectAudioDevice = onSelectAudioDevice,
            onEndCall = onEndCall,
        )
    }
}

/**
 * Performans önerisi (context7 dokümantasyonu — quick-reference.md):
 * `key(participant.identity)` ile VideoTrackView'in GEREKSİZ recomposition'ı
 * önlenir. LazyVerticalGrid.items() zaten kendi `key` parametresiyle bunu
 * DIŞ katmanda sağlıyor (yukarıda), burada AYRICA `key(participant.identity)`
 * ile VideoTrackView'i saran iç katman da eklendi (dokümantasyondaki BİREBİR
 * örnek — çift güvence, zararsız).
 */
@Composable
private fun ParticipantTile(
    room: Room,
    participant: Participant,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier,
) {
    // NOT: rememberParticipantTrackReferences'ın GERÇEK 2.4.0 overload seti
    // (gerçek assembleDebug hatasıyla doğrulandı — context7 dokümantasyonundaki
    // örnek passedParticipant+passedRoom'u BİRLİKTE gösteriyordu ama derleyici
    // bunu REDDETTİ) passedParticipant alan overload'da passedRoom YOK —
    // Participant zaten KENDİ Room'una bağlı olduğu için buna gerek yok.
    val cameraTracks by rememberParticipantTrackReferences(
        sources = listOf(Track.Source.CAMERA),
        passedParticipant = participant,
    )

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Color(0xFF1C1C1E)),
    ) {
        key(participant.identity) {
            val trackRef = cameraTracks.firstOrNull()
            if (trackRef != null) {
                // 2026-08-09 (kullanıcı raporu: "1:1'de düzelttin ama grupta
                // düzeltmedin") — LiveKit'in VideoTrackView'i 1:1'in aksine
                // (org.webrtc TextureViewRenderer'ı elle sarmalayan Stream
                // kütüphanesi) İLK SINIF bir `mirror: Boolean` parametresi
                // sunuyor, ayrıca "undo" mantığı YOK — bu yüzden SADECE yerel
                // katılımcı (kendi kutucuğumuz) için `true`, uzak katılımcılar
                // için varsayılan `false` (karşı tarafın görüntüsü GERÇEK
                // yönde kalmalı). `isFrontCamera` de EK koşul (kullanıcı
                // isteği: "arka kameraya geçme de gelsin") — arka kamerada
                // aynalamak GERÇEK yönle çelişirdi. `VideoTrackView`'in
                // `mirror` parametresi REAKTİF (DisposableEffect(mirror) ile
                // `setMirror` her değişimde tekrar çağrılıyor) — 1:1'deki
                // `key()` ile View yeniden kurma numarasına GEREK YOK.
                VideoTrackView(
                    trackReference = trackRef,
                    modifier = Modifier.fillMaxSize(),
                    room = room,
                    mirror = participant is LocalParticipant && isFrontCamera,
                    scaleType = ScaleType.Fill,
                )
            }
        }
        Text(
            text = participant.name?.takeIf { it.isNotBlank() } ?: (participant.identity?.toString() ?: "?"),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CallControls(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    availableAudioDevices: List<AudioDevice>,
    selectedAudioDevice: AudioDevice?,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSelectAudioDevice: (AudioDevice) -> Unit,
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
                active = isMicEnabled,
                onClick = onToggleMic,
            )
            CallControlButton(
                icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                contentDescription = if (isCameraEnabled) "Kamerayı kapat" else "Kamerayı aç",
                active = isCameraEnabled,
                onClick = onToggleCamera,
            )
            // 2026-08-09 (kullanıcı isteği: "arka kameraya geçme de gelsin")
            // — 1:1'deki AYNI koşul: sadece kamera açıkken anlamlı.
            if (isCameraEnabled) {
                CallControlButton(
                    icon = Icons.Filled.Cameraswitch,
                    contentDescription = "Kamerayı değiştir",
                    active = false,
                    onClick = onSwitchCamera,
                )
            }
            AudioOutputButton(
                availableDevices = availableAudioDevices,
                selectedDevice = selectedAudioDevice,
                onSelectDevice = onSelectAudioDevice,
            )
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

/**
 * Mikrofon/kamera aç-kapa butonu — SADECE görsel geri bildirim eklendi:
 * durum değişince zemin rengi ([animateColorAsState], kapalıyken hafif
 * kırmızımsı) ve kısa bir "pop" ölçeklenme geçişi. Toggle mantığının
 * KENDİSİ (onClick -> viewModel.toggleMic/toggleCamera) DEĞİŞMEDİ.
 */
@Composable
private fun RowScope.CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean = true,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (active) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
        animationSpec = tween(200),
        label = "callControlContainerColor",
    )
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        scale.animateTo(1.15f, tween(80))
        scale.animateTo(1f, tween(120))
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        modifier = Modifier
            .clip(CircleShape)
            .size(56.dp)
            .scale(scale.value),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** OneOnOneCallScreen.kt'deki AYNI helper (bkz. o dosyadaki yorum) —
 * `LocalContext.current` genelde bir `ContextWrapper` katmanına sarılı
 * gelir, `Window`'a erişmek (ekranı-açık-tut bayrağı için) gerçek
 * Activity'ye inmeyi gerektiriyor. */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
