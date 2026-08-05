package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.SuggestedUserDto
import com.umuterayaltay.sosyal.nativeapp.repository.FeedRefreshResult
import com.umuterayaltay.sosyal.nativeapp.repository.Poll
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ReportResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleFollowResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleMutePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.UnreadCountResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class FeedUiState {
    data object Loading : FeedUiState()
    data object Success : FeedUiState()
    data class Error(val message: String) : FeedUiState()
}

/** Tek seferlik olaylar (state değil) — navigasyon Compose tarafında dinler. */
sealed class FeedEvent {
    data object SessionExpired : FeedEvent()
    // Faz 5 sonrası eksik giderme: Repost/Bildir sonucu — bookmark/mute'un
    // AKSİNE (sessizce yutulan hatalar) burada kullanıcıya GÖRÜNÜR bir geri
    // bildirim gerekiyor (görev tanımı: "already_reposted"/"already_reported"
    // Snackbar/Toast ile gösterilmeli), bu yüzden yeni bir event türü eklendi.
    data class ShowToast(val message: String) : FeedEvent()
}

class FeedViewModel : ViewModel() {

    private val feedRepository = ServiceLocator.feedRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val notificationsRepository = ServiceLocator.notificationsRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val mutesRepository = ServiceLocator.mutesRepository
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val reportsRepository = ServiceLocator.reportsRepository
    // Madde 2 (önerilen kullanıcılar "Takip Et"): YENİ bir repository İCAT
    // EDİLMEDİ - ProfileRepository.toggleFollow() reuse edildi (görev tanımı).
    private val profileRepository = ServiceLocator.profileRepository
    private val tokenStore = ServiceLocator.tokenStore

    // Room'da poll kolonu YOK (bkz. PostEntity.toDomain() yorumu) — observePosts()
    // her zaman poll=null döner, bu yüzden votePoll() sonucu doğrudan Room'a
    // yazılamaz. Diğer ViewModel'lerdeki `_posts.value = ... .map { applyVote }`
    // deseninin buradaki karşılığı: sunucudan dönen GÜNCEL anket durumu bu yerel
    // overlay'e yazılıp Room akışıyla combine edilir.
    private val _pollOverrides = MutableStateFlow<Map<String, Poll>>(emptyMap())

    val posts: StateFlow<List<Post>> = combine(
        feedRepository.observePosts(),
        _pollOverrides,
    ) { list, overrides ->
        if (overrides.isEmpty()) list else list.map { post -> overrides[post.id]?.let { post.copy(poll = it) } ?: post }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Bildirim zili rozeti (opsiyonel) — sadece görüntüleme amaçlı, hata olursa
    // sessizce 0'da kalır (bir sayaç yüzünden Ana Sayfa'yı bir hata state'ine
    // düşürmek istenmiyor, toggleLike()'ın diğer-hatalar-sessiz-yutulur
    // gerekçesiyle AYNI).
    private val _unreadNotificationsCount = MutableStateFlow(0)
    val unreadNotificationsCount: StateFlow<Int> = _unreadNotificationsCount.asStateFlow()

    private val _events = MutableSharedFlow<FeedEvent>()
    val events: SharedFlow<FeedEvent> = _events

    // Madde 1 (sonsuz kaydırma) — DiscoverViewModel'in discoverHasMore/
    // discoverLoading state'leriyle AYNI desen, Feed'e ÖZGÜ tek fark: sayfa
    // numarası yerine backend'in döndürdüğü opak next_cursor taşınır.
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _nextCursor = MutableStateFlow<Int?>(null)

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    // Madde 2 (önerilen kullanıcılar) — SADECE refresh() (cursor=0) dolduruyor,
    // backend sözleşmesi gereği (bkz. ApiModels.kt SuggestedUserDto yorumu).
    private val _suggestedUsers = MutableStateFlow<List<SuggestedUserDto>>(emptyList())
    val suggestedUsers: StateFlow<List<SuggestedUserDto>> = _suggestedUsers.asStateFlow()

    init {
        refresh()
        loadUnreadNotificationsCount()
    }

    fun loadUnreadNotificationsCount() {
        viewModelScope.launch {
            when (val result = notificationsRepository.getUnreadCount()) {
                is UnreadCountResult.Success -> _unreadNotificationsCount.value = result.count
                is UnreadCountResult.Error -> Unit // sessizce yutulur, bkz. yukarıdaki alan yorumu
            }
        }
    }

    fun refresh() {
        // Pull-to-refresh ile bildirim rozeti de tazelenir (ör. kullanıcı
        // Bildirimler'i açıp geri döndükten sonra Ana Sayfa'yı yenilerse rozet
        // güncel sayıyı yansıtsın) — ayrı bir lifecycle-observer İCAT EDİLMEDİ,
        // mevcut kullanıcı eylemine (yenileme) bindirildi.
        loadUnreadNotificationsCount()
        viewModelScope.launch {
            _isRefreshing.value = true
            when (val result = feedRepository.refresh()) {
                is FeedRefreshResult.Success -> {
                    _uiState.value = FeedUiState.Success
                    _hasMore.value = result.hasNext
                    _nextCursor.value = result.nextCursor
                    _suggestedUsers.value = result.suggestedUsers ?: emptyList()
                }
                is FeedRefreshResult.Error -> {
                    if (result.code == "unauthorized") {
                        // Token geçersiz/süresi dolmuş — MVP kararı: proaktif doğrulama
                        // yok, ilk 401'de yerel token temizlenip login'e dönülür.
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    } else {
                        _uiState.value = FeedUiState.Error(mapErrorMessage(result.code))
                    }
                }
            }
            _isRefreshing.value = false
        }
    }

    /** Madde 1 (sonsuz kaydırma) — FeedScreen'in listState'i sona yaklaşınca
     * çağrılır. DiscoverViewModel.loadMoreDiscover() ile AYNI guard deseni
     * (hasMore/loading iken erken çıkış, tekrar tekrar tetiklenmesi zararsız). */
    fun loadMore() {
        val cursor = _nextCursor.value
        if (!_hasMore.value || _loadingMore.value || cursor == null) return
        viewModelScope.launch {
            _loadingMore.value = true
            when (val result = feedRepository.loadMore(cursor)) {
                is FeedRefreshResult.Success -> {
                    _hasMore.value = result.hasNext
                    _nextCursor.value = result.nextCursor
                    // suggestedUsers burada BİLEREK yok sayılır (backend zaten
                    // sadece cursor=0'da dolduruyor) - mevcut liste KORUNUR.
                }
                is FeedRefreshResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe
                    // (bir sayfa daha yüklenemezse tüm akışı hata durumuna düşürmek istenmiyor).
                }
            }
            _loadingMore.value = false
        }
    }

    /** Madde 2 (önerilen kullanıcılar kartındaki "Takip Et") — başarılı olunca
     * kullanıcı öneriler listesinden ÇIKARILIR (Instagram'ın "takip edince
     * öneri kaybolur" deseni), backend'e ayrı bir "öneriyi gizle" isteği
     * GÖNDERİLMEZ (kapsam dışı, sadece client-side liste güncellenir). */
    fun toggleSuggestedFollow(user: SuggestedUserDto) {
        val username = user.username ?: return
        viewModelScope.launch {
            when (val result = profileRepository.toggleFollow(username)) {
                is ToggleFollowResult.Success -> {
                    _suggestedUsers.value = _suggestedUsers.value.filterNot { it.id == user.id }
                }
                is ToggleFollowResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe.
                }
            }
        }
    }

    /** Kalp ikonuna tıklanınca çağrılır — sunucu yanıtı geldikten SONRA (optimistic
     * DEĞİL) Room cache'i güncellenir, observePosts() otomatik yeniden emit eder. */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(postId)) {
                is ToggleLikeResult.Success -> {
                    feedRepository.updateLikeState(postId, result.count, result.liked)
                }
                is ToggleLikeResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - tek bir post beğenisi
                    // başarısız olursa tüm akışı bir hata state'ine düşürmek istenmiyor.
                }
            }
        }
    }

    /** PostActionsSheet'teki "Postu Sessize Al"/"Sesini Aç" aksiyonu — toggleLike()
     * ile AYNI desen (sunucu yanıtı geldikten SONRA Room cache'i güncellenir,
     * observePosts() otomatik yeniden emit eder). Post'un feed'den GİZLENMESİYLE
     * karıştırılmamalı — bu SADECE bildirim susturur, muted_user_ids'in AKSİNE
     * feed'i filtrelemez (bkz. görev tanımı). */
    fun toggleMutePost(postId: String) {
        viewModelScope.launch {
            when (val result = mutesRepository.toggleMutePost(postId)) {
                is ToggleMutePostResult.Success -> {
                    feedRepository.updateMuteState(postId, result.muted)
                }
                is ToggleMutePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe.
                }
            }
        }
    }

    /** PostActionsSheet'teki "Kaydet"/"Kaydedildi" aksiyonu — toggleMutePost()
     * ile AYNI desen (sunucu yanıtı geldikten SONRA Room cache'i güncellenir,
     * observePosts() otomatik yeniden emit eder). İlk turda collection_id
     * her zaman null (Genel'e kaydedilir), koleksiyon seçimi kaydedilenler
     * ekranında yapılır. */
    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(postId)) {
                is ToggleBookmarkResult.Success -> {
                    feedRepository.updateBookmarkState(postId, result.bookmarked)
                }
                is ToggleBookmarkResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe.
                }
            }
        }
    }

    /** Anket seçeneğine tıklanınca — diğer ViewModel'lerdeki AYNI desen (sunucudan
     * dönen GÜNCEL durum yazılır, optimistic tahmin YAPILMAZ), Room'un poll
     * cache'lememesi yüzünden hedef `_pollOverrides`'a yazılır (yukarıdaki yorum). */
    fun votePoll(postId: String, optionId: String) {
        val pollId = posts.value.firstOrNull { it.id == postId }?.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    _pollOverrides.value = _pollOverrides.value + (postId to Poll(
                        id = pollId,
                        options = result.options,
                        totalVotes = result.totalVotes,
                        myVote = result.myVote,
                    ))
                }
                is VotePollResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    }
                    // Diğer hatalar sessizce yutulur - toggleLike() ile AYNI gerekçe.
                }
            }
        }
    }

    /** PostActionsSheet'teki "Yeniden Paylaş" aksiyonu — bu turda alıntısız
     * hızlı repost (content=""), backend YENİ bir post satırı oluşturuyor,
     * bu yüzden mevcut post state'inde bir alan GÜNCELLENMİYOR (mute/bookmark'ın
     * AKSİNE) — sadece sonuç Toast/Snackbar ile bildirilir. */
    fun repost(postId: String) {
        viewModelScope.launch {
            when (val result = repostsRepository.repost(postId)) {
                is RepostResult.Success -> _events.emit(FeedEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    } else {
                        _events.emit(FeedEvent.ShowToast(mapRepostError(result.code)))
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Bildir" aksiyonu — AlertDialog onayından SONRA
     * çağrılır (bkz. PostActionsSheet.kt), sonucu HER ZAMAN Toast/Snackbar ile
     * bildirilir (already_reported dahil, görev tanımı gereği). */
    fun report(postId: String) {
        viewModelScope.launch {
            when (val result = reportsRepository.reportPost(postId)) {
                is ReportResult.Success -> _events.emit(FeedEvent.ShowToast("Şikayetin alındı, teşekkürler"))
                is ReportResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FeedEvent.SessionExpired)
                    } else {
                        _events.emit(FeedEvent.ShowToast(mapReportError(result.code)))
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

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "Akış yüklenemedi, lütfen tekrar deneyin"
    }
}
