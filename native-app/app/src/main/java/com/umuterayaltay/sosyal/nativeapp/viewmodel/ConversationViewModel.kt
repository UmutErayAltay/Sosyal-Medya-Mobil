package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.CommentStickerDto
import com.umuterayaltay.sosyal.nativeapp.network.ConversationInfoDto
import com.umuterayaltay.sosyal.nativeapp.network.ForwardTargetDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageReactionDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageSearchResultDto
import com.umuterayaltay.sosyal.nativeapp.network.ReplyToDto
import com.umuterayaltay.sosyal.nativeapp.repository.ConversationDetailResult
import com.umuterayaltay.sosyal.nativeapp.repository.DeleteMessageResult
import com.umuterayaltay.sosyal.nativeapp.repository.EditMessageResult
import com.umuterayaltay.sosyal.nativeapp.repository.ForwardMessageResult
import com.umuterayaltay.sosyal.nativeapp.repository.ForwardTargetsResult
import com.umuterayaltay.sosyal.nativeapp.repository.MessageSearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.MuteConversationResult
import com.umuterayaltay.sosyal.nativeapp.repository.PinMessageResult
import com.umuterayaltay.sosyal.nativeapp.repository.ReactToMessageResult
import com.umuterayaltay.sosyal.nativeapp.repository.SendMessageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID

sealed class ConversationEvent {
    data object SessionExpired : ConversationEvent()
}

private const val POLL_INTERVAL_MS = 5000L

// 2026-08-21 (kullanıcı raporu: "sohbet açıkken bir mesaj sohbete düşmüyor
// ama bildirimi geliyor") — Realtime KANALI "SUBSCRIBED"/"CONNECTED" görünmeye
// devam edip GERÇEKTE bir INSERT'i teslim etmemiş olabilir (bkz.
// RealtimeConnectionManager.watchConnectionHealth() yorumu — SADECE bağlantı/
// kanal DURUMUNU izliyor, gerçek mesaj akışını DEĞİL, bu yüzden böyle bir
// sessiz teslim kaybını YAKALAYAMAZ ve onFailure()/polling'e hiç düşülmez).
// Kök nedeni bu ortamda canlı bir Supabase platform tanısıyla KANITLANAMADI
// — bu yüzden kesin teşhis yerine PRAGMATİK bir güvenlik ağı: Realtime
// BAŞARILI kurulduğunda BİLE, startPolling()'in 5sn'lik AGRESİF döngüsünden
// çok daha seyrek (bkz. startSafetyNetPolling()), arka planda ayrıca bir
// pollNewest() döngüsü çalışır — Realtime birincil kanal olarak KALIR, bu
// SADECE en kötü ihtimalde (sessiz teslim kaybı) mesajın er ya da geç
// (en fazla bu aralık kadar gecikmeyle) sohbete düşmesini garanti eder.
private const val SAFETY_NET_POLL_INTERVAL_MS = 15_000L

/**
 * Tek bir konuşma ekranı için ViewModel — ProfileViewModel'deki gibi constructor
 * parametreli (conversationId), bu yüzden [ConversationViewModelFactory] gerekir.
 *
 * Sayfalama: page=1 en yeni MESSAGE_PAGE mesaj (backend zaten eskiden-yeniye
 * çevirip döner), loadOlder() page++ ile bir önceki (daha eski) sayfayı çekip
 * listenin BAŞINA ekler — [messages] her zaman artan (eskiden yeniye) sırada
 * kalır, scroll pozisyonu bu yüzden loadOlder() sonrası bozulmaz.
 *
 * Canlı mesaj teslimi: ÖNCE gerçek Supabase Realtime (bkz.
 * ServiceLocator.realtimeConnectionManager / RealtimeConnectionManager) denenir
 * — init'te connectRealtimeOrPoll() bunu KURAR, başarılı olursa startPolling()
 * HİÇ ÇAĞRILMAZ. Realtime kurulumda VEYA sonradan (bağlantı koparsa/JWT
 * yenilenemezse) BAŞARISIZ olursa, RealtimeConnectionManager onFailure
 * callback'ini tetikler ve BURADAN itibaren eski (Faz 3'ten kalma) basit
 * polling devreye girer: viewModelScope içinde 5 saniyede bir page=1 tekrar
 * çekilir, o anda listede OLMAYAN id'ler (yeni gelen mesajlar) listenin
 * SONUNA eklenir; var olan mesajlara/eski sayfalara dokunulmaz. Bu dedupe/
 * append mantığı [appendFreshMessages]'ta ortaklaştırıldı — hem polling hem
 * Realtime'ın tek mesajlık teslimi AYNI helper'ı kullanır. viewModelScope,
 * ViewModel onCleared() olduğunda OTOMATİK iptal edilir (polling döngüsü
 * ayrıca elle durdurulmuyor) — ama Realtime bağlantısının WebSocket'i AYRICA
 * (elle) kapatılmalı, bkz. onCleared() override'ı.
 */
class ConversationViewModel(private val conversationId: String) : ViewModel() {

    companion object {
        // 2026-08-08 (kullanıcı raporu: "video ve fotoğraf gidiyor ama hemen
        // geri geliyor, göndermiyor") — kök neden: MultipartBody.Part.
        // createFormData()'ya sabit "message_image"/"message_video" (UZANTISIZ)
        // dosya adı veriliyordu. Backend `app/storage_helper.py::_get_extension()`
        // dosya adından uzantı çıkarır ve `ALLOWED_EXTENSIONS`/`ALLOWED_VIDEO_
        // EXTENSIONS` listesinde YOKSA (uzantısız asla listede olamaz) daha
        // magic-byte kontrolüne bile gelmeden `None` döner — her gönderim
        // SESSİZCE `upload_failed` ile reddediliyordu. CreatePostViewModel'in
        // VIDEO_MIME_TO_EXTENSION deseniyle AYNI çözüm — gerçek MIME'dan
        // gerçek uzantı üretilir. GÖRSEL için de (post akışının aksine "sabit
        // isim yeterli" varsayımı YANLIŞTI — o varsayım SADECE seçilen görsel
        // hep JPEG ise doğru, PNG/WEBP'te AYNI ext-uyuşmazlığı bug'ına düşer)
        // gerçek uzantı üretiliyor.
        private val IMAGE_MIME_TO_EXTENSION = mapOf(
            "image/png" to ".png",
            "image/jpeg" to ".jpg",
            "image/gif" to ".gif",
            "image/webp" to ".webp",
        )
        private val VIDEO_MIME_TO_EXTENSION = mapOf(
            "video/mp4" to ".mp4",
            "video/webm" to ".webm",
            "video/quicktime" to ".mov",
        )

        // 2026-08-09: sesli mesaj — MediaRecorder(OutputFormat.MPEG_4 +
        // AudioEncoder.AAC) çıktısı her zaman .m4a/audio-mp4 (backend'in
        // ALLOWED_AUDIO_MIMES listesindeki `audio/mp4`, `audio/m4a` DEĞİL —
        // bkz. MessagingRepository.sendMessage() yorumu), bu yüzden
        // görsel/video'nun AKSİNE bir MIME->uzantı EŞLEMESİNE gerek yok, TEK
        // sabit çıktı formatı var.
        private const val AUDIO_MIME_TYPE = "audio/mp4"
        private const val AUDIO_FILE_EXTENSION = ".m4a"

        // Backend'in app/storage_helper.py::MAX_FILE_SIZE'ı (5 MB) — biraz PAY
        // bırakılarak (4 MB) aşağıda sıkıştırma bu eşiğin altına düşürmeyi
        // hedefler (tam 5 MB'a kadar sıkıştırıp backend'in KENDİ >5MB kontrolüne
        // yine takılma riskini azaltmak için).
        private const val MAX_IMAGE_UPLOAD_BYTES = 4 * 1024 * 1024
    }

    private val messagingRepository = ServiceLocator.messagingRepository
    private val authRepository = ServiceLocator.authRepository
    private val tokenStore = ServiceLocator.tokenStore

    private var page = 1

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _conversationInfo = MutableStateFlow<ConversationInfoDto?>(null)
    val conversationInfo: StateFlow<ConversationInfoDto?> = _conversationInfo.asStateFlow()

    private val _sendText = MutableStateFlow("")
    val sendText: StateFlow<String> = _sendText.asStateFlow()

    private val _replyingTo = MutableStateFlow<MessageDto?>(null)
    val replyingTo: StateFlow<MessageDto?> = _replyingTo.asStateFlow()

    // Gönderilecek görsel (opsiyonel) — CreatePostViewModel._selectedImageUri
    // ile AYNI desen (Photo Picker'dan seçilen Uri, gönderilene/iptal edilene
    // kadar burada tutulur).
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    // 2026-08-08: gönderilecek video (opsiyonel) — selectedImageUri ile AYNI
    // desen, AYRI bir state (bir mesajda ikisi birden TEORİK olarak mümkün
    // ama UI şu an tek seferde birini seçtirir — bkz. ConversationInputBar).
    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    // Sesli mesaj kaydı (2026-08-09) — WhatsApp benzeri "basılı tut, bırakınca
    // gönder" akışı. Görsel/video'nun AKSİNE bir Uri SEÇİMİ değil, AKTİF bir
    // kayıt SÜRECİ — bu yüzden ayrı state'ler: [isRecordingAudio] UI'ın mikrofon
    // butonunu "kayıt oluyor" görünümüne geçirmesi için, [recordingElapsedMs]
    // canlı süre göstergesi için (her saniye güncellenir).
    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingTimerJob: Job? = null

    // Mesaj balonunu ben/karşı taraf olarak hizalamak için — AuthRepository.
    // getCurrentUser() (Faz 3 Profil'de eklendi) ile BİR KEZ çözülüp burada
    // cache'lenir. Kıyaslama username DEĞİL id ile yapılır: messages.sender_id
    // zaten user id'si — profiles embed'i her mesajda dolu gelmeyebileceği
    // (ör. profil silinmiş) için id kıyası username'e göre daha güvenilir
    // (spesifikasyondan BİLİNÇLİ küçük bir sapma, gerekçesi budur).
    private val _myUserId = MutableStateFlow<String?>(null)
    val myUserId: StateFlow<String?> = _myUserId.asStateFlow()

    private val _events = MutableSharedFlow<ConversationEvent>()
    val events: SharedFlow<ConversationEvent> = _events

    // ---- Faz 5 Dalga 1B: mesaj gelişmiş işlemleri (düzenle/sil/tepki/
    // sabitle/ilet/sessize al/ara) — state'ler AŞAĞIDA, fonksiyonlar sınıfın
    // SONUNDA ayrı bir bölgede toplandı.

    /** Sohbette SABİTLİ (pinned_at != null) EN SON işaretlenen mesaj — banner
     * için. Birden fazla mesaj teorik olarak sabitli olabilir (her satırın
     * kendi pinned_at'i var) ama v1 UI'da TEK banner gösterildiği için en
     * güncel pinned_at'e sahip olan seçilir. */
    private val _pinnedMessage = MutableStateFlow<MessageDto?>(null)
    val pinnedMessage: StateFlow<MessageDto?> = _pinnedMessage.asStateFlow()

    /** Uzun-basılan (aksiyon sheet'i açık olan) mesaj — null iken sheet kapalı. */
    private val _selectedMessage = MutableStateFlow<MessageDto?>(null)
    val selectedMessage: StateFlow<MessageDto?> = _selectedMessage.asStateFlow()

    private val _forwardTargets = MutableStateFlow<List<ForwardTargetDto>>(emptyList())
    val forwardTargets: StateFlow<List<ForwardTargetDto>> = _forwardTargets.asStateFlow()

    // Boş forwardTargets ile "henüz yüklenmedi" durumunu ayırt etmek için —
    // ForwardTargetScreen ilk açıldığında "hiç konuşman yok" yerine yükleniyor
    // göstersin diye.
    private val _forwardTargetsLoading = MutableStateFlow(false)
    val forwardTargetsLoading: StateFlow<Boolean> = _forwardTargetsLoading.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MessageSearchResultDto>>(emptyList())
    val searchResults: StateFlow<List<MessageSearchResultDto>> = _searchResults.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    // NOT: sunucunun GERÇEK is_muted durumu api_message_conversation_detail()'in
    // "conversation" alanında zaten dönüyor (bkz. backend docstring'i) AMA
    // ConversationInfoDto bu DTO'ya EKLENMEDİ — bu ajanın ApiModels.kt'de
    // dokunma yetkisi SADECE FAZ5-1B bölgesi + MessageDto ile sınırlı,
    // ConversationInfoDto başka bir bölgede tanımlı. Bu yüzden [_isMuted]
    // ekran açılışında HER ZAMAN false ile başlar, SADECE bu oturumda
    // toggleMute() çağrılırsa sunucudan dönen gerçek değerle güncellenir —
    // bilinen v1 sınırı (başka bir cihazdan sessize alınmışsa bu ekran
    // açılışta yansıtmaz).
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    init {
        resolveMyUserId()
        loadInitial()
        connectRealtimeOrPoll()
    }

    // startSafetyNetPolling()'in Job'ı — onFailure() sonradan tetiklenirse
    // (Realtime KURULUP SONRA koparsa) startPolling()'in kendi 5sn'lik
    // döngüsüyle GEREKSİZ yere çakışmasın diye iptal edilir (bkz. SAFETY_NET_POLL_INTERVAL_MS
    // yorumu — appendFreshMessages() idempotent olduğu için çakışsa da
    // ZARARSIZ olurdu, ama iptal etmek temiz).
    private var safetyNetPollJob: Job? = null

    /** Bkz. sınıf yorumu — ÖNCE Realtime dener, başarısız olursa (kurulumda
     * VEYA sonradan) [onFailure] üzerinden startPolling()'e düşer. Realtime
     * BAŞARIYLA kurulursa da SAFETY_NET_POLL_INTERVAL_MS yorumundaki
     * gerekçeyle startSafetyNetPolling() AYRICA başlatılır. */
    private fun connectRealtimeOrPoll() {
        viewModelScope.launch {
            var connectSucceeded = true
            ServiceLocator.realtimeConnectionManager.connect(
                conversationId = conversationId,
                onNewMessage = { message -> appendFreshMessages(listOf(message)) },
                onFailure = {
                    connectSucceeded = false
                    safetyNetPollJob?.cancel()
                    startPolling()
                },
            )
            // connect() suspend bir fonksiyon — kurulum aşamasında BAŞARISIZ
            // olduysa onFailure yukarıda ZATEN çağrılmış (connectSucceeded=false)
            // olur, bu satıra o durumda da gelinir (connect() erken return eder,
            // exception fırlatmaz) — bu yüzden burada AYRICA kontrol şart.
            if (connectSucceeded) startSafetyNetPolling()
        }
    }

    private fun startSafetyNetPolling() {
        safetyNetPollJob = viewModelScope.launch {
            while (true) {
                delay(SAFETY_NET_POLL_INTERVAL_MS)
                pollNewest()
            }
        }
    }

    private fun resolveMyUserId() {
        viewModelScope.launch {
            _myUserId.value = authRepository.getCurrentUser()?.id
        }
    }

    fun loadInitial() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            page = 1
            when (val result = messagingRepository.getConversationDetail(conversationId, page)) {
                is ConversationDetailResult.Success -> {
                    _messages.value = result.messages
                    _hasMore.value = result.hasMore
                    _conversationInfo.value = result.conversation
                    // Faz 5 Dalga 1B: sabitli mesaj banner'ı — ilk yüklemede
                    // gelen sayfadaki (page=1, en yeni MESSAGE_PAGE mesaj)
                    // pinned_at'i dolu satırlardan EN GÜNCELİ seçilir. Daha
                    // eski bir sayfada (loadOlder ile gelen) sabitli bir mesaj
                    // varsa bu v1'de YAKALANMAZ (bilinen sınır, banner'ın
                    // amacı zaten "şu an görünen sohbetin özeti").
                    _pinnedMessage.value = result.messages
                        .filter { it.pinnedAt != null }
                        .maxByOrNull { it.pinnedAt ?: "" }
                    // Sunucu zaten page=1 çekilince arka planda okundu isaretliyor
                    // (bkz. app/api_v1.py docstring'i) - burada AYRICA mark-read
                    // cagirmak zararsiz/tutarlilik icin (ekran acildiginda kesin
                    // okundu isaretlensin diye), sonucu beklenmiyor.
                    markRead()
                }
                is ConversationDetailResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun loadOlder() {
        if (_loadingOlder.value || _loading.value || !_hasMore.value) return
        viewModelScope.launch {
            _loadingOlder.value = true
            val nextPage = page + 1
            when (val result = messagingRepository.getConversationDetail(conversationId, nextPage)) {
                is ConversationDetailResult.Success -> {
                    page = nextPage
                    _hasMore.value = result.hasMore
                    _messages.value = result.messages + _messages.value
                }
                is ConversationDetailResult.Error -> handleError(result.code, silent = true)
            }
            _loadingOlder.value = false
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                pollNewest()
            }
        }
    }

    private suspend fun pollNewest() {
        when (val result = messagingRepository.getConversationDetail(conversationId, 1)) {
            is ConversationDetailResult.Success -> appendFreshMessages(result.messages)
            is ConversationDetailResult.Error -> {
                // Polling hatası kullanıcıyı rahatsız etmesin (arka planda sessizce
                // yeniden dener) — SADECE 401'de oturum sonlandırılır.
                if (result.code == "unauthorized") {
                    tokenStore.clearToken()
                    _events.emit(ConversationEvent.SessionExpired)
                }
            }
        }
    }

    /** pollNewest() (sayfa=1 tekrar çekimi) VE Realtime'ın tek mesajlık INSERT
     * teslimi (bkz. RealtimeConnectionManager.onNewMessage) AYNI "listede yoksa
     * sona ekle" (id bazlı dedupe) mantığını kullanır — kod tekrarını önlemek
     * için burada ortaklaştırıldı. Realtime tarafı Dispatchers.IO'dan (ViewModel'in
     * ana thread'i DIŞINDA) çağrılabilir — StateFlow.value ataması thread-safe
     * olduğu için bu fonksiyon suspend/main-confined OLMAK ZORUNDA değil. */
    private fun appendFreshMessages(candidates: List<MessageDto>) {
        val existingIds = _messages.value.mapTo(HashSet()) { it.id }
        val freshOnes = candidates.filter { it.id !in existingIds }
        // Faz 5 Dalga 1B NOTU: candidates'taki, listede ZATEN olan id'ler için
        // de en güncel satır devreye alınır — böylece BAŞKA bir cihazdan
        // yapılmış düzenle/tepki/sabitle/silme (bu fonksiyonun ASIL amacı olan
        // "yeni mesaj" senaryosunun DIŞINDaki değişiklikler) bir sonraki
        // pollNewest() turunda yakalanır. Bu ekranın KENDİ aksiyonları zaten
        // kendi response'undan optimistic güncelleme yapıyor (editMessage/
        // reactToMessage/pinMessage/deleteMessage) — buna ihtiyaç duymuyor.
        // SINIR: Realtime bağlantısı KURULU iken (bkz. connectRealtimeOrPoll)
        // pollNewest() hiç ÇAĞRILMAZ, Realtime'ın tek mesajlık teslimi SADECE
        // yeni INSERT taşır — bu durumda başka bir cihazdan gelen bir DEĞİŞİKLİK
        // (edit/react/pin, insert değil) bu oturuma hiç yansımaz. Bilinçli v1
        // kabulü: bu senaryo nadir (aynı sohbeti aynı anda iki cihazdan
        // düzenlemek) ve çözümü (mesaj güncellemelerini de Realtime'a taşımak)
        // kapsam dışı.
        val updates = candidates.filter { it.id in existingIds }.associateBy { it.id }
        if (freshOnes.isEmpty() && updates.isEmpty()) return
        _messages.value = _messages.value.map { updates[it.id] ?: it } + freshOnes
        if (updates.isNotEmpty()) {
            _pinnedMessage.value = _messages.value
                .filter { it.pinnedAt != null }
                .maxByOrNull { it.pinnedAt ?: "" }
        }
    }

    fun onSendTextChange(text: String) {
        _sendText.value = text
    }

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        if (uri != null) _selectedVideoUri.value = null
    }

    fun onVideoSelected(uri: Uri?) {
        _selectedVideoUri.value = uri
        if (uri != null) _selectedImageUri.value = null
    }

    /** Mikrofon butonuna BASILI TUTULUNCA çağrılır — [context] SADECE geçici
     * kayıt dosyasının cache dizinini bulmak için kullanılır. `Build.VERSION_
     * CODES.S`+ `MediaRecorder(Context)` yapıcısı ZORUNLU (parametresiz olan
     * API 31'de deprecated) — CreatePostViewModel'deki MIME->uzantı eşlemesi
     * GEREKMEZ, MediaRecorder çıktısı HER ZAMAN [AUDIO_MIME_TYPE]/
     * [AUDIO_FILE_EXTENSION] (bkz. companion object yorumu). Zaten kayıt
     * SÜRERKEN (`_isRecordingAudio` true) tekrar çağrılırsa no-op — buton
     * her `onPress` de bunu tetiklemesin diye çağıran taraf (UI) zaten
     * `detectTapGestures`/pointerInput ile TEK seferlik başlatmalı, ama BURADA
     * da bir ikinci güvence.
     */
    fun startRecording(context: Context) {
        if (_isRecordingAudio.value) return
        val file = File(context.cacheDir, "voice_${UUID.randomUUID()}$AUDIO_FILE_EXTENSION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Mikrofon başka bir uygulama tarafından kullanılıyor olabilir
            // (nadir ama gerçek bir senaryo) — sessizce vazgeç, kullanıcı
            // butona tekrar basabilir.
            recorder.release()
            return
        }
        mediaRecorder = recorder
        recordingFile = file
        _recordingElapsedMs.value = 0L
        _isRecordingAudio.value = true
        recordingTimerJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                delay(200)
                _recordingElapsedMs.value = System.currentTimeMillis() - startedAt
            }
        }
    }

    /** Mikrofon butonu BIRAKILINCA çağrılır — kaydı durdurur VE HEMEN gönderir
     * (WhatsApp'ın "bas-konuş-bırak-gönderilir" akışıyla AYNI, ayrı bir onay/
     * gönder adımı YOK). [context] send()'deki AYNI gerekçeyle gerekli
     * (ContentResolver DEĞİL burada — doğrudan File — ama tutarlılık için
     * aynı imza). Çok kısa (< 500ms, yanlışlıkla dokunma) kayıtlar sessizce
     * İPTAL edilir, backend'e boş/anlamsız bir ses dosyası gönderilmez. */
    fun stopRecordingAndSend(context: Context) {
        val recorder = mediaRecorder ?: return
        val file = recordingFile
        val elapsed = _recordingElapsedMs.value
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        mediaRecorder = null
        recordingFile = null
        _isRecordingAudio.value = false
        _recordingElapsedMs.value = 0L
        try {
            recorder.stop()
        } catch (e: Exception) {
            // start() sonrası hiç veri gelmeden stop() edilirse (çok kısa
            // basma) IllegalStateException fırlatabilir — dosya zaten
            // anlamsız, aşağıdaki elapsed<500ms kontrolüyle ZATEN atlanacaktı.
        }
        recorder.release()
        if (elapsed < 500L || file == null) {
            file?.delete()
            return
        }
        sendRecordedAudio(file)
    }

    /** Kullanıcı kaydı İPTAL ederse (ör. gönder istemediği bir kayıt) —
     * dosya silinir, HİÇBİR mesaj gönderilmez. */
    fun cancelRecording() {
        val recorder = mediaRecorder ?: return
        val file = recordingFile
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        mediaRecorder = null
        recordingFile = null
        _isRecordingAudio.value = false
        _recordingElapsedMs.value = 0L
        try {
            recorder.stop()
        } catch (e: Exception) {
            // Yukarıdaki stopRecordingAndSend ile AYNI gerekçe.
        }
        recorder.release()
        file?.delete()
    }

    /** [send]'deki optimistic-mesaj deseninin sesli mesaja UYARLANMIŞ hali —
     * görsel/video Photo Picker Uri'sinden (uygulamanın erişimi SÜREKLİ)
     * FARKLI olarak burada kaynak kendi ürettiğimiz bir cache dosyası, bu
     * yüzden ayrı bir fonksiyon (send()'in içine gömmek yerine): metin/reply/
     * diğer medya state'leriyle KARIŞMASIN, sesli mesaj TEK BAŞINA gönderilir
     * (backend zaten mutually-exclusive bir kural DAYATMIYOR ama UI'da SES
     * kaydı SIRASINDA zaten metin girişi/diğer medya seçimi ANLAMSIZ). */
    private fun sendRecordedAudio(file: File) {
        val tempId = "local-${UUID.randomUUID()}"
        val localUri = Uri.fromFile(file).toString()
        val optimisticMessage = MessageDto(
            id = tempId,
            senderId = _myUserId.value ?: "",
            content = "",
            replyToId = null,
            readAt = null,
            createdAt = Instant.now().toString(),
            profiles = null,
            replyTo = null,
            reactions = null,
            sticker = null,
            imageUrl = null,
            videoUrl = null,
            audioUrl = localUri,
        )
        _messages.value = _messages.value + optimisticMessage

        viewModelScope.launch {
            val audioBytes = try {
                withContext(Dispatchers.IO) { file.readBytes() }
            } catch (e: Exception) {
                removeOptimisticMessage(tempId)
                _error.value = "Sesli mesaj okunamadı, lütfen tekrar deneyin"
                return@launch
            } finally {
                file.delete()
            }

            when (
                val result = messagingRepository.sendMessage(
                    conversationId = conversationId,
                    content = "",
                    replyToId = null,
                    imageBytes = null,
                    imageMimeType = null,
                    audioBytes = audioBytes,
                    audioMimeType = AUDIO_MIME_TYPE,
                    audioFileName = "upload$AUDIO_FILE_EXTENSION",
                )
            ) {
                is SendMessageResult.Success -> replaceOptimisticMessage(tempId, result.message)
                is SendMessageResult.Error -> {
                    removeOptimisticMessage(tempId)
                    if (result.code == "unauthorized") {
                        handleError(result.code)
                    } else {
                        _error.value = "Sesli mesaj gönderilemedi, lütfen tekrar deneyin"
                    }
                }
            }
        }
    }

    /** [context] SADECE seçilen görsel Uri'sinin byte'larını/mime tipini okumak
     * için gerekiyor (ContentResolver) — ViewModel Context'i SAKLAMAZ, sadece
     * bu tek çağrı sırasında kullanır (CreatePostViewModel.submit() ile AYNI
     * desen/gerekçe).
     *
     * OPTIMISTIC gönderim (web'in "mesaj göndermeyi sıraya alma" — anında
     * sohbete yansıtıp gerçek gönderimi arka planda yapma — deneyimiyle
     * tutarlı olsun diye, kullanıcı geri bildirimi üzerine eklendi): metin
     * kutusu/görsel seçimi TIKLANIR TIKLANMAZ temizlenir ve "local-" önekli
     * geçici bir id'yle mesaj HEMEN listeye eklenir — ağ isteği bittiğinde
     * (appendFreshMessages'ın AKSİNE, id burada henüz sunucudan gelmediği
     * için) bu geçici satır [replaceOptimisticMessage] ile sunucunun
     * döndürdüğü GERÇEK mesajla YERİNDE değiştirilir (pozisyon bozulmaz).
     * Gönderim BAŞARISIZ olursa geçici satır listeden çıkarılır, metin/görsel
     * kullanıcı kaybetmesin diye GERİ YÜKLENİR ve hata (sessiz DEĞİL, kullanıcı
     * fark etmeli) gösterilir. Görsel yükleme sürerken geçici balonun
     * imageUrl'i BİLEREK null bırakılır (yerel content:// URI'sini MessageDto's
     * String alanına güvenilir biçimde koymak yerine basit tutuldu) — görsel,
     * gerçek mesaj gelince balon üzerinde belirir.
     *
     * [gifUrl]/[stickerId] (Faz 5 Dalga 3B, MediaPickerSheet'ten) — GİF zaten
     * kalıcı bir Klipy CDN URL'si olduğu için optimistic balonun imageUrl'ine
     * DOĞRUDAN konur, hemen görünür. [stickerImageUrl] 2026-08-08 (kullanıcı
     * raporu: "çıkartma direkt yüklenmiyor, bi süre sonra geliyor") eklendi —
     * MediaPickerSheet artık seçilen sticker'ın TAM DTO'sunu (id + imageUrl)
     * verdiği için optimistic balon da sticker'ı GERÇEK mesaj gelmeden
     * gösterebiliyor. Seçilen GÖRSEL (Photo Picker) de AYNI gerekçeyle artık
     * `imageUri.toString()` (content:// URI, Coil DOĞRUDAN çözebilir) ile
     * ANINDA gösteriliyor — önceden yüklenene kadar boş kalıyordu, kullanıcı
     * "fotoğraf gönderilmiyor" sanıyordu. Seçilen VİDEO (2026-08-08) da AYNI
     * mantıkla `videoUri.toString()` ile ANINDA gösterilir — ExoPlayer content://
     * URI'yi DOĞRUDAN oynatabilir, yükleme bitmesini beklemeye gerek yok.
     */
    fun send(
        context: Context,
        gifUrl: String? = null,
        stickerId: String? = null,
        stickerImageUrl: String? = null,
    ) {
        val content = _sendText.value.trim()
        val imageUri = _selectedImageUri.value
        val videoUri = _selectedVideoUri.value
        if (content.isEmpty() && imageUri == null && videoUri == null &&
            gifUrl.isNullOrBlank() && stickerId.isNullOrBlank()
        ) {
            return
        }
        val replyingTo = _replyingTo.value
        val replyId = replyingTo?.id

        val tempId = "local-${UUID.randomUUID()}"
        val optimisticMessage = MessageDto(
            id = tempId,
            senderId = _myUserId.value ?: "",
            content = content,
            replyToId = replyId,
            readAt = null,
            createdAt = Instant.now().toString(),
            profiles = null,
            replyTo = replyingTo?.let {
                ReplyToDto(
                    id = it.id,
                    content = it.content,
                    imageUrl = it.imageUrl,
                    senderId = it.senderId,
                    profiles = it.profiles,
                )
            },
            reactions = null,
            sticker = stickerId?.let { id -> CommentStickerDto(id = id, imageUrl = stickerImageUrl) },
            imageUrl = gifUrl ?: imageUri?.toString(),
            videoUrl = videoUri?.toString(),
        )

        // Input HEMEN temizlenir — "gönderdim" hissi ağ isteği bitmeden verilir.
        _messages.value = _messages.value + optimisticMessage
        _sendText.value = ""
        _replyingTo.value = null
        _selectedImageUri.value = null
        _selectedVideoUri.value = null

        viewModelScope.launch {
            var imageBytes: ByteArray? = null
            var imageMimeType: String? = null
            var imageFileName: String? = null
            if (imageUri != null) {
                imageMimeType = context.contentResolver.getType(imageUri)
                val imageExt = IMAGE_MIME_TO_EXTENSION[imageMimeType]
                if (imageExt == null) {
                    removeOptimisticMessage(tempId)
                    restoreFailedSend(content, replyingTo, imageUri, videoUri)
                    _error.value = "Desteklenmeyen görsel formatı (png/jpeg/gif/webp kullanın)"
                    return@launch
                }
                imageFileName = "upload$imageExt"
                try {
                    withContext(Dispatchers.IO) {
                        val rawBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                        // 2026-08-22 (kullanıcı raporu: "kamerayla çekilen fotoğraf
                        // gönderiliyor gibi oluyor ama geri geliyor, göndermiyor") —
                        // kök neden izin DEĞİL, boyuttu: backend'in upload_image()'ı
                        // (app/storage_helper.py MAX_FILE_SIZE=5MB) tam çözünürlüklü
                        // kamera JPEG'lerini (genelde 8-15MB) SESSİZCE "upload_failed"
                        // ile reddediyordu — galeriden seçilen (genelde zaten küçük/
                        // sıkıştırılmış) görsellerde bu hiç görülmüyordu. GIF hariç
                        // (animasyon kaybolur) 5MB sınırının altına düşürülene kadar
                        // JPEG kalitesi/boyutu kademeli azaltılır.
                        if (rawBytes != null && rawBytes.size > MAX_IMAGE_UPLOAD_BYTES && imageMimeType != "image/gif") {
                            val (compressed, compressedMime) = compressImageForUpload(rawBytes)
                            imageBytes = compressed
                            imageMimeType = compressedMime
                            imageFileName = "upload${IMAGE_MIME_TO_EXTENSION[compressedMime] ?: ".jpg"}"
                        } else {
                            imageBytes = rawBytes
                        }
                    }
                } catch (e: Exception) {
                    removeOptimisticMessage(tempId)
                    restoreFailedSend(content, replyingTo, imageUri, videoUri)
                    _error.value = "Görsel okunamadı, lütfen tekrar deneyin"
                    return@launch
                }
            }

            var videoBytes: ByteArray? = null
            var videoMimeType: String? = null
            var videoFileName: String? = null
            if (videoUri != null) {
                videoMimeType = context.contentResolver.getType(videoUri)
                val videoExt = VIDEO_MIME_TO_EXTENSION[videoMimeType]
                if (videoExt == null) {
                    removeOptimisticMessage(tempId)
                    restoreFailedSend(content, replyingTo, imageUri, videoUri)
                    _error.value = "Desteklenmeyen video formatı (mp4/webm/mov kullanın)"
                    return@launch
                }
                videoFileName = "upload$videoExt"
                try {
                    withContext(Dispatchers.IO) {
                        videoBytes = context.contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    removeOptimisticMessage(tempId)
                    restoreFailedSend(content, replyingTo, imageUri, videoUri)
                    _error.value = "Video okunamadı, lütfen tekrar deneyin"
                    return@launch
                }
            }

            when (
                val result = messagingRepository.sendMessage(
                    conversationId,
                    content,
                    replyId,
                    imageBytes,
                    imageMimeType,
                    stickerId,
                    gifUrl,
                    videoBytes,
                    videoMimeType,
                    imageFileName,
                    videoFileName,
                )
            ) {
                is SendMessageResult.Success -> replaceOptimisticMessage(tempId, result.message)
                is SendMessageResult.Error -> {
                    removeOptimisticMessage(tempId)
                    if (result.code == "unauthorized") {
                        handleError(result.code)
                    } else {
                        restoreFailedSend(content, replyingTo, imageUri, videoUri)
                        handleError(result.code)
                    }
                }
            }
        }
    }

    /** [MAX_IMAGE_UPLOAD_BYTES]'ı aşan bir görseli EXIF yönünü koruyarak JPEG'e
     * kademeli kalite/boyut düşürmesiyle sıkıştırır — tam çözünürlüklü kamera
     * fotoğrafları (8-15 MB) backend'in 5 MB sınırına SESSİZCE takılıp mesajı
     * geri getiriyordu (bkz. send()'teki çağrı yeri yorumu). Çağıran taraf zaten
     * `Dispatchers.IO`'da (bkz. send()) — burada AYRICA withContext YOK. */
    private fun compressImageForUpload(original: ByteArray): Pair<ByteArray, String> {
        val exifOrientation = try {
            ExifInterface(ByteArrayInputStream(original))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val decoded = BitmapFactory.decodeByteArray(original, 0, original.size) ?: return original to "image/jpeg"
        val rotationDegrees = when (exifOrientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        var bitmap = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        // 1) Önce KALİTE kademeli düşürülür (boyut/keskinlik korunur).
        var quality = 90
        var encoded = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, this) }.toByteArray()
        while (encoded.size > MAX_IMAGE_UPLOAD_BYTES && quality > 30) {
            quality -= 15
            encoded = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, this) }.toByteArray()
        }

        // 2) Kalite tek başına yetmediyse (ör. çok yüksek çözünürlük) boyut da
        // kademeli küçültülür — %75'in altına düşülmez (görünürlük bozulmasın).
        var scale = 1f
        while (encoded.size > MAX_IMAGE_UPLOAD_BYTES && scale > 0.4f) {
            scale -= 0.15f
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            encoded = ByteArrayOutputStream().apply { scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
            bitmap = scaledBitmap
        }

        return encoded to "image/jpeg"
    }

    /** Geçici (local-) satırı sunucunun döndürdüğü GERÇEK mesajla, listedeki
     * AYNI pozisyonda değiştirir — sona eklemek yerine map ile yer değiştirmek
     * mesajın gönderim sırasındaki konumunu korur. */
    private fun replaceOptimisticMessage(tempId: String, real: MessageDto) {
        _messages.value = _messages.value.map { if (it.id == tempId) real else it }
    }

    private fun removeOptimisticMessage(tempId: String) {
        _messages.value = _messages.value.filterNot { it.id == tempId }
    }

    /** Gönderim başarısız olunca kullanıcı yazdığını/seçtiği görseli/yanıt
     * bağlamını KAYBETMESİN diye giriş alanlarını geri yükler (silinen
     * optimistic satırın YERİNE hiçbir şey konmaz — kullanıcı "Gönder"e
     * tekrar basarak yeniden dener). */
    private fun restoreFailedSend(content: String, replyingTo: MessageDto?, imageUri: Uri?, videoUri: Uri? = null) {
        _sendText.value = content
        _replyingTo.value = replyingTo
        _selectedImageUri.value = imageUri
        _selectedVideoUri.value = videoUri
    }

    fun setReplyingTo(message: MessageDto) {
        _replyingTo.value = message
    }

    fun clearReplyingTo() {
        _replyingTo.value = null
    }

    private fun markRead() {
        viewModelScope.launch {
            messagingRepository.markRead(conversationId)
        }
    }

    // ---- Faz 5 Dalga 1B: mesaj gelişmiş işlemleri ----

    fun selectMessage(message: MessageDto) {
        _selectedMessage.value = message
    }

    fun clearSelectedMessage() {
        _selectedMessage.value = null
    }

    /** SADECE gönderen (backend zaten sender_id=me filtresiyle uyguluyor,
     * burada AYRICA bir ön-kontrol yapılmıyor — tek doğruluk kaynağı backend,
     * sendMessage()'daki AYNI gerekçe). Başarılı olunca listedeki satır
     * YERİNDE güncellenir (replaceOptimisticMessage'daki AYNI .map deseni) —
     * scroll pozisyonu bozulmaz. */
    fun editMessage(messageId: String, content: String) {
        viewModelScope.launch {
            when (val result = messagingRepository.editMessage(messageId, content)) {
                is EditMessageResult.Success -> {
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(content = result.content, editedAt = result.editedAt) else it
                    }
                    clearSelectedMessage()
                }
                is EditMessageResult.Error -> handleError(result.code)
            }
        }
    }

    /** Backend HER ZAMAN {ok:true} döner (bkz. MessagingRepository.deleteMessage
     * yorumu) — bu yüzden burada "silinmedi" dalı yok, sadece ağ hatası. */
    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = messagingRepository.deleteMessage(messageId)) {
                is DeleteMessageResult.Success -> {
                    _messages.value = _messages.value.filterNot { it.id == messageId }
                    if (_pinnedMessage.value?.id == messageId) {
                        _pinnedMessage.value = _messages.value
                            .filter { it.pinnedAt != null }
                            .maxByOrNull { it.pinnedAt ?: "" }
                    }
                    clearSelectedMessage()
                }
                is DeleteMessageResult.Error -> handleError(result.code)
            }
        }
    }

    /** Backend SADECE çağıranın kendi tepkisini döner (aggregate reaction
     * listesini DEĞİL) — bu yüzden yerel [MessageReactionDto] listesi TAM
     * sunucu verisiyle değiştirilmez, [applyReactionUpdate] ile SADECE
     * kendi tepkim (mine=true olan satır) yerinde güncellenir/silinir/eklenir,
     * başkalarının tepkileri (mine=false satırlar) olduğu gibi bırakılır. */
    fun reactToMessage(messageId: String, reaction: String) {
        viewModelScope.launch {
            when (val result = messagingRepository.reactToMessage(messageId, reaction)) {
                is ReactToMessageResult.Success -> {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(reactions = applyReactionUpdate(msg.reactions, result.reaction))
                        } else {
                            msg
                        }
                    }
                    clearSelectedMessage()
                }
                is ReactToMessageResult.Error -> {
                    // react'ın 404'ü web'deki AYNI bilinçli enumeration
                    // koruması (katılımcı değilsen de 404) — burada
                    // "düzeltilmez", sadece genel bir hata mesajına çevrilir
                    // (handleError'daki varsayılan dal zaten bunu yapıyor).
                    handleError(result.code)
                }
            }
        }
    }

    private fun applyReactionUpdate(
        current: List<MessageReactionDto>?,
        newReaction: String?,
    ): List<MessageReactionDto> {
        val list = (current ?: emptyList()).toMutableList()
        val mineIndex = list.indexOfFirst { it.mine }
        if (mineIndex >= 0) {
            val mine = list[mineIndex]
            val decremented = mine.count - 1
            if (decremented <= 0) {
                list.removeAt(mineIndex)
            } else {
                list[mineIndex] = mine.copy(count = decremented, mine = false)
            }
        }
        if (newReaction != null) {
            val existingIndex = list.indexOfFirst { it.reaction == newReaction }
            if (existingIndex >= 0) {
                val existing = list[existingIndex]
                list[existingIndex] = existing.copy(count = existing.count + 1, mine = true)
            } else {
                list.add(MessageReactionDto(reaction = newReaction, count = 1, mine = true))
            }
        }
        return list
    }

    /** Yetki GÖNDEREN değil konuşmanın HERHANGİ bir katılımcısı (bkz.
     * ApiModels.kt PinResponse yorumu) — bu yüzden pin aksiyonu MessageActionsSheet'te
     * isMine şartı ARANMADAN gösterilir. */
    fun pinMessage(messageId: String) {
        viewModelScope.launch {
            when (val result = messagingRepository.pinMessage(messageId)) {
                is PinMessageResult.Success -> {
                    val now = Instant.now().toString()
                    _messages.value = _messages.value.map {
                        if (it.id == messageId) it.copy(pinnedAt = if (result.pinned) now else null) else it
                    }
                    _pinnedMessage.value = if (result.pinned) {
                        _messages.value.firstOrNull { it.id == messageId }
                    } else if (_pinnedMessage.value?.id == messageId) {
                        null
                    } else {
                        _pinnedMessage.value
                    }
                    clearSelectedMessage()
                }
                is PinMessageResult.Error -> handleError(result.code)
            }
        }
    }

    /** İletme hedefleri listesi (TÜM konuşmalar) — "İlet" sheet'i açılırken
     * çağrılır, sonuç [forwardTargets]'a yazılır. */
    fun loadForwardTargets() {
        viewModelScope.launch {
            _forwardTargetsLoading.value = true
            when (val result = messagingRepository.getForwardTargets()) {
                is ForwardTargetsResult.Success -> _forwardTargets.value = result.targets
                is ForwardTargetsResult.Error -> handleError(result.code, silent = true)
            }
            _forwardTargetsLoading.value = false
        }
    }

    /** Hedef BAŞKA bir konuşma olduğu için (bu ekranın kendi [_messages]
     * listesine YENİ bir satır EKLENMEZ) — sadece başarı/hata durumu
     * [error] üzerinden yansıtılır, çağıran taraf (ConversationScreen)
     * kapanış/geri bildirimi kendi UI state'inde yönetir. */
    fun forwardMessage(messageId: String, targetConversationId: String) {
        viewModelScope.launch {
            when (val result = messagingRepository.forwardMessage(messageId, targetConversationId)) {
                is ForwardMessageResult.Success -> clearSelectedMessage()
                is ForwardMessageResult.Error -> handleError(result.code)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            when (val result = messagingRepository.muteConversation(conversationId)) {
                is MuteConversationResult.Success -> _isMuted.value = result.muted
                is MuteConversationResult.Error -> handleError(result.code)
            }
        }
    }

    /** Sohbet-içi arama — [MessageSearchResultDto] listesindeki conversationId
     * her zaman null gelir (backend with_conversation=False), bu ekranın
     * KENDİ sohbeti zaten belli. Boş sorguda sonuç listesi temizlenir. */
    fun searchInConversation(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchLoading.value = true
            when (val result = messagingRepository.searchInConversation(conversationId, trimmed, 0)) {
                is MessageSearchResult.Success -> _searchResults.value = result.results
                is MessageSearchResult.Error -> handleError(result.code, silent = true)
            }
            _searchLoading.value = false
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(ConversationEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = when (code) {
            "forbidden" -> "Bu konuşmaya erişiminiz yok"
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> "Mesajlar yüklenemedi, lütfen tekrar deneyin"
        }
    }

    /** ExoPlayer.release() ile AYNI disiplin: Realtime WebSocket bağlantısı
     * ELLE kapatılmazsa sızar (viewModelScope'un otomatik iptali sadece BİZİM
     * coroutine'lerimizi durdurur, RealtimeConnectionManager'ın kendi iç
     * bağlantısını DEĞİL — bkz. o sınıfın disconnect() yorumu). onCleared()
     * SUSPEND bir fonksiyon DEĞİL ve viewModelScope bu noktada ZATEN iptal
     * edilmiş durumda (ViewModel'in kendi iç mekanizması onCleared()'dan ÖNCE
     * closeable'ları kapatıyor) — bu yüzden disconnect()'i çağırabilmek için
     * AYRI, kısa ömürlü bir scope kullanılıyor. */
    override fun onCleared() {
        super.onCleared()
        // Ekran kayıt SÜRERKEN kapatılırsa (nadir — geri tuşu/uygulama arkaya
        // atma) MediaRecorder sızmasın diye en iyi-çaba temizlik.
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // stopRecordingAndSend()'deki AYNI gerekçe.
        }
        mediaRecorder?.release()
        recordingFile?.delete()
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.realtimeConnectionManager.disconnect(conversationId)
        }
    }
}

/** ConversationViewModel constructor'ı conversationId parametresi aldığı için
 * ProfileViewModelFactory/FollowListViewModelFactory ile AYNI desen. */
class ConversationViewModelFactory(private val conversationId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ConversationViewModel(conversationId) as T
    }
}
