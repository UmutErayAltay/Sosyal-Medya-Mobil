package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.network.SavedSearchItemDto
import com.umuterayaltay.sosyal.nativeapp.network.SearchHistoryItemDto
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.DiscoverPageResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ReportResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.SearchActionResult
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleMutePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import com.umuterayaltay.sosyal.nativeapp.repository.applyVote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview

sealed class DiscoverEvent {
    data object SessionExpired : DiscoverEvent()
    // FeedEvent.ShowToast ile AYNI gerekçe: repost/report sonucu (already_reposted/
    // already_reported dahil) kullanıcıya GÖRÜNÜR bir geri bildirimle gösterilmeli.
    data class ShowToast(val message: String) : DiscoverEvent()
}

/** Arama tipi filtresi — backend `type=all|users|posts|hashtags` sözleşmesiyle BİREBİR. */
enum class SearchType(val apiValue: String, val label: String) {
    All("all", "Tümü"),
    Users("users", "Kullanıcılar"),
    Posts("posts", "Postlar"),
    Hashtags("hashtags", "Hashtag'ler"),
}

/**
 * Keşfet akışı (algoritmik postlar, sayfalanmış) + arama (kullanıcı/post/hashtag
 * + tip filtresi + tarih aralığı + son/kayıtlı aramalar) tek ViewModel'de —
 * DiscoverScreen'in tek bir kaydırılabilir liste olarak render ettiği iki ayrı
 * "akış" burada da iki ayrı state grubu olarak tutuluyor.
 */
@OptIn(FlowPreview::class)
class DiscoverViewModel : ViewModel() {

    private val discoverRepository = ServiceLocator.discoverRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val mutesRepository = ServiceLocator.mutesRepository
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val reportsRepository = ServiceLocator.reportsRepository
    private val tokenStore = ServiceLocator.tokenStore

    // ---- Keşfet akışı ----

    private val _discoverPosts = MutableStateFlow<List<Post>>(emptyList())
    val discoverPosts: StateFlow<List<Post>> = _discoverPosts.asStateFlow()

    private val _discoverPage = MutableStateFlow(0)
    val discoverPage: StateFlow<Int> = _discoverPage.asStateFlow()

    private val _discoverHasMore = MutableStateFlow(true)
    val discoverHasMore: StateFlow<Boolean> = _discoverHasMore.asStateFlow()

    private val _discoverLoading = MutableStateFlow(false)
    val discoverLoading: StateFlow<Boolean> = _discoverLoading.asStateFlow()

    private val _discoverError = MutableStateFlow<String?>(null)
    val discoverError: StateFlow<String?> = _discoverError.asStateFlow()

    // ---- Arama ----

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchType = MutableStateFlow(SearchType.All)
    val searchType: StateFlow<SearchType> = _searchType.asStateFlow()

    private val _dateFrom = MutableStateFlow<String?>(null)
    val dateFrom: StateFlow<String?> = _dateFrom.asStateFlow()

    private val _dateTo = MutableStateFlow<String?>(null)
    val dateTo: StateFlow<String?> = _dateTo.asStateFlow()

    private val _searchUsers = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val searchUsers: StateFlow<List<UserSearchDto>> = _searchUsers.asStateFlow()

    private val _searchPosts = MutableStateFlow<List<Post>>(emptyList())
    val searchPosts: StateFlow<List<Post>> = _searchPosts.asStateFlow()

    private val _searchHashtags = MutableStateFlow<List<HashtagSearchDto>>(emptyList())
    val searchHashtags: StateFlow<List<HashtagSearchDto>> = _searchHashtags.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<SearchHistoryItemDto>>(emptyList())
    val recentSearches: StateFlow<List<SearchHistoryItemDto>> = _recentSearches.asStateFlow()

    private val _savedSearches = MutableStateFlow<List<SavedSearchItemDto>>(emptyList())
    val savedSearches: StateFlow<List<SavedSearchItemDto>> = _savedSearches.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _events = MutableSharedFlow<DiscoverEvent>()
    val events: SharedFlow<DiscoverEvent> = _events

    init {
        // İlk açılış: hem keşfet akışı hem geçmiş/kayıtlı aramalar (search(""))
        // hazır olsun — kullanıcı hiçbir şey yazmadan ekran dolu görünür.
        loadDiscoverPage(1)
        search("")

        // Kullanıcı yazdıkça ANINDA network çağrısı YAPILMAZ — ~400ms yazma
        // durakladıktan sonra tetiklenir (her tuş vuruşunda backend bombalanmasın).
        // drop(1): init'te search("") zaten manuel çağrıldı, StateFlow'un başlangıç
        // değerini burada TEKRAR debounce sonunda göndermesin diye ilk emisyon atlanır.
        _searchQuery
            .drop(1)
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> search(q) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(q: String) {
        _searchQuery.value = q
    }

    fun onSearchTypeChange(type: SearchType) {
        _searchType.value = type
        search(_searchQuery.value)
    }

    fun onDateRangeChange(from: String?, to: String?) {
        _dateFrom.value = from
        _dateTo.value = to
        search(_searchQuery.value)
    }

    fun loadDiscoverPage(page: Int) {
        if (_discoverLoading.value) return
        viewModelScope.launch {
            _discoverLoading.value = true
            _discoverError.value = null
            when (val result = discoverRepository.getDiscoverPage(page)) {
                is DiscoverPageResult.Success -> {
                    _discoverPosts.value = if (page <= 1) {
                        result.posts
                    } else {
                        _discoverPosts.value + result.posts
                    }
                    _discoverPage.value = result.page
                    _discoverHasMore.value = result.hasMore
                }
                is DiscoverPageResult.Error -> {
                    if (result.code == "unauthorized") {
                        // FeedViewModel'deki AYNI MVP kararı: proaktif doğrulama yok,
                        // ilk 401'de yerel token temizlenip login'e dönülür.
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    } else {
                        _discoverError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _discoverLoading.value = false
        }
    }

    /** LazyColumn sonuna gelince veya "daha fazla yükle" butonuna basınca çağrılır. */
    fun loadMoreDiscover() {
        if (!_discoverHasMore.value || _discoverLoading.value) return
        loadDiscoverPage(_discoverPage.value + 1)
    }

    /**
     * init'te ve debounce edilmiş sorgu değişikliklerinde çağrılır. q 2 karakterden
     * kısa olsa BİLE çağrılır — backend bu durumda users/posts/hashtags'i boş,
     * recent_searches/saved_searches'i HER ZAMAN dolu döner (arama kutusu boşken
     * geçmiş+kayıtlıları göstermek için).
     */
    fun search(q: String) {
        viewModelScope.launch {
            _searchLoading.value = true
            _searchError.value = null
            when (
                val result = discoverRepository.search(
                    q = q,
                    type = _searchType.value.apiValue,
                    dateFrom = _dateFrom.value,
                    dateTo = _dateTo.value,
                )
            ) {
                is SearchResult.Success -> {
                    _searchUsers.value = result.data.users
                    _searchPosts.value = result.data.posts
                    _searchHashtags.value = result.data.hashtags
                    _recentSearches.value = result.data.recentSearches
                    _savedSearches.value = result.data.savedSearches
                }
                is SearchResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    } else {
                        _searchError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _searchLoading.value = false
        }
    }

    fun saveCurrentSearch(label: String? = null) {
        val q = _searchQuery.value
        if (q.length < 2) return
        viewModelScope.launch {
            // Kaydetme ikincil bir eylem — başarısız olsa bile arama akışını kesmez,
            // sessizce yutulur (ayrı bir hata state'i bu MVP'de tutulmuyor).
            if (discoverRepository.saveSearch(q, label) is SearchActionResult.Success) {
                search(q)
            }
        }
    }

    fun selectRecentSearch(query: String) {
        _searchQuery.value = query
        search(query)
    }

    fun selectSavedSearch(query: String) {
        _searchQuery.value = query
        search(query)
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            discoverRepository.deleteHistoryItem(id)
            search(_searchQuery.value)
        }
    }

    fun deleteSavedSearch(id: String) {
        viewModelScope.launch {
            discoverRepository.deleteSavedSearch(id)
            search(_searchQuery.value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            discoverRepository.clearHistory()
            search(_searchQuery.value)
        }
    }

    /** Kalp ikonuna tıklanınca çağrılır — hem keşfet akışı hem arama sonuçları
     * listesinde aynı post görünebileceği için ikisi de güncellenir (postId
     * pratikte genelde sadece birinde olur, ama teorik olarak ikisinde de
     * bulunabilir — spesifikasyon gereği). */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(postId)) {
                is ToggleLikeResult.Success -> {
                    _discoverPosts.value = _discoverPosts.value.map { post ->
                        if (post.id == postId) post.copy(likeCount = result.count, likedByMe = result.liked) else post
                    }
                    _searchPosts.value = _searchPosts.value.map { post ->
                        if (post.id == postId) post.copy(likeCount = result.count, likedByMe = result.liked) else post
                    }
                }
                is ToggleLikeResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Postu Sessize Al"/"Sesini Aç" aksiyonu — toggleLike()
     * ile AYNI gerekçeyle hem keşfet akışı hem arama sonuçları güncellenir. */
    fun toggleMutePost(postId: String) {
        viewModelScope.launch {
            when (val result = mutesRepository.toggleMutePost(postId)) {
                is ToggleMutePostResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { post ->
                        if (post.id == postId) post.copy(mutedByMe = result.muted) else post
                    }
                    _discoverPosts.value = apply(_discoverPosts.value)
                    _searchPosts.value = apply(_searchPosts.value)
                }
                is ToggleMutePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Kaydet"/"Kaydedildi" aksiyonu — toggleMutePost()
     * ile AYNI gerekçeyle hem keşfet akışı hem arama sonuçları güncellenir. */
    fun toggleBookmark(postId: String) {
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(postId)) {
                is ToggleBookmarkResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { post ->
                        if (post.id == postId) post.copy(bookmarkedByMe = result.bookmarked) else post
                    }
                    _discoverPosts.value = apply(_discoverPosts.value)
                    _searchPosts.value = apply(_searchPosts.value)
                }
                is ToggleBookmarkResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Anket seçeneğine tıklanınca — toggleLike ile AYNI desen: sunucudan dönen
     * GÜNCEL durum hem keşfet akışına hem arama sonuçlarına yazılır. Optimistic
     * güncelleme YOK (3-yönlü toggle'da yüzdeleri client'ta hesaplamak hatalı olur). */
    fun votePoll(postId: String, optionId: String) {
        val pollId = (_discoverPosts.value + _searchPosts.value)
            .firstOrNull { it.id == postId }?.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    _discoverPosts.value = _discoverPosts.value.map { it.applyVote(postId, result) }
                    _searchPosts.value = _searchPosts.value.map { it.applyVote(postId, result) }
                }
                is VotePollResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Yeniden Paylaş" aksiyonu — FeedViewModel.repost()
     * ile AYNI desen (backend YENİ bir post oluşturuyor, mevcut listelerde
     * güncellenecek bir alan yok, sonuç sadece Toast/Snackbar ile bildirilir). */
    fun repost(postId: String) {
        viewModelScope.launch {
            when (val result = repostsRepository.repost(postId)) {
                is RepostResult.Success -> _events.emit(DiscoverEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    } else {
                        _events.emit(DiscoverEvent.ShowToast(mapRepostError(result.code)))
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Bildir" aksiyonu — FeedViewModel.report() ile AYNI desen. */
    fun report(postId: String) {
        viewModelScope.launch {
            when (val result = reportsRepository.reportPost(postId)) {
                is ReportResult.Success -> _events.emit(DiscoverEvent.ShowToast("Şikayetin alındı, teşekkürler"))
                is ReportResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(DiscoverEvent.SessionExpired)
                    } else {
                        _events.emit(DiscoverEvent.ShowToast(mapReportError(result.code)))
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
        else -> "Yüklenemedi, lütfen tekrar deneyin"
    }
}
