package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.repository.AddCommentResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.PostDetailResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleMutePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import com.umuterayaltay.sosyal.nativeapp.repository.applyVote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PostDetailEvent {
    data object SessionExpired : PostDetailEvent()
}

/**
 * Tek bir post + yorum hiyerarşisi ekranı için ViewModel — ConversationViewModel/
 * ProfileViewModel'deki gibi constructor parametreli (postId), bu yüzden
 * [PostDetailViewModelFactory] gerekir.
 *
 * Yorum ekleme optimistic DEĞİL: sunucunun döndürdüğü GERÇEK CommentDto
 * listenin sonuna eklenir (ConversationViewModel.send()'deki AYNI felsefe).
 * Beğeni de diğer ViewModel'lerdeki AYNI desen — sunucu yanıtı geldikten
 * SONRA post state'i güncellenir.
 */
class PostDetailViewModel(private val postId: String) : ViewModel() {

    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val mutesRepository = ServiceLocator.mutesRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentDto>>(emptyList())
    val comments: StateFlow<List<CommentDto>> = _comments.asStateFlow()

    private val _commentText = MutableStateFlow("")
    val commentText: StateFlow<String> = _commentText.asStateFlow()

    private val _replyingTo = MutableStateFlow<CommentDto?>(null)
    val replyingTo: StateFlow<CommentDto?> = _replyingTo.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<PostDetailEvent>()
    val events: SharedFlow<PostDetailEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = interactionsRepository.getPostDetail(postId)) {
                is PostDetailResult.Success -> {
                    _post.value = result.data.post
                    _comments.value = result.data.comments
                }
                is PostDetailResult.Error -> {
                    if (result.code == "unauthorized") {
                        // Feed/Discover/Profil/Reels'teki AYNI MVP kararı: proaktif
                        // doğrulama yok, ilk 401'de yerel token temizlenip login'e dönülür.
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }
            _loading.value = false
        }
    }

    /** Post üzerindeki kalp ikonuna tıklanınca çağrılır — diğer ekranlardaki
     * AYNI desen (sunucu yanıtındaki GERÇEK count/liked kullanılır). */
    fun toggleLike() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(current.id)) {
                is ToggleLikeResult.Success -> {
                    _post.value = current.copy(likeCount = result.count, likedByMe = result.liked)
                }
                is ToggleLikeResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Anket seçeneğine tıklanınca — diğer ekranlardaki AYNI desen (sunucudan
     * dönen GÜNCEL durum posta yazılır, optimistic tahmin YAPILMAZ). Tek post
     * gösterildiği için toggleLike() ile AYNI şekilde _post.value doğrudan
     * güncellenir. */
    fun votePoll(postId: String, optionId: String) {
        val current = _post.value ?: return
        val pollId = current.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    _post.value = current.applyVote(postId, result)
                }
                is VotePollResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Post üzerindeki "Postu Sessize Al"/"Sesini Aç" aksiyonu — toggleLike() ile
     * AYNI desen (tek post gösterildiği için doğrudan _post.value güncellenir). */
    fun toggleMutePost() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = mutesRepository.toggleMutePost(current.id)) {
                is ToggleMutePostResult.Success -> {
                    _post.value = current.copy(mutedByMe = result.muted)
                }
                is ToggleMutePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    }
                }
            }
        }
    }

    fun onCommentTextChange(text: String) {
        _commentText.value = text
    }

    fun setReplyingTo(comment: CommentDto) {
        _replyingTo.value = comment
    }

    fun clearReplyingTo() {
        _replyingTo.value = null
    }

    fun addComment() {
        val content = _commentText.value.trim()
        if (content.isEmpty()) return
        val parentId = _replyingTo.value?.id
        viewModelScope.launch {
            when (val result = interactionsRepository.addComment(postId, content, parentId)) {
                is AddCommentResult.Success -> {
                    _commentText.value = ""
                    _replyingTo.value = null
                    _comments.value = if (parentId == null) {
                        _comments.value + result.comment
                    } else {
                        // Yanıt: ana yorumun replies listesinin SONUNA eklenir —
                        // backend de yanıtları tek seviye, ana yorumun altına
                        // gömdüğü için (bkz. api_post_detail top_comments/replies).
                        _comments.value.map { top ->
                            if (top.id == parentId) {
                                top.copy(replies = (top.replies ?: emptyList()) + result.comment)
                            } else {
                                top
                            }
                        }
                    }
                }
                is AddCommentResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }
        }
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "not_found" -> "Gönderi bulunamadı"
        "empty" -> "Yorum boş olamaz"
        else -> "Yüklenemedi, lütfen tekrar deneyin"
    }
}

/** PostDetailViewModel constructor'ı postId parametresi aldığı için
 * ConversationViewModelFactory/ProfileViewModelFactory ile AYNI desen. */
class PostDetailViewModelFactory(private val postId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PostDetailViewModel(postId) as T
    }
}
