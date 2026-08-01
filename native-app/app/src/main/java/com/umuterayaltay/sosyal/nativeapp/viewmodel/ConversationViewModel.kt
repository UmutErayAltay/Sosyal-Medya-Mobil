package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
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
import java.time.Instant
import java.util.UUID

sealed class ConversationEvent {
    data object SessionExpired : ConversationEvent()
}

private const val POLL_INTERVAL_MS = 5000L

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

    /** Bkz. sınıf yorumu — ÖNCE Realtime dener, başarısız olursa (kurulumda
     * VEYA sonradan) [onFailure] üzerinden startPolling()'e düşer. */
    private fun connectRealtimeOrPoll() {
        viewModelScope.launch {
            ServiceLocator.realtimeConnectionManager.connect(
                conversationId = conversationId,
                onNewMessage = { message -> appendFreshMessages(listOf(message)) },
                onFailure = { startPolling() },
            )
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
     */
    fun send(context: Context) {
        val content = _sendText.value.trim()
        val imageUri = _selectedImageUri.value
        if (content.isEmpty() && imageUri == null) return
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
            sticker = null,
            imageUrl = null,
        )

        // Input HEMEN temizlenir — "gönderdim" hissi ağ isteği bitmeden verilir.
        _messages.value = _messages.value + optimisticMessage
        _sendText.value = ""
        _replyingTo.value = null
        _selectedImageUri.value = null

        viewModelScope.launch {
            var imageBytes: ByteArray? = null
            var imageMimeType: String? = null
            if (imageUri != null) {
                try {
                    withContext(Dispatchers.IO) {
                        imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    }
                    imageMimeType = context.contentResolver.getType(imageUri)
                } catch (e: Exception) {
                    removeOptimisticMessage(tempId)
                    restoreFailedSend(content, replyingTo, imageUri)
                    _error.value = "Görsel okunamadı, lütfen tekrar deneyin"
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
                )
            ) {
                is SendMessageResult.Success -> replaceOptimisticMessage(tempId, result.message)
                is SendMessageResult.Error -> {
                    removeOptimisticMessage(tempId)
                    if (result.code == "unauthorized") {
                        handleError(result.code)
                    } else {
                        restoreFailedSend(content, replyingTo, imageUri)
                        handleError(result.code)
                    }
                }
            }
        }
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
    private fun restoreFailedSend(content: String, replyingTo: MessageDto?, imageUri: Uri?) {
        _sendText.value = content
        _replyingTo.value = replyingTo
        _selectedImageUri.value = imageUri
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
