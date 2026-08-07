package com.umuterayaltay.sosyal.nativeapp.repository

import android.content.Context
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import com.umuterayaltay.sosyal.nativeapp.webrtc.WebRtcCallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

private const val TAG = "CallSession"

enum class CallEndReason { LOCAL_HANGUP, REMOTE_HANGUP, REJECTED, NO_ANSWER, FAILED, ERROR }

/** Web'in call.js `state.callState` (idle/ringing/active/ended) ile AYNI
 * durum makinesi — burada arayan/aranan ayrımı da tip seviyesinde (Outgoing
 * vs Incoming) net. */
sealed class CallPhase {
    data object Idle : CallPhase()
    data class OutgoingRinging(
        val conversationId: String,
        val otherUserId: String,
        // Hedefin arama kanalı adı — YÖNLENDİRME bunun üzerinden yapılır
        // (bkz. CallSignalingManager.sendSignal). otherUserId sadece
        // görüntüleme/kimlik amaçlı KALDI, kanal ADI DEĞİL.
        val otherCallTopic: String,
        val otherName: String,
        val otherAvatar: String?,
        val isVideo: Boolean,
    ) : CallPhase()
    data class IncomingRinging(
        val conversationId: String?,
        val otherUserId: String,
        // Arayanın kanal adı — SADECE gelen Offer'ın fromTopic'inden
        // öğrenilebilir (bkz. handleOffer), başka hiçbir yerden bilinemez.
        val otherCallTopic: String,
        val callerName: String,
        val callerAvatar: String?,
        val isVideo: Boolean,
    ) : CallPhase()
    data class Active(
        val conversationId: String?,
        val otherUserId: String,
        val otherCallTopic: String,
        val otherName: String,
        val otherAvatar: String?,
        val isVideo: Boolean,
        val isCaller: Boolean,
        val startedAtMs: Long,
    ) : CallPhase()
    data class Ended(val reason: CallEndReason) : CallPhase()
}

/**
 * 1:1 WebRTC aramasının UÇTAN UCA orkestrasyonu — [CallSignalingManager]
 * (Supabase Realtime sinyalleşmesi) + [WebRtcCallManager] (PeerConnection)
 * arasında köprü, web'in call.js dosyasındaki TEK global `state` objesiyle
 * AYNI rolü oynuyor. BİLEREK bir ViewModel DEĞİL, ServiceLocator'da UYGULAMA
 * ÖMRÜ boyunca yaşayan bir singleton (bkz. ServiceLocator.callSessionManager)
 * — arama, kullanıcı hangi ekrana geçerse geçsin (Feed/Discover/başka bir
 * konuşma) devam etmeli ve gelen arama HERHANGİ bir ekranda yakalanabilmeli
 * (görev notu, web'in de AYNI sayfa-geneli davranışı) — bir ViewModel
 * navigasyonla birlikte dispose olurdu, bu yüzden state Compose lifecycle'ının
 * DIŞINA taşındı. [OneOnOneCallViewModel] bu sınıfın StateFlow'larını
 * SADECE Compose'a yansıtan ince bir katman.
 */
class CallSessionManager(
    private val signaling: CallSignalingManager,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var myUserId: String? = null
    private var listening = false
    private var webRtc: WebRtcCallManager? = null
    private var pendingOfferSdp: String? = null
    private var noAnswerJob: Job? = null

    // startCall()/acceptIncoming() PeerConnectionFactory kurulumu/SDP üretimi
    // için asenkron çalışır (yüzlerce ms sürebilir) — bu sırada kullanıcı
    // hangup()/rejectIncoming() ile aramayı senkron olarak Ended/Idle'a
    // düşürebilir. generation her teardown'da (cleanupAndEnd/rejectIncoming)
    // artırılır; async iş bitince kendi ürettiği değerle KARŞILAŞTIRIR —
    // uyuşmuyorsa (araya bir teardown girmiş) webRtc'yi ata/phase'i Active'e
    // GEÇİRMEDEN dispose eder. Bu koruma OLMADAN: kamera/mikrofon açık kalan,
    // hiçbir yerden dispose edilmeyen bir WebRtcCallManager sızdırılabilir ve
    // zaten Idle/Ended olan phase yanlışlıkla Active'e geri döndürülebilirdi.
    private var generation = 0

    private val _phase = MutableStateFlow<CallPhase>(CallPhase.Idle)
    val phase: StateFlow<CallPhase> = _phase.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    // Ses çıkışı seçimi — WebRtcCallManager'ın AudioSwitchHandler'ından
    // geçiş yapılır (bkz. o sınıfın yorumu). Liste/seçim reaktif olarak
    // güncellenir (kulaklık takılıp çıkarılması gibi).
    private val _availableAudioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioDevice>> = _availableAudioDevices.asStateFlow()

    private val _selectedAudioDevice = MutableStateFlow<AudioDevice?>(null)
    val selectedAudioDevice: StateFlow<AudioDevice?> = _selectedAudioDevice.asStateFlow()

    fun selectAudioDevice(device: AudioDevice) {
        webRtc?.selectAudioDevice(device)
    }

    fun eglBaseContext() = WebRtcCallManager.eglBaseContextOrNull()

    /** AppNavHost kök seviyesinde LaunchedEffect(Unit) ile çağrılır (görev
     * notu — "calls:<meId> kanalına ABONELİĞİ tek bir yerden, oturum
     * açıkken SÜREKLİ açık tut"). idempotent: aynı kullanıcı için tekrarlı
     * çağrı no-op (config-change/recomposition güvenli).
     *
     * KULLANICI RAPORU (gerçek cihaz: "web'den arayınca mobile gelen arama
     * hiç gelmiyor") — [CallSignalingManager.startListening] artık TEK
     * seferlik bir connect() DEĞİL, sürekli retry/health-watch eden bir
     * döngü (bkz. o sınıfın sınıf yorumu) — soğuk başlangıçta yaşanabilecek
     * geçici bir token/ağ hıçkırığı artık oturumun GERİ KALANI boyunca
     * "gelen arama hiç gelmiyor" anlamına gelmiyor. */
    fun startGlobalListening(userId: String) {
        if (listening && myUserId == userId) return
        Log.d(TAG, "startGlobalListening: userId=$userId")
        listening = true
        myUserId = userId
        signaling.startListening(userId, scope)
        signaling.incoming.onEach { handleSignal(it) }.launchIn(scope)
    }

    private fun handleSignal(signal: CallSignal) {
        Log.d(TAG, "handleSignal: alındı: $signal, mevcut phase=${_phase.value}")
        val me = myUserId ?: return
        if (signal.from == me) return
        when (signal) {
            is CallSignal.Offer -> handleOffer(signal)
            is CallSignal.Answer -> handleAnswer(signal)
            is CallSignal.Ice -> webRtc?.addRemoteIceCandidate(signal.candidate, signal.sdpMLineIndex, signal.sdpMid)
            is CallSignal.Hangup -> {
                if (_phase.value !is CallPhase.Idle && _phase.value !is CallPhase.Ended) {
                    cleanupAndEnd(CallEndReason.REMOTE_HANGUP)
                }
            }
            is CallSignal.Reject -> {
                // SADECE biz ararken (OutgoingRinging) anlamlı — web'in AYNI
                // koruması (bkz. call.js handleSignal 'reject' dalı yorumu):
                // kurulu bir aramayı bayat bir reject sinyali ÖLDÜRMESİN.
                if (_phase.value is CallPhase.OutgoingRinging) {
                    cleanupAndEnd(CallEndReason.REJECTED)
                }
            }
        }
    }

    private fun handleOffer(signal: CallSignal.Offer) {
        if (_phase.value !is CallPhase.Idle) {
            // Meşgulüm — web'in AYNI davranışı (call.js: reject SADECE ARAYANA gider).
            Log.d(TAG, "handleOffer: meşgulüm (phase=${_phase.value}), ${signal.from}'a Reject gönderiliyor")
            val me = myUserId ?: return
            val myTopic = signaling.myTopic ?: ""
            scope.launch { signaling.sendSignal(signal.fromTopic, CallSignal.Reject(me, myTopic, signal.conversationId)) }
            return
        }
        Log.d(TAG, "handleOffer: IncomingRinging'e geçiliyor, from=${signal.from}")
        pendingOfferSdp = signal.sdp
        _phase.value = CallPhase.IncomingRinging(
            conversationId = signal.conversationId,
            otherUserId = signal.from,
            otherCallTopic = signal.fromTopic,
            callerName = signal.callerName,
            callerAvatar = signal.callerAvatar,
            isVideo = signal.video,
        )
    }

    private fun handleAnswer(signal: CallSignal.Answer) {
        val outgoing = _phase.value as? CallPhase.OutgoingRinging ?: run {
            Log.w(TAG, "handleAnswer: Answer geldi ama phase OutgoingRinging DEĞİL (${_phase.value}), YOK SAYILIYOR")
            null
        } ?: return
        Log.d(TAG, "handleAnswer: Answer alındı from=${signal.from}, setRemoteAnswer çağrılıyor")
        noAnswerJob?.cancel()
        scope.launch {
            try {
                webRtc?.setRemoteAnswer(signal.sdp)
                Log.d(TAG, "handleAnswer: setRemoteAnswer başarılı, Active'e geçiliyor")
                _phase.value = CallPhase.Active(
                    conversationId = outgoing.conversationId,
                    otherUserId = outgoing.otherUserId,
                    otherCallTopic = outgoing.otherCallTopic,
                    otherName = outgoing.otherName,
                    otherAvatar = outgoing.otherAvatar,
                    isVideo = outgoing.isVideo,
                    isCaller = true,
                    startedAtMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "handleAnswer: setRemoteAnswer istisna fırlattı", e)
                cleanupAndEnd(CallEndReason.ERROR)
            }
        }
    }

    /** Arayan taraf — ConversationScreen'in yeni 1:1 arama ikonundan çağrılır.
     * [otherCallTopic]: backend'in bu konuşma için ürettiği tahmin edilemez
     * HMAC kanal adı (bkz. ConversationInfoDto.otherCallTopic) — YÖNLENDİRME
     * artık BUNUNLA yapılıyor, otherUserId sadece kimlik/görüntüleme için. */
    fun startCall(
        context: Context,
        conversationId: String,
        otherUserId: String,
        otherCallTopic: String,
        isVideo: Boolean,
        otherName: String,
        otherAvatar: String?,
    ) {
        val me = myUserId ?: return
        if (_phase.value !is CallPhase.Idle) return
        if (otherCallTopic.isBlank()) {
            Log.w(TAG, "startCall: otherCallTopic boş, arama başlatılamıyor")
            return
        }
        _phase.value = CallPhase.OutgoingRinging(conversationId, otherUserId, otherCallTopic, otherName, otherAvatar, isVideo)
        val myGeneration = ++generation
        scope.launch {
            try {
                val myTopic = signaling.myTopic ?: ""
                val rtc = WebRtcCallManager(
                    context = context,
                    enableVideo = isVideo,
                    onIceCandidate = { candidate ->
                        scope.launch {
                            signaling.sendSignal(
                                otherCallTopic,
                                CallSignal.Ice(me, myTopic, conversationId, candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid),
                            )
                        }
                    },
                    onRemoteVideoTrack = { track -> _remoteVideoTrack.value = track },
                    onConnectionFailed = { cleanupAndEnd(CallEndReason.FAILED) },
                )
                val offerSdp = rtc.createOffer()
                if (myGeneration != generation) {
                    // Araya hangup()/cleanupAndEnd() girdi (kullanıcı çağrıyı
                    // SDP üretimi bitmeden iptal etti) — bu rtc'yi hiçbir yere
                    // atamadan doğrudan dispose et, phase'e DOKUNMA.
                    rtc.dispose()
                    return@launch
                }
                webRtc = rtc
                rtc.availableAudioDevices.onEach { _availableAudioDevices.value = it }.launchIn(scope)
                rtc.selectedAudioDevice.onEach { _selectedAudioDevice.value = it }.launchIn(scope)
                _localVideoTrack.value = rtc.localVideoTrack
                _isCameraEnabled.value = isVideo
                _isMicEnabled.value = true

                val myProfile = authRepository.getCurrentUser()
                val offerDelivered = signaling.sendSignal(
                    otherCallTopic,
                    CallSignal.Offer(
                        from = me,
                        fromTopic = myTopic,
                        conversationId = conversationId,
                        sdp = offerSdp,
                        video = isVideo,
                        callerName = myProfile?.username ?: "Birisi",
                        callerAvatar = myProfile?.avatarUrl,
                    ),
                )
                Log.d(TAG, "startCall: Offer gönderim sonucu=$offerDelivered, hedef=$otherCallTopic")

                noAnswerJob?.cancel()
                noAnswerJob = scope.launch {
                    delay(30_000)
                    if (_phase.value is CallPhase.OutgoingRinging) {
                        Log.w(TAG, "startCall: 30sn'de cevap gelmedi (NO_ANSWER), otherUserId=$otherUserId")
                        cleanupAndEnd(CallEndReason.NO_ANSWER)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCall: istisna", e)
                cleanupAndEnd(CallEndReason.ERROR)
            }
        }
    }

    /** Aranan taraf — gelen-arama overlay'inin "Kabul et" butonundan (izin
     * verildikten SONRA, bkz. OneOnOneCallScreen) çağrılır. */
    fun acceptIncoming(context: Context) {
        val incoming = _phase.value as? CallPhase.IncomingRinging ?: return
        val sdp = pendingOfferSdp ?: return
        val me = myUserId ?: return
        val myGeneration = ++generation
        scope.launch {
            try {
                val myTopic = signaling.myTopic ?: ""
                val rtc = WebRtcCallManager(
                    context = context,
                    enableVideo = incoming.isVideo,
                    onIceCandidate = { candidate ->
                        scope.launch {
                            signaling.sendSignal(
                                incoming.otherCallTopic,
                                CallSignal.Ice(me, myTopic, incoming.conversationId, candidate.sdp, candidate.sdpMLineIndex, candidate.sdpMid),
                            )
                        }
                    },
                    onRemoteVideoTrack = { track -> _remoteVideoTrack.value = track },
                    onConnectionFailed = { cleanupAndEnd(CallEndReason.FAILED) },
                )
                val answerSdp = rtc.createAnswerForOffer(sdp)
                if (myGeneration != generation) {
                    // Araya rejectIncoming()/cleanupAndEnd() girdi (kullanıcı
                    // kabul ile ret arasında hızlıca reddetti) — rtc'yi hiçbir
                    // yere atamadan dispose et, zaten gönderilmiş Reject'in
                    // üzerine yanlışlıkla bir Answer/Active YAZMA.
                    rtc.dispose()
                    return@launch
                }
                webRtc = rtc
                rtc.availableAudioDevices.onEach { _availableAudioDevices.value = it }.launchIn(scope)
                rtc.selectedAudioDevice.onEach { _selectedAudioDevice.value = it }.launchIn(scope)
                _localVideoTrack.value = rtc.localVideoTrack
                _isCameraEnabled.value = incoming.isVideo
                _isMicEnabled.value = true
                pendingOfferSdp = null

                // KULLANICI RAPORU (gerçek cihaz: "karşı taraf kabul ediyor
                // ama aramaya geçmiyor, sadece aranıyor kalıyor") — ÖNCEDEN
                // bu satırın sonucu HİÇ kontrol edilmeden doğrudan Active'e
                // geçiliyordu: Answer sinyali arayana gerçekten ULAŞMASA bile
                // aranan taraf "bağlandı" görüyordu, arayan sonsuza kadar
                // "Aranıyor..." ekranında kalıyordu. Artık gönderim
                // başarısızsa (bkz. CallSignalingManager.sendSignal'ın YENİ
                // Boolean dönüşü) Active'e GEÇİLMEZ, ERROR ile temizlenir.
                val delivered = signaling.sendSignal(incoming.otherCallTopic, CallSignal.Answer(me, myTopic, incoming.conversationId, answerSdp))
                Log.d(TAG, "acceptIncoming: Answer gönderim sonucu=$delivered, hedef=${incoming.otherCallTopic}")
                if (!delivered) {
                    cleanupAndEnd(CallEndReason.ERROR)
                    return@launch
                }

                _phase.value = CallPhase.Active(
                    conversationId = incoming.conversationId,
                    otherUserId = incoming.otherUserId,
                    otherCallTopic = incoming.otherCallTopic,
                    otherName = incoming.callerName,
                    otherAvatar = incoming.callerAvatar,
                    isVideo = incoming.isVideo,
                    isCaller = false,
                    startedAtMs = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "acceptIncoming: istisna", e)
                cleanupAndEnd(CallEndReason.ERROR)
            }
        }
    }

    /** Gelen-arama overlay'inin "Reddet" butonu — web'in rejectCall()'ı gibi
     * DOĞRUDAN Idle'a döner (Ended ekranı YOK, hiç kabul edilmemiş bir arama
     * için "arama bitti" göstermeye gerek yok). */
    fun rejectIncoming() {
        val incoming = _phase.value as? CallPhase.IncomingRinging ?: return
        val me = myUserId ?: return
        val myTopic = signaling.myTopic ?: ""
        generation++
        pendingOfferSdp = null
        scope.launch { signaling.sendSignal(incoming.otherCallTopic, CallSignal.Reject(me, myTopic, incoming.conversationId)) }
        _phase.value = CallPhase.Idle
    }

    /** Kullanıcı "kapat" butonuna basınca (OutgoingRinging VEYA Active
     * durumunda) — karşı tarafa hangup sinyali + yerel WebRTC temizliği. */
    fun hangup() {
        val current = _phase.value
        val me = myUserId
        val targetTopic = when (current) {
            is CallPhase.OutgoingRinging -> current.otherCallTopic
            is CallPhase.Active -> current.otherCallTopic
            else -> null
        }
        val conversationId = when (current) {
            is CallPhase.OutgoingRinging -> current.conversationId
            is CallPhase.Active -> current.conversationId
            else -> null
        }
        if (me != null && targetTopic != null) {
            val myTopic = signaling.myTopic ?: ""
            scope.launch { signaling.sendSignal(targetTopic, CallSignal.Hangup(me, myTopic, conversationId)) }
        }
        cleanupAndEnd(CallEndReason.LOCAL_HANGUP)
    }

    fun toggleMic() {
        val newValue = !_isMicEnabled.value
        webRtc?.setMicEnabled(newValue)
        _isMicEnabled.value = newValue
    }

    fun toggleCamera() {
        val newValue = !_isCameraEnabled.value
        webRtc?.setCameraEnabled(newValue)
        _isCameraEnabled.value = newValue
    }

    private fun cleanupAndEnd(reason: CallEndReason) {
        Log.d(TAG, "cleanupAndEnd: reason=$reason, önceki phase=${_phase.value}")
        generation++
        val myGeneration = generation
        noAnswerJob?.cancel()
        noAnswerJob = null
        webRtc?.dispose()
        webRtc = null
        pendingOfferSdp = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _availableAudioDevices.value = emptyList()
        _selectedAudioDevice.value = null

        // KULLANICI RAPORU (gerçek cihaz: aramayı bitirip TEKRAR arayınca
        // "arama bitirildi" yazıyor, hiçbir şey olmuyor) — kök neden: BackHandler/
        // "aranıyor" panelindeki iptal/aktif aramadaki kapat butonunun ÜÇÜ DE
        // hangup()'ı çağırdıktan HEMEN SONRA (senkron) onNavigateBack() de
        // çağırıyor — OneOnOneCallScreen bu yüzden ANINDA dispose oluyor ve
        // Ended fazını 1400ms gösterip resetToIdle() çağıran LaunchedEffect(phase)
        // HİÇ tamamlanamıyordu (composable dispose olunca coroutine iptal
        // edilir). Sonuç: phase SONSUZA KADAR Ended'de takılı kalıyordu —
        // startCall()/handleOffer() "phase Idle değilse no-op" korumasına
        // takılıp bir SONRAKİ arama denemesini (gelen VEYA giden) SESSİZCE
        // hiçbir şey yapmadan reddediyordu.
        //
        // Fix — iki katmanlı: (1) LOCAL_HANGUP'ta web'in AYNI davranışına
        // dönülüp (call.js endCall() sonrası ANINDA idle) Ended ekranı
        // HİÇ gösterilmiyor — kullanıcı zaten NEDEN bittiğini biliyor, ekranın
        // açık kalıp kalmamasına bağlı bir ETA riski de ortadan kalkıyor.
        // (2) DİĞER sebepler (REMOTE_HANGUP/REJECTED/NO_ANSWER/FAILED/ERROR)
        // için Ended YİNE gösterilir (kullanıcı gerçek bir açıklama görmeli)
        // ama artık resetToIdle()'ı SADECE ekranın LaunchedEffect'ine
        // GÜVENMİYOR — CallSessionManager'ın KENDİ (Compose lifecycle'ından
        // bağımsız, ServiceLocator singleton) scope'unda da bir güvenlik ağı
        // olarak zamanlanıyor; ekran zaten resetToIdle() çağırmışsa generation
        // kontrolü bunu ZARARSIZ bir no-op yapar.
        if (reason == CallEndReason.LOCAL_HANGUP) {
            _phase.value = CallPhase.Idle
            return
        }
        _phase.value = CallPhase.Ended(reason)
        scope.launch {
            delay(1600)
            if (generation == myGeneration && _phase.value is CallPhase.Ended) {
                _phase.value = CallPhase.Idle
            }
        }
    }

    /** [CallPhase.Ended] kısa ömürlü bir ara durum — OneOnOneCallScreen (açık
     * kaldıysa) bitiş sebebini kısaca gösterip geri navigasyon yaptıktan SONRA
     * bunu çağırır. [cleanupAndEnd]'İN KENDİ zamanlanmış sıfırlaması da AYNI
     * işi yapar (ekran o sırada açık olmayabilir diye) — ikisi de idempotent,
     * hangisi önce çalışırsa çalışsın güvenli. */
    fun resetToIdle() {
        if (_phase.value is CallPhase.Ended) _phase.value = CallPhase.Idle
    }
}
