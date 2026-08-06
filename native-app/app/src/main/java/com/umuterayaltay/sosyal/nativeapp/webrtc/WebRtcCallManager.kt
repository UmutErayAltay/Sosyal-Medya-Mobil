package com.umuterayaltay.sosyal.nativeapp.webrtc

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Web'in call.js#getIceServers() ile BİREBİR AYNI (görev spesifikasyonu) —
 * ücretsiz, kayıt gerektirmeyen STUN + metered.ca TURN relay listesi.
 */
private object WebRtcIceServers {
    fun list(): List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:80")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:443")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turns:standard.relay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
    )
}

/**
 * Tek bir 1:1 aramanın WebRTC katmanı — PeerConnectionFactory/EglBase
 * PAHALI (bir kez oluşturulup app ömrü boyunca paylaşılması gerekir, bkz.
 * companion object), ama PeerConnection'ın KENDİSİ her arama için YENİDEN
 * kurulur (bu sınıfın bir örneği = bir arama). CallSessionManager BUNU
 * offer/answer akışına göre sürüyor (bkz. o dosya) — burası SADECE org.webrtc
 * API'sini (context7 MCP ile /getstream/webrtc-android dokümantasyonu +
 * gerçek .aar/sources.jar İÇERİĞİ okunarak DOĞRULANDI, LiveKit görevindeki
 * AYNI disiplinle: paket adı `org.webrtc.*` — Stream'in kütüphanesi Google'ın
 * resmi WebRTC Android bindings'ini OLDUĞU GİBİ paketliyor, kendi paket adı
 * KULLANMIYOR, bkz. context7 "Usages" notu) callback→coroutine'e çeviren ince
 * bir sarmalayıcı.
 *
 * KTX modülü (io.getstream:stream-webrtc-android-ktx) BİLİNÇLİ olarak
 * eklenmedi — SdpObserver/PeerConnection.Observer callback'leri projenin
 * zaten kullandığı AuthRepository.currentFcmToken() deseniyle
 * (suspendCancellableCoroutine) elle sarmalandı, gereksiz bağımlılık
 * şişmesin diye (bkz. app/build.gradle.kts yorumu).
 *
 * Kamera: CameraX YERİNE WebRTC'nin KENDİ Camera2Enumerator/CameraVideoCapturer
 * pipeline'ı (görev notu) — StoryCreateScreen'in CameraX akışıyla KARIŞTIRILMADI,
 * tamamen ayrı bir video capture yolu.
 */
class WebRtcCallManager(
    context: Context,
    private val enableVideo: Boolean,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack?) -> Unit,
    private val onConnectionFailed: () -> Unit,
) {
    companion object {
        @Volatile private var factory: PeerConnectionFactory? = null
        @Volatile private var sharedEglBase: EglBase? = null
        private val initLock = Any()

        /** Compose tarafında VideoRenderer(eglBaseContext = ...) için gerekli —
         * SADECE en az bir WebRtcCallManager oluşturulduktan (ensureFactory
         * çağrıldıktan) SONRA çağrılabilir; CallSessionManager local/remote
         * VideoTrack'i null değilken kullanıldığı için bu sıralama HER ZAMAN
         * sağlanır. */
        fun eglBaseContextOrNull(): EglBase.Context? = sharedEglBase?.eglBaseContext

        private fun ensureFactory(context: Context): PeerConnectionFactory = synchronized(initLock) {
            factory?.let { return it }
            val egl = EglBase.create()
            sharedEglBase = egl
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions(),
            )
            val builtFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory()
            factory = builtFactory
            builtFactory
        }
    }

    private val appContext = context.applicationContext
    private val pcFactory = ensureFactory(appContext)

    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    var localVideoTrack: VideoTrack? = null
        private set
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null

    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private fun buildPeerConnection(): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(WebRtcIceServers.list()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                onIceCandidate.invoke(candidate)
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                if (state == PeerConnection.IceConnectionState.FAILED) onConnectionFailed.invoke()
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) onRemoteVideoTrack.invoke(track)
            }
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
        }
        return pcFactory.createPeerConnection(rtcConfig, observer)
            ?: error("PeerConnection oluşturulamadı")
    }

    private fun addLocalTracks(pc: PeerConnection) {
        val audioSource = pcFactory.createAudioSource(MediaConstraints())
        val audioTrack = pcFactory.createAudioTrack("audio0", audioSource)
        localAudioTrack = audioTrack
        pc.addTrack(audioTrack, listOf("stream0"))

        if (enableVideo) startLocalVideo(pc)
    }

    private fun startLocalVideo(pc: PeerConnection) {
        val eglContext = sharedEglBase?.eglBaseContext ?: return
        val enumerator = Camera2Enumerator(appContext)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        val capturer = enumerator.createCapturer(deviceName, null) ?: return
        videoCapturer = capturer

        val helper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglContext)
        surfaceTextureHelper = helper

        val source = pcFactory.createVideoSource(false)
        localVideoSource = source
        capturer.initialize(helper, appContext, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)

        val track = pcFactory.createVideoTrack("video0", source)
        localVideoTrack = track
        pc.addTrack(track, listOf("stream0"))
    }

    /** Arayan taraf — offer üretir, local description'ı set eder, SDP metnini
     * (sendSignal ile call-signal broadcast'ine konacak) döner. */
    suspend fun createOffer(): String {
        val pc = buildPeerConnection()
        peerConnection = pc
        addLocalTracks(pc)
        val offer = createSdp { observer -> pc.createOffer(observer, MediaConstraints()) }
        setLocalDescription(pc, offer)
        return offer.description
    }

    /** Aranan taraf — gelen offer'ı remote description olarak set eder,
     * answer üretir + local description'ı set eder, SDP metnini döner. */
    suspend fun createAnswerForOffer(remoteSdp: String): String {
        val pc = buildPeerConnection()
        peerConnection = pc
        addLocalTracks(pc)
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.OFFER, remoteSdp))
        flushPendingIce(pc)
        val answer = createSdp { observer -> pc.createAnswer(observer, MediaConstraints()) }
        setLocalDescription(pc, answer)
        return answer.description
    }

    /** Arayan taraf — karşı tarafın answer'ını remote description olarak set eder. */
    suspend fun setRemoteAnswer(sdp: String) {
        val pc = peerConnection ?: return
        setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        flushPendingIce(pc)
    }

    /** Web'in iceCandidateQueue'suyla AYNI mantık (bkz. call.js
     * handleIceCandidate yorumu): remote description set edilmeden ÖNCE
     * gelen adaylar kuyruklanır, flushPendingIce() ile boşaltılır. */
    fun addRemoteIceCandidate(candidate: String, sdpMLineIndex: Int, sdpMid: String?) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        val pc = peerConnection
        if (pc == null || !remoteDescriptionSet) {
            pendingRemoteIce.add(ice)
        } else {
            pc.addIceCandidate(ice)
        }
    }

    private fun flushPendingIce(pc: PeerConnection) {
        remoteDescriptionSet = true
        pendingRemoteIce.forEach { pc.addIceCandidate(it) }
        pendingRemoteIce.clear()
    }

    fun setMicEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun dispose() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            // Zaten durmuş olabilir — en iyi-çaba temizlik.
        }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        peerConnection?.close()
        peerConnection = null
        pendingRemoteIce.clear()
        remoteDescriptionSet = false
    }

    // --- SdpObserver/PeerConnection callback'lerini suspend fonksiyonlara
    // çeviren yardımcılar — AuthRepository.currentFcmToken() ile AYNI desen. ---

    private suspend fun createSdp(action: (SdpObserver) -> Unit): SessionDescription =
        suspendCancellableCoroutine { cont ->
            action(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    if (cont.isActive) cont.resume(desc)
                }
                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "SDP oluşturulamadı"))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            })
        }

    private suspend fun setLocalDescription(pc: PeerConnection, desc: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Local SDP set edilemedi"))
                }
                override fun onCreateSuccess(desc: SessionDescription) {}
                override fun onCreateFailure(error: String?) {}
            }, desc)
        }

    private suspend fun setRemoteDescription(pc: PeerConnection, desc: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Remote SDP set edilemedi"))
                }
                override fun onCreateSuccess(desc: SessionDescription) {}
                override fun onCreateFailure(error: String?) {}
            }, desc)
        }
}
