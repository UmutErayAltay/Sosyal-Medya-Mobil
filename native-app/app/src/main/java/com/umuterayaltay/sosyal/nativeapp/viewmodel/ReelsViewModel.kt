package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ReelsPageResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReelsEvent {
    data object SessionExpired : ReelsEvent()
    // 2026-08-09 (kullanıcı isteği: "reels'lere gönderme, kaydetme, repost
    // gibi butonlar gelsin") — FeedEvent.ShowToast ile AYNI amaç (repost
    // başarı/hata geri bildirimi, bkz. FeedViewModel.repost() yorumu).
    data class ShowToast(val message: String) : ReelsEvent()
}

/**
 * Dikey video akışı (Reels) ViewModel'i — DiscoverViewModel'in keşfet-akışı
 * kısmıyla AYNI desen (sayfalama, hasMore/loading guard, 401 → token temizle +
 * SessionExpired event, aynı MVP kararı bkz. FeedViewModel/DiscoverViewModel).
 */
class ReelsViewModel : ViewModel() {

    private val reelsRepository = ServiceLocator.reelsRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    // 2026-08-09: kaydet/repost — FeedViewModel'in AYNI repository'leri,
    // YENİ bir soyutlama İCAT EDİLMEDİ.
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<ReelsEvent>()
    val events: SharedFlow<ReelsEvent> = _events

    init {
        loadPage(1)
    }

    fun loadPage(page: Int) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = reelsRepository.getReelsPage(page)) {
                is ReelsPageResult.Success -> {
                    _posts.value = if (page <= 1) {
                        result.posts
                    } else {
                        _posts.value + result.posts
                    }
                    _page.value = result.page
                    _hasMore.value = result.hasMore
                }
                is ReelsPageResult.Error -> {
                    if (result.code == "unauthorized") {
                        // FeedViewModel/DiscoverViewModel'deki AYNI MVP kararı: proaktif
                        // doğrulama yok, ilk 401'de yerel token temizlenip login'e dönülür.
                        tokenStore.clearToken()
                        _events.emit(ReelsEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }
            _loading.value = false
        }
    }

    /** VerticalPager sonuna yaklaşınca çağrılır (bkz. ReelsScreen currentPage guard'ı). */
    fun loadMore() {
        if (!_hasMore.value || _loading.value) return
        loadPage(_page.value + 1)
    }

    /** ReelOverlay'deki kalp ikonuna tıklanınca çağrılır — Feed/Discover/Profil'deki
     * AYNI desen (sunucu yanıtındaki GERÇEK count/liked ile listeyi güncelle). */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(postId)) {
                is ToggleLikeResult.Success -> {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(likeCount = result.count, likedByMe = result.liked) else post
                    }
                }
                is ToggleLikeResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ReelsEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** ReelOverlay'deki kaydet ikonuna tıklanınca — FeedViewModel.toggleBookmark()
     * ile AYNI mantık, ama Room-backed FeedRepository.updateBookmarkState()
     * YOK (Reels'in KENDİ kalıcı önbelleği yok, sayfalama sadece [_posts]
     * StateFlow'unda tutuluyor) — bu yüzden sonuç DOĞRUDAN [_posts]'a yazılır
     * (toggleLike() ile AYNI yerel-güncelleme deseni). */
    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(postId)) {
                is ToggleBookmarkResult.Success -> {
                    _posts.value = _posts.value.map { post ->
                        if (post.id == postId) post.copy(bookmarkedByMe = result.bookmarked) else post
                    }
                }
                is ToggleBookmarkResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ReelsEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe.
                }
            }
        }
    }

    /** ReelOverlay'deki repost ikonuna tıklanınca — FeedViewModel.repost() ile
     * AYNI mantık (onay diyaloğu YOK, tek-tık hızlı repost). */
    fun repost(postId: String) {
        viewModelScope.launch {
            when (val result = repostsRepository.repost(postId)) {
                is RepostResult.Success -> _events.emit(ReelsEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ReelsEvent.SessionExpired)
                    } else {
                        _events.emit(ReelsEvent.ShowToast(mapRepostError(result.code)))
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
        else -> "Yeniden paylaşılamadı, lütfen tekrar dene"
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "Reels yüklenemedi, lütfen tekrar deneyin"
    }
}
