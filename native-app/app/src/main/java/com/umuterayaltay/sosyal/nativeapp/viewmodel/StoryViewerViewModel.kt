package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.DeleteStoryResult
import com.umuterayaltay.sosyal.nativeapp.repository.Poll
import com.umuterayaltay.sosyal.nativeapp.repository.ReactToStoryResult
import com.umuterayaltay.sosyal.nativeapp.repository.ReplyToStoryResult
import com.umuterayaltay.sosyal.nativeapp.repository.SaveHighlightResult
import com.umuterayaltay.sosyal.nativeapp.repository.Story
import com.umuterayaltay.sosyal.nativeapp.repository.StoryViewer
import com.umuterayaltay.sosyal.nativeapp.repository.StoryViewersResult
import com.umuterayaltay.sosyal.nativeapp.repository.UserStoriesResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "İzleyenler" bottom sheet'inin durumu — 2026-08-11 (kullanıcı isteği:
 * "hikayeyi kim izledi listesi"). */
sealed class StoryViewersUiState {
    data object Idle : StoryViewersUiState()
    data object Loading : StoryViewersUiState()
    data class Success(val viewers: List<StoryViewer>, val count: Int) : StoryViewersUiState()
    data class Error(val message: String) : StoryViewersUiState()
}

sealed class StoryViewerEvent {
    data object SessionExpired : StoryViewerEvent()
    /** Kendi hikayeni sildikten sonra viewer'ın kapanması gerekir (Instagram
     * deseni: silinen hikaye bir daha gösterilemez). */
    data object StoryDeleted : StoryViewerEvent()
    /** react/reply BAŞARILI olunca üretilen DM'e "sohbete git" linki için —
     * bu turda otomatik navigasyon YAPILMIYOR (görev tanımı: sadece backend'in
     * ürettiği conversation_id taşınır), UI isterse kullanır. */
    data class MessageSent(val conversationId: String?) : StoryViewerEvent()
    data class HighlightSaved(val highlightId: String?) : StoryViewerEvent()
}

/**
 * Bir kullanıcının hikaye viewer'ı — StoryBar/FeedScreen'den `userIds`
 * (hikaye çubuğundaki TÜM kullanıcıların sıralı listesi) + tıklanan
 * kullanıcının o listedeki `startIndex`'i ile açılır (ProfileViewModel/
 * HashtagViewModel'deki AYNI constructor-parametre + Factory deseni).
 * `loadUserStories()` her kullanıcı geçişinde bir kez çağrılır (bkz.
 * StoriesRepository.getUserStories() docstring'i — GET içinde story_views
 * upsert yan etkisi var, Compose recomposition'ı bunu tekrar tetiklememeli).
 *
 * 2026-08-10 (kullanıcı isteği: "storylere girdikten sonra birinin storyleri
 * bitince sıradakine geçsin") — Instagram'ın AYNI davranışı: `goNext()`
 * geçerli kullanıcının SON hikayesinden sonra viewer'ı KAPATMAK yerine
 * (nav YAPMADAN, AYNI ekran/ViewModel instance'ında) `userIds`'teki bir
 * SONRAKİ kullanıcının hikayelerini yükler. Hiç kullanıcı kalmayınca (son
 * kullanıcının da son hikayesi) `false` döner, çağıran taraf (StoryViewerScreen)
 * o zaman viewer'ı kapatır — mevcut `if (!viewModel.goNext()) onNavigateBack(...)`
 * çağrı yerleri DEĞİŞMEDEN doğru çalışmaya devam eder.
 *
 * Anket oylaması VAR OLAN [ServiceLocator.pollsRepository]'yi kullanır —
 * PollsRepository/PollsApi'ye DOKUNULMADI, story'nin poll'u post'unkiyle
 * AYNI şekil olduğu için doğrudan reuse edilir.
 */
class StoryViewerViewModel(
    private val userIds: List<String>,
    startIndex: Int,
) : ViewModel() {

    private val storiesRepository = ServiceLocator.storiesRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _userIndex = MutableStateFlow(startIndex.coerceIn(0, (userIds.size - 1).coerceAtLeast(0)))

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _avatarUrl = MutableStateFlow<String?>(null)
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _isMine = MutableStateFlow(false)
    val isMine: StateFlow<Boolean> = _isMine.asStateFlow()

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<StoryViewerEvent>()
    val events: SharedFlow<StoryViewerEvent> = _events

    // 2026-08-11 (kullanıcı isteği: "hikayeyi kim izledi listesi") — SADECE
    // kendi hikayendeyken anlamlı, o yüzden lazy: ekran açılır açılmaz
    // OTOMATİK çekilmiyor (her hikaye geçişinde gereksiz bir network isteği
    // olurdu), sadece kullanıcı "izleyenler" satırına dokununca yüklenir.
    private val _viewers = MutableStateFlow<StoryViewersUiState>(StoryViewersUiState.Idle)
    val viewers: StateFlow<StoryViewersUiState> = _viewers.asStateFlow()

    init {
        loadUserStories()
    }

    private fun loadUserStories() {
        val uid = userIds.getOrNull(_userIndex.value) ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // Kullanıcı geçişinde ESKİ listeyi HEMEN temizle — aksi halde
            // ağ isteği süresince `loading=true` AMA `stories` hâlâ ÖNCEKİ
            // kullanıcının listesini taşırdı, "loading && stories.isEmpty()"
            // dalı YAKALAMAZ, ekran bir an ÖNCEKİ kullanıcının son karesinde
            // donmuş kalırdı.
            _stories.value = emptyList()
            _currentIndex.value = 0
            when (val result = storiesRepository.getUserStories(uid)) {
                is UserStoriesResult.Success -> {
                    _username.value = result.data.username
                    _avatarUrl.value = result.data.avatarUrl
                    _isMine.value = result.data.isMine
                    _stories.value = result.data.stories
                    _currentIndex.value = 0
                }
                is UserStoriesResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    /** Sağ yarıya tıklama / segment bitişi — geçerli kullanıcının hikayeleri
     * bitince sıradaki kullanıcıya geçer (yukarıdaki sınıf yorumuna bkz.),
     * o da yoksa viewer'ın kendisi (Compose tarafı) kapatma kararını verir. */
    fun goNext(): Boolean {
        val next = _currentIndex.value + 1
        if (next < _stories.value.size) {
            _currentIndex.value = next
            return true
        }
        val nextUserIndex = _userIndex.value + 1
        if (nextUserIndex < userIds.size) {
            _userIndex.value = nextUserIndex
            loadUserStories()
            return true
        }
        return false
    }

    /** Sol yarıya tıklama — ilk segmentteyken no-op (viewer'dan çıkmaz). */
    fun goPrevious() {
        val prev = _currentIndex.value - 1
        if (prev >= 0) _currentIndex.value = prev
    }

    fun currentStory(): Story? = _stories.value.getOrNull(_currentIndex.value)

    fun reactToStory(emoji: String) {
        val story = currentStory() ?: return
        viewModelScope.launch {
            when (val result = storiesRepository.reactToStory(story.id, emoji)) {
                is ReactToStoryResult.Success -> _events.emit(StoryViewerEvent.MessageSent(result.conversationId))
                is ReactToStoryResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    fun replyToStory(text: String) {
        val story = currentStory() ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = storiesRepository.replyToStory(story.id, text)) {
                is ReplyToStoryResult.Success -> _events.emit(StoryViewerEvent.MessageSent(result.conversationId))
                is ReplyToStoryResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    fun deleteCurrentStory() {
        val story = currentStory() ?: return
        viewModelScope.launch {
            when (val result = storiesRepository.deleteStory(story.id)) {
                is DeleteStoryResult.Success -> _events.emit(StoryViewerEvent.StoryDeleted)
                is DeleteStoryResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    fun saveToHighlight(highlightId: String? = null, newTitle: String? = null) {
        val story = currentStory() ?: return
        viewModelScope.launch {
            when (val result = storiesRepository.saveHighlight(story.id, highlightId, newTitle)) {
                is SaveHighlightResult.Success -> _events.emit(StoryViewerEvent.HighlightSaved(result.highlightId))
                is SaveHighlightResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** "İzleyenler" satırına dokununca çağrılır — SADECE kendi hikayen için
     * anlamlı (backend başkasının hikayesinde 403 döner, bu ViewModel bunu
     * genel "izlenemedi" hatası olarak gösterir, isMine kontrolü zaten
     * UI'da yapılıyor). */
    fun loadViewers() {
        val story = currentStory() ?: return
        viewModelScope.launch {
            _viewers.value = StoryViewersUiState.Loading
            when (val result = storiesRepository.getStoryViewers(story.id)) {
                is StoryViewersResult.Success -> {
                    _viewers.value = StoryViewersUiState.Success(result.viewers, result.count)
                }
                is StoryViewersResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(StoryViewerEvent.SessionExpired)
                        _viewers.value = StoryViewersUiState.Idle
                    } else {
                        _viewers.value = StoryViewersUiState.Error("İzleyenler yüklenemedi")
                    }
                }
            }
        }
    }

    /** Bottom sheet kapanınca çağrılır — bir sonraki açılışta eski veriyi
     * BİR AN göstermesin diye (Idle'a dönerse sheet kendi loading'ini gösterir). */
    fun resetViewers() {
        _viewers.value = StoryViewersUiState.Idle
    }

    /** FeedViewModel.votePoll() ile AYNI desen: sunucudan dönen GÜNCEL anket
     * durumu doğrudan geçerli hikayeye yazılır, optimistic tahmin YAPILMAZ. */
    fun votePoll(optionId: String) {
        val story = currentStory() ?: return
        val poll = story.poll ?: return
        val pollId = poll.id
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    // Kullanıcı raporu (2026-08-10): "ankette oy kullanınca
                    // anket bi anda yeri değişip ortaya geliyor" — kök neden
                    // bu constructor'ın positionX/positionY/scale'i HİÇ
                    // taşımaması (varsayılan null) idi; StoryViewerScreen
                    // null'da 0.5/0.5/1.0'a (ekranın TAM ORTASI) düşüyordu —
                    // yani her oy, editörde sürüklenerek seçilmiş konumu
                    // SIFIRLIYORDU. Eski `poll`'dan AYNEN taşınıyor.
                    val updatedPoll = Poll(
                        id = pollId,
                        options = result.options,
                        totalVotes = result.totalVotes,
                        myVote = result.myVote,
                        positionX = poll.positionX,
                        positionY = poll.positionY,
                        scale = poll.scale,
                    )
                    _stories.value = _stories.value.map {
                        if (it.id == story.id) it.copy(poll = updatedPoll) else it
                    }
                }
                is VotePollResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(StoryViewerEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = when (code) {
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> "Hikaye yüklenemedi"
        }
    }
}

/** StoryViewerViewModel constructor'ı userIds/startIndex parametresi aldığı
 * için Compose'un varsayılan factory'si yetmez — HashtagViewModelFactory ile
 * AYNI desen. */
class StoryViewerViewModelFactory(
    private val userIds: List<String>,
    private val startIndex: Int,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StoryViewerViewModel(userIds, startIndex) as T
    }
}
