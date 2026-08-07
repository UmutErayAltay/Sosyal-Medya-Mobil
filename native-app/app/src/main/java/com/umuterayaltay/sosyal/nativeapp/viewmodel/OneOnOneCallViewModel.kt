package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.twilio.audioswitch.AudioDevice
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.CallPhase
import com.umuterayaltay.sosyal.nativeapp.webrtc.WebRtcCallManager
import kotlinx.coroutines.flow.StateFlow

/**
 * [CallSessionManager]'ın (ServiceLocator singleton — bkz. o sınıfın dosya
 * yorumu, uygulama ömrü boyunca yaşar) StateFlow'larını Compose'a yansıtan
 * İNCE bir katman — CallViewModel (LiveKit, grup araması) İLE KARIŞTIRILMASIN,
 * o TAMAMEN AYRI bir sistem/dosya. Bu ViewModel'in KENDİ state'i YOK, gerçek
 * kaynak HER ZAMAN CallSessionManager'da — ekran dispose olup yeniden
 * oluşsa bile (ör. konfigürasyon değişikliği) arama KESİNTİYE UĞRAMAZ.
 */
class OneOnOneCallViewModel : ViewModel() {

    private val sessionManager = ServiceLocator.callSessionManager

    val phase: StateFlow<CallPhase> = sessionManager.phase
    val localVideoTrack = sessionManager.localVideoTrack
    val remoteVideoTrack = sessionManager.remoteVideoTrack
    val isMicEnabled = sessionManager.isMicEnabled
    val isCameraEnabled = sessionManager.isCameraEnabled
    val availableAudioDevices = sessionManager.availableAudioDevices
    val selectedAudioDevice = sessionManager.selectedAudioDevice

    fun selectAudioDevice(device: AudioDevice) {
        sessionManager.selectAudioDevice(device)
    }

    private var outgoingStarted = false

    fun eglBaseContext() = WebRtcCallManager.eglBaseContextOrNull()

    /** ConversationScreen'in yeni 1:1 arama ikonundan gelen arayan taraf —
     * OneOnOneCallScreen ilk composition'da (LaunchedEffect(Unit)) çağırır.
     * [outgoingStarted] guard'ı CallViewModel.start()'taki (LiveKit) AYNI
     * gerekçe: recomposition/config-change'de İKİNCİ bir offer ATILMASIN. */
    fun startOutgoingCall(
        context: Context,
        conversationId: String,
        otherUserId: String,
        otherCallTopic: String,
        isVideo: Boolean,
        otherName: String,
        otherAvatar: String?,
    ) {
        if (outgoingStarted) return
        outgoingStarted = true
        sessionManager.startCall(context, conversationId, otherUserId, otherCallTopic, isVideo, otherName, otherAvatar)
    }

    /** Gelen-arama overlay'inden "Kabul et" ile buraya navigate edilince,
     * OneOnOneCallScreen izin verildikten SONRA çağırır (CallScreen.kt'deki
     * AYNI izin-sonra-başlat deseni). */
    fun acceptIncoming(context: Context) {
        sessionManager.acceptIncoming(context)
    }

    fun hangup() {
        sessionManager.hangup()
    }

    fun toggleMic() {
        sessionManager.toggleMic()
    }

    fun toggleCamera() {
        sessionManager.toggleCamera()
    }

    fun resetToIdle() {
        sessionManager.resetToIdle()
    }
}

class OneOnOneCallViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OneOnOneCallViewModel() as T
    }
}
