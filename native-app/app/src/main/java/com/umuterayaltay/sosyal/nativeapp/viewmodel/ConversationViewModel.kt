package com.umuterayaltay.sosyal.nativeapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.ConversationInfoDto
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import com.umuterayaltay.sosyal.nativeapp.repository.ConversationDetailResult
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
        if (freshOnes.isNotEmpty()) {
            _messages.value = _messages.value + freshOnes
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
     * desen/gerekçe). */
    fun send(context: Context) {
        val content = _sendText.value.trim()
        val imageUri = _selectedImageUri.value
        if (content.isEmpty() && imageUri == null) return
        val replyId = _replyingTo.value?.id

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
                is SendMessageResult.Success -> {
                    // Optimistic DEĞİL — sunucu yanıtındaki mesajı kullanıyoruz
                    // (basit ve güvenilir, spesifikasyon gereği).
                    _messages.value = _messages.value + result.message
                    _sendText.value = ""
                    _replyingTo.value = null
                    _selectedImageUri.value = null
                }
                is SendMessageResult.Error -> handleError(result.code, silent = true)
            }
        }
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
