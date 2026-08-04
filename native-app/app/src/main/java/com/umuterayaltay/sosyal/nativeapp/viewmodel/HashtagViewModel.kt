package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.HashtagPostsResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ReportResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleHashtagFollowResult
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

sealed class HashtagEvent {
    data object SessionExpired : HashtagEvent()
    // FeedEvent.ShowToast ile AYNI gerekçe — repost/report sonucu GÖRÜNÜR bildirim ister.
    data class ShowToast(val message: String) : HashtagEvent()
}

/**
 * Hashtag detay ekranı için ViewModel — DiscoverViewModel'in arama-sonucu
 * postlarındaki AYNI toggleLike deseni (interactionsRepository üzerinden).
 * `tag` constructor parametresi (ProfileViewModel'in username alışıyla AYNI —
 * [HashtagViewModelFactory] gerekir, Compose'un varsayılan parametresiz
 * fabrikası yetmez).
 */
class HashtagViewModel(private val tag: String) : ViewModel() {

    private val hashtagRepository = ServiceLocator.hashtagRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val mutesRepository = ServiceLocator.mutesRepository
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val reportsRepository = ServiceLocator.reportsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<HashtagEvent>()
    val events: SharedFlow<HashtagEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = hashtagRepository.getHashtagPosts(tag)) {
                is HashtagPostsResult.Success -> {
                    _posts.value = result.posts
                    _isFollowing.value = result.isFollowing
                }
                is HashtagPostsResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun toggleFollow() {
        viewModelScope.launch {
            when (val result = hashtagRepository.toggleFollow(tag)) {
                is ToggleHashtagFollowResult.Success -> _isFollowing.value = result.following
                is ToggleHashtagFollowResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** DiscoverViewModel.toggleLike() ile AYNI gerekçe: kalp ikonuna tıklanınca çağrılır. */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(postId)) {
                is ToggleLikeResult.Success -> {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(likeCount = result.count, likedByMe = result.liked) else post
                    }
                }
                is ToggleLikeResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** DiscoverViewModel.toggleMutePost() ile AYNI gerekçe: PostActionsSheet'teki
     * "Postu Sessize Al"/"Sesini Aç" aksiyonu. */
    fun toggleMutePost(postId: String) {
        viewModelScope.launch {
            when (val result = mutesRepository.toggleMutePost(postId)) {
                is ToggleMutePostResult.Success -> {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(mutedByMe = result.muted) else post
                    }
                }
                is ToggleMutePostResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** DiscoverViewModel.toggleBookmark() ile AYNI gerekçe: PostActionsSheet'teki
     * "Kaydet"/"Kaydedildi" aksiyonu. */
    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(postId)) {
                is ToggleBookmarkResult.Success -> {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(bookmarkedByMe = result.bookmarked) else post
                    }
                }
                is ToggleBookmarkResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** DiscoverViewModel.votePoll() ile AYNI desen: sunucudan dönen GÜNCEL
     * anket durumu doğrudan ilgili posta yazılır, optimistic tahmin YAPILMAZ. */
    fun votePoll(postId: String, optionId: String) {
        val pollId = _posts.value.firstOrNull { it.id == postId }?.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    _posts.value = _posts.value.map { it.applyVote(postId, result) }
                }
                is VotePollResult.Error -> handleError(result.code, silent = true)
            }
        }
    }

    /** PostActionsSheet'teki "Yeniden Paylaş" aksiyonu — FeedViewModel.repost()
     * ile AYNI desen (backend YENİ bir post oluşturuyor, listede güncellenecek
     * bir alan yok, sonuç sadece Toast/Snackbar ile bildirilir). */
    fun repost(postId: String) {
        viewModelScope.launch {
            when (val result = repostsRepository.repost(postId)) {
                is RepostResult.Success -> _events.emit(HashtagEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(HashtagEvent.SessionExpired)
                    } else {
                        _events.emit(HashtagEvent.ShowToast(mapRepostError(result.code)))
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Bildir" aksiyonu — FeedViewModel.report() ile AYNI desen. */
    fun report(postId: String) {
        viewModelScope.launch {
            when (val result = reportsRepository.reportPost(postId)) {
                is ReportResult.Success -> _events.emit(HashtagEvent.ShowToast("Şikayetin alındı, teşekkürler"))
                is ReportResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(HashtagEvent.SessionExpired)
                    } else {
                        _events.emit(HashtagEvent.ShowToast(mapReportError(result.code)))
                    }
                }
            }
        }
    }

    private fun mapRepostError(code: String?): String = when (code) {
        "already_reposted" -> "Zaten yeniden paylaştın"
        "not_found" -> "Gönderi bulunamadı"
        "not_public", "private_account" -> "Bu gönderi yeniden paylaşılamaz"
        "blocked" -> "Bu işlem engellenmiş bir kullanıcı yüzünden yapılamıyor"
        "not_available" -> "Bu gönderi şu an yeniden paylaşılamıyor"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "unavailable" -> "Şu anda kullanılamıyor, daha sonra tekrar dene"
        else -> "Yeniden paylaşılamadı, lütfen tekrar dene"
    }

    private fun mapReportError(code: String?): String = when (code) {
        "already_reported" -> "Bu içeriği zaten şikayet ettin"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "unavailable" -> "Şu anda kullanılamıyor, daha sonra tekrar dene"
        else -> "Şikayet gönderilemedi, lütfen tekrar dene"
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(HashtagEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = when (code) {
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> "Yüklenemedi, lütfen tekrar deneyin"
        }
    }
}

/** HashtagViewModel constructor'ı tag parametresi aldığı için Compose'un
 * varsayılan (parametresiz) factory'si yetmez — ProfileViewModelFactory ile
 * AYNI desen. */
class HashtagViewModelFactory(private val tag: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HashtagViewModel(tag) as T
    }
}
