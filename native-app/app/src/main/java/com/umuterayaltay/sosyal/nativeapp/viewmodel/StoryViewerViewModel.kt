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
import com.umuterayaltay.sosyal.nativeapp.repository.UserStoriesResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
 * Bir kullanıcının hikaye viewer'ı — StoryBar/FeedScreen'den `userId` ile
 * açılır (ProfileViewModel/HashtagViewModel'deki AYNI constructor-parametre +
 * Factory deseni). `loadUserStories()` SADECE init'te bir kez çağrılır (bkz.
 * StoriesRepository.getUserStories() docstring'i — GET içinde story_views
 * upsert yan etkisi var, Compose recomposition'ı bunu tekrar tetiklememeli).
 *
 * Anket oylaması VAR OLAN [ServiceLocator.pollsRepository]'yi kullanır —
 * PollsRepository/PollsApi'ye DOKUNULMADI, story'nin poll'u post'unkiyle
 * AYNI şekil olduğu için doğrudan reuse edilir.
 */
class StoryViewerViewModel(private val userId: String) : ViewModel() {

    private val storiesRepository = ServiceLocator.storiesRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val tokenStore = ServiceLocator.tokenStore

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

    init {
        loadUserStories()
    }

    private fun loadUserStories() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = storiesRepository.getUserStories(userId)) {
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

    /** Sağ yarıya tıklama / segment bitişi — sondaysa viewer'ın kendisi
     * (Compose tarafı) kapatma kararını verir, bu ViewModel sadece index'i
     * sınırlar. */
    fun goNext(): Boolean {
        val next = _currentIndex.value + 1
        return if (next < _stories.value.size) {
            _currentIndex.value = next
            true
        } else {
            false
        }
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

    /** FeedViewModel.votePoll() ile AYNI desen: sunucudan dönen GÜNCEL anket
     * durumu doğrudan geçerli hikayeye yazılır, optimistic tahmin YAPILMAZ. */
    fun votePoll(optionId: String) {
        val story = currentStory() ?: return
        val pollId = story.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    val updatedPoll = Poll(
                        id = pollId,
                        options = result.options,
                        totalVotes = result.totalVotes,
                        myVote = result.myVote,
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

/** StoryViewerViewModel constructor'ı userId parametresi aldığı için
 * Compose'un varsayılan factory'si yetmez — HashtagViewModelFactory ile
 * AYNI desen. */
class StoryViewerViewModelFactory(private val userId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return StoryViewerViewModel(userId) as T
    }
}
