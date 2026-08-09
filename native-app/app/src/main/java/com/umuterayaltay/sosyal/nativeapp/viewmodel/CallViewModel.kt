package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.twilio.audioswitch.AudioDevice
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CallTokenResult
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Grup sesli/görüntülü arama ekranının bağlantı durumu — backend'den token
 * alma (Loading) + LiveKit'e bağlanma başarılı olunca [room] (Connected) +
 * hata (Error, [notConfigured]=true iken CallScreen tam ekran hata YERİNE
 * kısa bir Toast/Snackbar + otomatik geri navigasyon gösterir — bkz. görev
 * notu: LiveKit henüz yapılandırılmamış olması ÇOK MUHTEMEL, kapatıcı bir
 * hata ekranı gibi SUNULMAZ).
 */
sealed class CallUiState {
    data object Loading : CallUiState()
    data class Connected(val room: Room) : CallUiState()
    data class Error(val message: String, val notConfigured: Boolean = false) : CallUiState()
}

sealed class CallEvent {
    data object SessionExpired : CallEvent()
}

/**
 * Tek bir grup araması için ViewModel — ConversationViewModel ile AYNI desen
 * (constructor parametreli conversationId, bu yüzden [CallViewModelFactory]
 * gerekir). LiveKit `Room` nesnesi BURADA (ViewModel'de) tutulur ve
 * connect/disconnect BURADAN yönetilir (görev notu: "CallViewModel Room
 * nesnesini tutup connect/disconnect yönetir") — CallScreen bu Room'u
 * `passedRoom` olarak Compose state composable'larına (rememberParticipants/
 * rememberParticipantTrackReferences) besler, kendi bağlantısını KURMAZ
 * (RoomScope/rememberLiveKitRoom kullanılmadı — context7 dokümantasyonuna
 * göre BU iki fonksiyon KENDİ Room'unu yaratıp bağlanır, halbuki backend'den
 * token almak ASENKRON bir ön-adım olduğu için Room'un ViewModel tarafında
 * yaratılıp beslenmesi daha uygun).
 *
 * app/api_v1/messaging.py api_call_token() sözleşmesi: 503
 * group_calls_not_configured / 404 not_found / 401 unauthorized / 500
 * token_generation_failed (bkz. MessagingRepository.getCallToken() yorumu).
 */
class CallViewModel(
    private val conversationId: String,
    // 2026-08-09 (kullanıcı raporu: "grupta sesli arama butonu yok, sadece
    // görüntülü var") — 1:1'in OutgoingRinging/AcceptIncoming'deki isVideo
    // parametresiyle AYNI gerekçe: sesli arama için kamera BAŞTAN kapalı
    // başlar, kullanıcı isterse aramanın ORTASINDA toggleCamera() ile
    // açabilir (LiveKit'te sesli/görüntülü arasında SERT bir ayrım yok,
    // sadece başlangıç durumu farklı).
    private val initialVideo: Boolean = true,
) : ViewModel() {

    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Loading)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(initialVideo)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    // Ön/arka kamera değiştirme (2026-08-09) — 1:1'in WebRtcCallManager'ındaki
    // AYNI ihtiyaç, ama LiveKit'in LocalVideoTrack.switchCamera() 1:1'in
    // CameraVideoCapturer.switchCamera()'sının AKSİNE dışa açık bir
    // "isFrontFacing" onay callback'i SUNMUYOR (kendi içinde options.position'ı
    // GÜNCELLİYOR ama bunu StateFlow gibi gözlemlenebilir kılmıyor) — bu yüzden
    // burada İYİMSER bir toggle yapılıyor (standart 2-kameralı — ön+arka —
    // cihazlarda DOĞRU, 3+ kameralı bazı egzotik cihazlarda YANLIŞ olabilir,
    // kabul edilebilir bir sınır: switchCamera() zaten deviceId/position
    // verilmeden "bir SONRAKİ kameraya" geçiyor, biz sadece hangi YÖNDE
    // olduğumuzu TAHMİN ediyoruz).
    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    fun switchCamera() {
        val activeRoom = room ?: return
        val track = activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
            ?: return
        track.switchCamera()
        _isFrontCamera.value = !_isFrontCamera.value
    }

    // Ses çıkışı seçimi — Room'un KENDİ audioSwitchHandler'ı (LiveKit SDK
    // built-in, io.livekit.android.audio.AudioSwitchHandler) kullanılıyor,
    // OneOnOneCallScreen'in WebRtcCallManager'da örneklediği AYNI sınıf
    // (bkz. o dosyanın yorumu) — burada AYRICA örneklenmiyor, Room zaten
    // kendi audio routing'ini bu sınıfla yönetiyor, biz sadece dinleyip
    // StateFlow'a yansıtıyoruz.
    private val _availableAudioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioDevice>> = _availableAudioDevices.asStateFlow()

    private val _selectedAudioDevice = MutableStateFlow<AudioDevice?>(null)
    val selectedAudioDevice: StateFlow<AudioDevice?> = _selectedAudioDevice.asStateFlow()

    private val audioDeviceChangeListener = { devices: List<AudioDevice>, selected: AudioDevice? ->
        _availableAudioDevices.value = devices
        _selectedAudioDevice.value = selected
    }

    fun selectAudioDevice(device: AudioDevice) {
        room?.audioSwitchHandler?.selectDevice(device)
        _selectedAudioDevice.value = device
    }

    private val _events = MutableSharedFlow<CallEvent>()
    val events: SharedFlow<CallEvent> = _events.asSharedFlow()

    private var room: Room? = null
    private var started = false

    /**
     * [context] kamera/render için gerekli (LiveKit.create(applicationContext))
     * — CallScreen ilk composition'da (LaunchedEffect(Unit)) çağırır.
     * [started] guard'ı config-change/recomposition'da İKİNCİ bir token isteği
     * + bağlantı ATILMASIN diye (StoryCreateViewModel'deki tekil-init
     * guard'larıyla AYNI gerekçe).
     */
    fun start(context: Context) {
        if (started) return
        started = true
        viewModelScope.launch {
            _uiState.value = CallUiState.Loading
            when (val result = messagingRepository.getCallToken(conversationId)) {
                is CallTokenResult.Success -> connect(context, result.url, result.token)
                is CallTokenResult.Error -> handleError(result.code)
            }
        }
    }

    private suspend fun connect(context: Context, url: String, token: String) {
        try {
            val newRoom = LiveKit.create(context.applicationContext)
            newRoom.connect(url, token)
            newRoom.localParticipant.setMicrophoneEnabled(true)
            newRoom.localParticipant.setCameraEnabled(initialVideo)
            room = newRoom
            _isMicEnabled.value = true
            _isCameraEnabled.value = initialVideo
            // Room bağlandığı anda audioSwitchHandler'ı KENDİSİ start eder —
            // biz sadece dinleyip mevcut listeyi/seçimi StateFlow'a yansıtıyoruz.
            // Nullable: Room varsayılan olarak AudioSwitchHandler kullanır ama
            // API farklı bir AudioHandler ile özelleştirilme ihtimaline karşı
            // null dönebiliyor — bu proje özelleştirmiyor, pratikte hep dolu.
            val switchHandler = newRoom.audioSwitchHandler
            switchHandler?.registerAudioDeviceChangeListener(audioDeviceChangeListener)
            _availableAudioDevices.value = switchHandler?.availableAudioDevices ?: emptyList()
            _selectedAudioDevice.value = switchHandler?.selectedAudioDevice
            _uiState.value = CallUiState.Connected(newRoom)
        } catch (e: Exception) {
            _uiState.value = CallUiState.Error("Aramaya bağlanılamadı, lütfen tekrar deneyin")
        }
    }

    private suspend fun handleError(code: String?) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(CallEvent.SessionExpired)
            return
        }
        _uiState.value = when (code) {
            // Görev notu: LiveKit kimlik bilgileri sunucuda henüz ayarlanmamış
            // olması ÇOK MUHTEMEL bir durum — kapatıcı bir hata gibi DEĞİL,
            // CallScreen'de kısa bir mesajla + otomatik geri navigasyonla ele
            // alınır (bkz. [CallUiState.Error.notConfigured]).
            "group_calls_not_configured" -> CallUiState.Error(
                message = "Sesli/görüntülü arama şu an aktif değil",
                notConfigured = true,
            )
            "not_found" -> CallUiState.Error("Bu sohbette arama yapılamıyor")
            "network_error" -> CallUiState.Error("Bağlantı hatası — internet bağlantınızı kontrol edin")
            else -> CallUiState.Error("Arama başlatılamadı, lütfen tekrar deneyin")
        }
    }

    fun toggleMic() {
        val activeRoom = room ?: return
        viewModelScope.launch {
            val newValue = !_isMicEnabled.value
            activeRoom.localParticipant.setMicrophoneEnabled(newValue)
            _isMicEnabled.value = newValue
        }
    }

    fun toggleCamera() {
        val activeRoom = room ?: return
        viewModelScope.launch {
            val newValue = !_isCameraEnabled.value
            activeRoom.localParticipant.setCameraEnabled(newValue)
            _isCameraEnabled.value = newValue
        }
    }

    /** Kullanıcı "aramayı sonlandır" butonuna basınca — CallScreen bunu
     * çağırdıktan sonra KENDİSİ geri navigasyon yapar (onCleared() da AYRICA
     * disconnect dener, iki kez disconnect() çağrılması LiveKit tarafında
     * zararsız/idempotent kabul edilir — Room zaten bağlı değilken no-op olur). */
    fun endCall() {
        val activeRoom = room ?: return
        room = null
        activeRoom.audioSwitchHandler?.unregisterAudioDeviceChangeListener(audioDeviceChangeListener)
        _availableAudioDevices.value = emptyList()
        _selectedAudioDevice.value = null
        viewModelScope.launch {
            activeRoom.disconnect()
        }
    }

    /** ConversationViewModel.onCleared()'daki AYNI disiplin (bkz. o dosyanın
     * yorumu): onCleared() suspend DEĞİL ve viewModelScope bu noktada ZATEN
     * iptal edilmiş durumda — Room.disconnect()'i çağırabilmek için AYRI,
     * kısa ömürlü bir scope kullanılıyor (ekran arkaya atılır/kapatılırsa
     * LiveKit bağlantısı sızmasın diye). */
    override fun onCleared() {
        super.onCleared()
        val activeRoom = room
        room = null
        if (activeRoom != null) {
            try {
                activeRoom.audioSwitchHandler?.unregisterAudioDeviceChangeListener(audioDeviceChangeListener)
            } catch (e: Exception) {
                // En iyi-çaba temizlik.
            }
            CoroutineScope(Dispatchers.IO).launch {
                activeRoom.disconnect()
            }
        }
    }
}

/** CallViewModel constructor'ı conversationId parametresi aldığı için
 * ConversationViewModelFactory ile AYNI desen. */
class CallViewModelFactory(
    private val conversationId: String,
    private val initialVideo: Boolean = true,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CallViewModel(conversationId, initialVideo) as T
    }
}
