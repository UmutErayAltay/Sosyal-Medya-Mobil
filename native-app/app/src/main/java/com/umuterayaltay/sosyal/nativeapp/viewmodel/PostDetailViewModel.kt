package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.repository.AddCommentResult
import com.umuterayaltay.sosyal.nativeapp.repository.DeletePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.EditPostResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.PostDetailResult
import com.umuterayaltay.sosyal.nativeapp.repository.ReportResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleArchiveResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleMutePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.TogglePinResult
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
    // FeedEvent.ShowToast ile AYNI gerekçe — repost/report sonucu GÖRÜNÜR bildirim ister.
    data class ShowToast(val message: String) : PostDetailEvent()
    // Silinen/arşivlenen bir postun DETAY ekranında kalmaya devam etmesi
    // anlamsız — Feed/Discover/Hashtag/Profile'ın AKSİNE (listeden çıkar
    // yeter) burada geri navigasyon GEREKİR, bkz. deletePost()/toggleArchive().
    data object NavigateBack : PostDetailEvent()
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
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val reportsRepository = ServiceLocator.reportsRepository
    private val tokenStore = ServiceLocator.tokenStore
    private val authRepository = ServiceLocator.authRepository

    private val _post = MutableStateFlow<Post?>(null)
    val post: StateFlow<Post?> = _post.asStateFlow()

    // Post yönetimi (düzenle/sil/arşivle/sabitle) — FeedViewModel.currentUserId
    // ile AYNI gerekçe/desen.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    init {
        viewModelScope.launch { _currentUserId.value = authRepository.getCurrentUserId() }
    }

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

    /** Post üzerindeki "Kaydet"/"Kaydedildi" aksiyonu — toggleMutePost() ile AYNI
     * desen (tek post gösterildiği için doğrudan _post.value güncellenir). */
    fun toggleBookmark() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(current.id)) {
                is ToggleBookmarkResult.Success -> {
                    _post.value = current.copy(bookmarkedByMe = result.bookmarked)
                }
                is ToggleBookmarkResult.Error -> {
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

    /** [gifUrl]/[stickerId] (Faz 5 Dalga 3B, MediaPickerSheet'ten) — content
     * boşken de biri doluysa backend kabul eder (InteractionsRepository.addComment
     * yorumu, api_add_comment ile AYNI kural), bu yüzden aşağıdaki boş-kontrolü
     * ÜÇÜNÜ birden kapsar (sadece content.isEmpty() değil). */
    fun addComment(gifUrl: String? = null, stickerId: String? = null) {
        val content = _commentText.value.trim()
        if (content.isEmpty() && gifUrl.isNullOrBlank() && stickerId.isNullOrBlank()) return
        val parentId = _replyingTo.value?.id
        viewModelScope.launch {
            when (val result = interactionsRepository.addComment(postId, content, parentId, stickerId, gifUrl)) {
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

    /** Post üzerindeki "Yeniden Paylaş" aksiyonu — FeedViewModel.repost() ile AYNI
     * desen (tek post gösterildiği için postId `_post.value`'dan alınır, backend
     * YENİ bir post oluşturuyor, bu ekranda güncellenecek bir alan yok). */
    fun repost() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = repostsRepository.repost(current.id)) {
                is RepostResult.Success -> _events.emit(PostDetailEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    } else {
                        _events.emit(PostDetailEvent.ShowToast(mapRepostError(result.code)))
                    }
                }
            }
        }
    }

    /** Post üzerindeki "Bildir" aksiyonu — FeedViewModel.report() ile AYNI desen. */
    fun report() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = reportsRepository.reportPost(current.id)) {
                is ReportResult.Success -> _events.emit(PostDetailEvent.ShowToast("Şikayetin alındı, teşekkürler"))
                is ReportResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(PostDetailEvent.SessionExpired)
                    } else {
                        _events.emit(PostDetailEvent.ShowToast(mapReportError(result.code)))
                    }
                }
            }
        }
    }

    /** Bu ekran TEK bir post gösterir — edit içerik günceller (ekranda kalınır),
     * delete/archive SONRASI [PostDetailEvent.NavigateBack] emit edilir (geride
     * artık gösterilecek/anlamlı bir post yok, Feed/Discover/Hashtag/Profile'ın
     * "listeden çıkar" deseninin buradaki karşılığı). */
    fun editPost(content: String) {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = interactionsRepository.editPost(current.id, content)) {
                is EditPostResult.Success -> {
                    _post.value = current.copy(content = result.content)
                    _events.emit(PostDetailEvent.ShowToast("Post güncellendi"))
                }
                is EditPostResult.Error -> handlePostManagementError(result.code, "Post güncellenemedi")
            }
        }
    }

    fun deletePost() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = interactionsRepository.deletePost(current.id)) {
                is DeletePostResult.Success -> {
                    _events.emit(PostDetailEvent.ShowToast("Post silindi"))
                    _events.emit(PostDetailEvent.NavigateBack)
                }
                is DeletePostResult.Error -> handlePostManagementError(result.code, "Post silinemedi")
            }
        }
    }

    fun toggleArchive() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleArchive(current.id)) {
                is ToggleArchiveResult.Success -> {
                    _events.emit(
                        PostDetailEvent.ShowToast(if (result.isArchived) "Post arşivlendi" else "Post arşivden çıkarıldı"),
                    )
                    // Arşivden ÇIKARILAN post da (is_archived=false) bu ekranda
                    // sorunsuz görüntülenebilir olurdu ama Feed/Discover/Hashtag/
                    // Profile'daki "listeden çıkar" tutarlılığı için basitlik
                    // adına HER İKİ yönde de geri dönülür.
                    _events.emit(PostDetailEvent.NavigateBack)
                }
                is ToggleArchiveResult.Error -> handlePostManagementError(result.code, "İşlem başarısız")
            }
        }
    }

    fun togglePin() {
        val current = _post.value ?: return
        viewModelScope.launch {
            when (val result = interactionsRepository.togglePin(current.id)) {
                is TogglePinResult.Success -> _events.emit(
                    PostDetailEvent.ShowToast(if (result.pinned) "Profilinin en üstüne sabitlendi" else "Sabitleme kaldırıldı"),
                )
                is TogglePinResult.Error -> handlePostManagementError(result.code, "İşlem başarısız")
            }
        }
    }

    private suspend fun handlePostManagementError(code: String?, fallback: String) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(PostDetailEvent.SessionExpired)
            return
        }
        val message = when (code) {
            "not_found" -> "Bu post artık mevcut değil"
            "empty_post" -> "Post boş olamaz"
            "unavailable" -> "Şu anda kullanılamıyor, daha sonra tekrar dene"
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> fallback
        }
        _events.emit(PostDetailEvent.ShowToast(message))
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
