package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.CollectionDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileStatsDto
import com.umuterayaltay.sosyal.nativeapp.repository.CollectionsResult
import com.umuterayaltay.sosyal.nativeapp.repository.DeletePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.EditPostResult
import com.umuterayaltay.sosyal.nativeapp.repository.Highlight
import com.umuterayaltay.sosyal.nativeapp.repository.HighlightsResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ProfileResult
import com.umuterayaltay.sosyal.nativeapp.repository.ReportResult
import com.umuterayaltay.sosyal.nativeapp.repository.RepostResult
import com.umuterayaltay.sosyal.nativeapp.repository.StartConversationResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleArchiveResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBlockResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleBookmarkResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleFollowResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleMutePostResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleUserMuteResult
import com.umuterayaltay.sosyal.nativeapp.repository.TogglePinResult
import com.umuterayaltay.sosyal.nativeapp.repository.VotePollResult
import com.umuterayaltay.sosyal.nativeapp.repository.applyVote
import com.umuterayaltay.sosyal.nativeapp.repository.toDomain
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileEvent {
    data object SessionExpired : ProfileEvent()
    // FeedEvent.ShowToast ile AYNI gerekçe — repost/report sonucu GÖRÜNÜR bildirim ister.
    data class ShowToast(val message: String) : ProfileEvent()
    // 2026-08-22 (kullanıcı isteği: "başkasının profili boş görünüyor" —
    // web'in profile.html'deki "Mesaj gönder" butonunun native'de HİÇ
    // karşılığı yoktu) — NewMessageViewModel.selectUser()'daki AYNI
    // startConversation() çağrısı/sonuç deseni.
    data class ConversationReady(val conversationId: String) : ProfileEvent()
}

/**
 * Profil ekrani icin ViewModel - username null ise KENDI profilimiz demektir:
 * once AuthRepository.getCurrentUser() (AuthApi.me()) ile kendi username'imiz
 * cozulur, SONRA profileRepository.getProfile(username) cagrilir. Bir kez
 * cozulen username sonraki refresh/toggleFollow cagrilarinda TEKRAR me() istegi
 * yapmamak icin resolvedUsername'de saklanir.
 */
class ProfileViewModel(private val requestedUsername: String?) : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val authRepository = ServiceLocator.authRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val pollsRepository = ServiceLocator.pollsRepository
    private val mutesRepository = ServiceLocator.mutesRepository
    private val bookmarksRepository = ServiceLocator.bookmarksRepository
    private val storiesRepository = ServiceLocator.storiesRepository
    private val repostsRepository = ServiceLocator.repostsRepository
    private val reportsRepository = ServiceLocator.reportsRepository
    private val messagingRepository = ServiceLocator.messagingRepository
    private val tokenStore = ServiceLocator.tokenStore

    // "Mesaj gönder" butonu çift tıklamayla iki ayrı konuşma başlatma isteği
    // atmasın diye — FollowActionButton'daki AYNI "isteği tekrar gönderme"
    // deseni (bkz. NewMessageViewModel._starting).
    private var startingConversation = false

    private var resolvedUsername: String? = requestedUsername

    private val _profile = MutableStateFlow<ProfileDto?>(null)
    val profile: StateFlow<ProfileDto?> = _profile.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _likedPosts = MutableStateFlow<List<Post>>(emptyList())
    val likedPosts: StateFlow<List<Post>> = _likedPosts.asStateFlow()

    private val _bookmarkedPosts = MutableStateFlow<List<Post>>(emptyList())
    val bookmarkedPosts: StateFlow<List<Post>> = _bookmarkedPosts.asStateFlow()

    // Faz 5 Dalga 3A — "Kaydedilenler" sekmesindeki koleksiyon filtresi için
    // (bkz. görev tanımı). SADECE isSelf profilinde anlamlı, başkasının
    // profilinde bookmarkedPosts zaten boş dönüyor (backend _serialize_profile
    // sadece sahibine dolduruyor).
    private val _collections = MutableStateFlow<List<CollectionDto>>(emptyList())
    val collections: StateFlow<List<CollectionDto>> = _collections.asStateFlow()

    private val _archivedPosts = MutableStateFlow<List<Post>>(emptyList())
    val archivedPosts: StateFlow<List<Post>> = _archivedPosts.asStateFlow()

    // Faz 5 Dalga 4B — web'in profile.html'deki AYNI highlight-bar'ı: herkese
    // AÇIK (isSelf şartı YOK, _collections'ın AKSİNE), bu yüzden hem kendi hem
    // başkasının profilinde yüklenir.
    private val _highlights = MutableStateFlow<List<Highlight>>(emptyList())
    val highlights: StateFlow<List<Highlight>> = _highlights.asStateFlow()

    private val _isSelf = MutableStateFlow(false)
    val isSelf: StateFlow<Boolean> = _isSelf.asStateFlow()

    // Post yönetimi (düzenle/sil/arşivle/sabitle) — FeedViewModel.currentUserId
    // ile AYNI gerekçe/desen. isSelf İLE AYNI ŞEY DEĞİL: "Beğenilenler"/
    // "Kaydedilenler" sekmeleri BAŞKALARININ postlarını da gösterebilir (isSelf
    // sadece "bu PROFİL benim mi" demek), o yüzden her post için AYRI ayrI
    // post.userId == currentUserId karşılaştırması gerekir.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _isPendingRequest = MutableStateFlow(false)
    val isPendingRequest: StateFlow<Boolean> = _isPendingRequest.asStateFlow()

    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _isBlockedByMe = MutableStateFlow(false)
    val isBlockedByMe: StateFlow<Boolean> = _isBlockedByMe.asStateFlow()

    // Faz 5 Dalga 2A — profil.isMuted DTO'da zaten dolu geliyordu (backend
    // api_v1/profile.py), sadece hiç okunmuyordu (bkz. görev tanımı). loadProfile()
    // sonunda buraya yazılır, toggleUserMute() ile güncellenir.
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isDeactivated = MutableStateFlow(false)
    val isDeactivated: StateFlow<Boolean> = _isDeactivated.asStateFlow()

    private val _stats = MutableStateFlow<ProfileStatsDto?>(null)
    val stats: StateFlow<ProfileStatsDto?> = _stats.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEvent>()
    val events: SharedFlow<ProfileEvent> = _events

    init {
        loadProfile()
        viewModelScope.launch { _currentUserId.value = authRepository.getCurrentUserId() }
    }

    fun loadProfile() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val username = resolvedUsername ?: run {
                val me = authRepository.getCurrentUser()
                val meUsername = me?.username
                if (meUsername.isNullOrBlank()) {
                    // me() basarisiz/401 - MVP karari: token temizle, login'e don
                    // (FeedViewModel/DiscoverViewModel'deki AYNI desen).
                    tokenStore.clearToken()
                    _events.emit(ProfileEvent.SessionExpired)
                    _loading.value = false
                    return@launch
                }
                resolvedUsername = meUsername
                meUsername
            }

            when (val result = profileRepository.getProfile(username)) {
                is ProfileResult.Success -> {
                    val body = result.data
                    _profile.value = body.profile
                    _isDeactivated.value = body.deactivated
                    _posts.value = (body.posts ?: emptyList()).map { it.toDomain() }
                    _likedPosts.value = (body.likedPosts ?: emptyList()).map { it.toDomain() }
                    _bookmarkedPosts.value = (body.bookmarkedPosts ?: emptyList()).map { it.toDomain() }
                    _archivedPosts.value = (body.archivedPosts ?: emptyList()).map { it.toDomain() }
                    _isSelf.value = body.isSelf
                    _isFollowing.value = body.isFollowing
                    _isPendingRequest.value = body.isPendingRequest
                    _isPrivate.value = body.isPrivate
                    _isBlockedByMe.value = body.isBlockedByMe
                    _isMuted.value = body.isMuted
                    _stats.value = body.stats
                    // SADECE kendi profilimizde anlamlı (bkz. _collections yorumu) —
                    // başkasının profilinde gereksiz bir istek atılmasın.
                    if (body.isSelf) loadCollections()
                    body.profile?.id?.let { loadHighlights(it) }
                }
                is ProfileResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }
            _loading.value = false
        }
    }

    fun toggleFollow() {
        val username = resolvedUsername ?: return
        viewModelScope.launch {
            when (val result = profileRepository.toggleFollow(username)) {
                is ToggleFollowResult.Success -> {
                    _isFollowing.value = result.following
                    _isPendingRequest.value = result.isPending
                    // Tutarlilik icin profili YENIDEN yukle (takipci sayisi/stats
                    // degismis olabilir) - spesifikasyon geregi.
                    loadProfile()
                }
                is ToggleFollowResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _error.value = mapErrorMessage(result.code)
                    }
                }
            }
        }
    }

    /** Profil ekranındaki "Engelle"/"Engeli Kaldır" aksiyonu — toggleFollow()'un
     * AKSİNE tam bir loadProfile() TEKRARI YAPILMAZ (spesifikasyon gereği):
     * backend {"ok":true,"blocked":bool} döner, bu YENİ durum doğrudan
     * _isBlockedByMe'ye yazılır. Backend blok BAŞARILI olunca karşılıklı takip
     * ilişkisini de koparıyor (her iki yönde, bkz. app/api_v1.py
     * api_toggle_block() docstring'i) — bu yan etki loadProfile() olmadan
     * yansımaz, bu yüzden blocked=true durumunda isFollowing/isPendingRequest
     * de burada local olarak sıfırlanır (stale "Takip Ediliyor" state'i
     * önlenir). unblock'ta (blocked=false) takip durumu zaten kopmuş
     * durumdaydı, dokunulmaz. */
    fun toggleBlock() {
        val username = resolvedUsername ?: return
        viewModelScope.launch {
            when (val result = profileRepository.toggleBlock(username)) {
                is ToggleBlockResult.Success -> {
                    _isBlockedByMe.value = result.blocked
                    if (result.blocked) {
                        _isFollowing.value = false
                        _isPendingRequest.value = false
                    }
                }
                is ToggleBlockResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "cannot_block_self" -> "Kendini engelleyemezsin"
                            "not_found" -> "Kullanıcı bulunamadı"
                            else -> "İşlem başarısız, lütfen tekrar deneyin"
                        }
                    }
                }
            }
        }
    }

    /** Profil ekranındaki "Sessize Al"/"Sesi Aç" aksiyonu — toggleBlock() ile AYNI
     * desen (sunucudan dönen YENİ durum doğrudan _isMuted'e yazılır, loadProfile()
     * tekrarı YOK). Kullanıcı mute etmek feed'i FİLTRELER (muted_user_ids), ama bu
     * BAŞKASININ profilinde etkisi yoktur — profil/keşfet/arama muted kişinin
     * postlarını YİNE gösterir (bkz. app/mutes.py docstring'i), bu yüzden burada
     * posts listesine dokunulmaz. */
    fun toggleUserMute() {
        val targetId = profile.value?.id ?: return
        viewModelScope.launch {
            when (val result = mutesRepository.toggleUserMute(targetId)) {
                is ToggleUserMuteResult.Success -> _isMuted.value = result.muted
                is ToggleUserMuteResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "cannot_mute_self" -> "Kendini sessize alamazsın"
                            else -> "İşlem başarısız, lütfen tekrar deneyin"
                        }
                    }
                }
            }
        }
    }

    /** ProfileHeader'daki "Mesaj Gönder" butonu — NewMessageViewModel.selectUser()
     * ile AYNI startConversation() çağrısı/hata haritası, sadece sonuç bir
     * event ile ProfileScreen'e taşınıp navigasyon oradan tetiklenir. */
    fun startConversation() {
        val username = resolvedUsername ?: return
        if (startingConversation) return
        startingConversation = true
        viewModelScope.launch {
            when (val result = messagingRepository.startConversation(username)) {
                is StartConversationResult.Success -> _events.emit(ProfileEvent.ConversationReady(result.conversationId))
                is StartConversationResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "blocked" -> "Bu kullanıcıyla mesajlaşamazsınız"
                            "not_found" -> "Kullanıcı bulunamadı"
                            else -> "Konuşma başlatılamadı, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            startingConversation = false
        }
    }

    /** PostActionsSheet'teki "Postu Sessize Al"/"Sesini Aç" aksiyonu —
     * toggleLike() ile AYNI gerekçeyle posts/likedPosts/archivedPosts'ın HEPSİ
     * güncellenir (aynı post birden fazla sekmede görünebilir). */
    fun toggleMutePost(postId: String) {
        viewModelScope.launch {
            when (val result = mutesRepository.toggleMutePost(postId)) {
                is ToggleMutePostResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { post ->
                        if (post.id == postId) post.copy(mutedByMe = result.muted) else post
                    }
                    _posts.value = apply(_posts.value)
                    _likedPosts.value = apply(_likedPosts.value)
                    _archivedPosts.value = apply(_archivedPosts.value)
                }
                is ToggleMutePostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Profil başlığındaki highlight-bar için — loadProfile() HER ZAMAN çeker
     * (kendi/başkasının profili farketmez, web'in `{% if highlights %}`'ıyla
     * AYNI: liste boşsa ProfileScreen bar'ı hiç göstermez). */
    private fun loadHighlights(userId: String) {
        viewModelScope.launch {
            when (val result = storiesRepository.getHighlights(userId)) {
                is HighlightsResult.Success -> _highlights.value = result.highlights
                is HighlightsResult.Error -> Unit // sessizce yutulur, toggleUserMute() ile AYNI gerekçe
            }
        }
    }

    /** 2026-08-09 (kullanıcı raporu: "öne çıkarılanlara ekleyince uygulamayı
     * aç kapa yapmak zorunda kalıyorum") — StoryViewerScreen'den bir
     * highlight kaydedildikten sonra ProfileScreen tarafından çağrılır
     * (bkz. AppNavHost.kt/MainScaffold.kt "highlight_changed" savedStateHandle
     * deseni). Profil henüz yüklenmediyse ([_profile] null) no-op — bu
     * durumda zaten loadProfile() en güncel highlight'ları getirecek. */
    fun refreshHighlights() {
        _profile.value?.id?.let { loadHighlights(it) }
    }

    /** "Kaydedilenler" sekmesi için koleksiyon listesi — loadProfile() isSelf ise
     * otomatik çeker, koleksiyon oluşturma/silmeden sonra da manuel çağrılabilir. */
    fun loadCollections() {
        viewModelScope.launch {
            when (val result = bookmarksRepository.getCollections()) {
                is CollectionsResult.Success -> _collections.value = result.collections
                is CollectionsResult.Error -> Unit // sessizce yutulur, toggleUserMute() ile AYNI gerekçe
            }
        }
    }

    /** PostActionsSheet'teki "Kaydet"/"Kaydedildi" aksiyonu — toggleMutePost() ile
     * AYNI desen (posts/likedPosts/archivedPosts'ın HEPSİ güncellenir). Kaldırılan
     * bookmark ("Kaydedilenler" sekmesinde iken tekrar tıklanırsa) bookmarkedPosts
     * listesinden de ÇIKARILIR — aksi halde sekme, artık kaydedilmemiş bir postu
     * göstermeye devam ederdi. */
    fun toggleBookmark(postId: String, collectionId: String? = null) {
        viewModelScope.launch {
            when (val result = bookmarksRepository.toggleBookmark(postId, collectionId)) {
                is ToggleBookmarkResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { post ->
                        if (post.id == postId) post.copy(bookmarkedByMe = result.bookmarked) else post
                    }
                    _posts.value = apply(_posts.value)
                    _likedPosts.value = apply(_likedPosts.value)
                    _archivedPosts.value = apply(_archivedPosts.value)
                    _bookmarkedPosts.value = if (result.bookmarked) {
                        apply(_bookmarkedPosts.value)
                    } else {
                        _bookmarkedPosts.value.filterNot { it.id == postId }
                    }
                }
                is ToggleBookmarkResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Kalp ikonuna tıklanınca çağrılır — aynı post birden fazla sekmede/listede
     * görünebilir (ör. hem "Gönderiler" hem "Beğenilenler"de), bu yüzden
     * posts/likedPosts/archivedPosts/bookmarkedPosts'ın HEPSİ güncellenir (Faz 5
     * Dalga 3A'dan beri "Kaydedilenler" sekmesi de var, bkz. toggleBookmark()). */
    fun toggleLike(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleLike(postId)) {
                is ToggleLikeResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { post ->
                        if (post.id == postId) post.copy(likeCount = result.count, likedByMe = result.liked) else post
                    }
                    _posts.value = apply(_posts.value)
                    _likedPosts.value = apply(_likedPosts.value)
                    _archivedPosts.value = apply(_archivedPosts.value)
                    _bookmarkedPosts.value = apply(_bookmarkedPosts.value)
                }
                is ToggleLikeResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** Anket seçeneğine tıklanınca — toggleLike() ile AYNI gerekçeyle
     * posts/likedPosts/archivedPosts'ın HEPSİ güncellenir (aynı post birden
     * fazla sekmede görünebilir). Sunucudan dönen GÜNCEL durum yazılır,
     * optimistic tahmin YAPILMAZ (DiscoverViewModel.votePoll() ile AYNI desen). */
    fun votePoll(postId: String, optionId: String) {
        val pollId = (_posts.value + _likedPosts.value + _archivedPosts.value)
            .firstOrNull { it.id == postId }?.poll?.id ?: return
        viewModelScope.launch {
            when (val result = pollsRepository.vote(pollId, optionId)) {
                is VotePollResult.Success -> {
                    fun apply(list: List<Post>): List<Post> = list.map { it.applyVote(postId, result) }
                    _posts.value = apply(_posts.value)
                    _likedPosts.value = apply(_likedPosts.value)
                    _archivedPosts.value = apply(_archivedPosts.value)
                }
                is VotePollResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Yeniden Paylaş" aksiyonu — FeedViewModel.repost()
     * ile AYNI desen (backend YENİ bir post oluşturuyor, listelerde
     * güncellenecek bir alan yok, sonuç sadece Toast/Snackbar ile bildirilir). */
    fun repost(postId: String) {
        viewModelScope.launch {
            when (val result = repostsRepository.repost(postId)) {
                is RepostResult.Success -> _events.emit(ProfileEvent.ShowToast("Yeniden paylaşıldı"))
                is RepostResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _events.emit(ProfileEvent.ShowToast(mapRepostError(result.code)))
                    }
                }
            }
        }
    }

    /** PostActionsSheet'teki "Bildir" aksiyonu — FeedViewModel.report() ile AYNI desen. */
    fun report(postId: String) {
        viewModelScope.launch {
            when (val result = reportsRepository.reportPost(postId)) {
                is ReportResult.Success -> _events.emit(ProfileEvent.ShowToast("Şikayetin alındı, teşekkürler"))
                is ReportResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ProfileEvent.SessionExpired)
                    } else {
                        _events.emit(ProfileEvent.ShowToast(mapReportError(result.code)))
                    }
                }
            }
        }
    }

    /** editPost/deletePost/toggleArchive/togglePin — ProfileScreen'de post
     * dört AYRI listede ({@code _posts}/{@code _likedPosts}/{@code
     * _bookmarkedPosts}/{@code _archivedPosts}) görünebildiği için (ör. kendi
     * postunu hem "Gönderiler" hem "Kaydedilenler"de görebilirsin), edit tüm
     * dörtte içerik günceller; delete/archive TÜM dörtten çıkarır (arşivlenen/
     * arşivden çıkan bir postun HANGİ sekmede belireceği ayrı bir fetch işi,
     * burada proaktif eklenmiyor — bir sonraki sekme yüklemesi doğru listeyi
     * getirir). */
    fun editPost(postId: String, content: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.editPost(postId, content)) {
                is EditPostResult.Success -> {
                    val update: (Post) -> Post = { post ->
                        if (post.id == postId) post.copy(content = result.content) else post
                    }
                    _posts.value = _posts.value.map(update)
                    _likedPosts.value = _likedPosts.value.map(update)
                    _bookmarkedPosts.value = _bookmarkedPosts.value.map(update)
                    _archivedPosts.value = _archivedPosts.value.map(update)
                    _events.emit(ProfileEvent.ShowToast("Post güncellendi"))
                }
                is EditPostResult.Error -> handlePostManagementError(result.code, "Post güncellenemedi")
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.deletePost(postId)) {
                is DeletePostResult.Success -> {
                    _posts.value = _posts.value.filterNot { it.id == postId }
                    _likedPosts.value = _likedPosts.value.filterNot { it.id == postId }
                    _bookmarkedPosts.value = _bookmarkedPosts.value.filterNot { it.id == postId }
                    _archivedPosts.value = _archivedPosts.value.filterNot { it.id == postId }
                    _events.emit(ProfileEvent.ShowToast("Post silindi"))
                }
                is DeletePostResult.Error -> handlePostManagementError(result.code, "Post silinemedi")
            }
        }
    }

    fun toggleArchive(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.toggleArchive(postId)) {
                is ToggleArchiveResult.Success -> {
                    _posts.value = _posts.value.filterNot { it.id == postId }
                    _likedPosts.value = _likedPosts.value.filterNot { it.id == postId }
                    _bookmarkedPosts.value = _bookmarkedPosts.value.filterNot { it.id == postId }
                    _archivedPosts.value = _archivedPosts.value.filterNot { it.id == postId }
                    _events.emit(
                        ProfileEvent.ShowToast(if (result.isArchived) "Post arşivlendi" else "Post arşivden çıkarıldı"),
                    )
                }
                is ToggleArchiveResult.Error -> handlePostManagementError(result.code, "İşlem başarısız")
            }
        }
    }

    fun togglePin(postId: String) {
        viewModelScope.launch {
            when (val result = interactionsRepository.togglePin(postId)) {
                is TogglePinResult.Success -> _events.emit(
                    ProfileEvent.ShowToast(if (result.pinned) "Profilinin en üstüne sabitlendi" else "Sabitleme kaldırıldı"),
                )
                is TogglePinResult.Error -> handlePostManagementError(result.code, "İşlem başarısız")
            }
        }
    }

    private suspend fun handlePostManagementError(code: String?, fallback: String) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(ProfileEvent.SessionExpired)
            return
        }
        val message = when (code) {
            "not_found" -> "Bu post artık mevcut değil"
            "empty_post" -> "Post boş olamaz"
            "unavailable" -> "Şu anda kullanılamıyor, daha sonra tekrar dene"
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> fallback
        }
        _events.emit(ProfileEvent.ShowToast(message))
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
        "not_found" -> "Profil bulunamadı"
        else -> "Profil yüklenemedi, lütfen tekrar deneyin"
    }
}

/** ProfileViewModel constructor'i username parametresi aldigi icin Compose'un
 * varsayilan (parametresiz) factory'si yetmez - bu basit factory ile viewModel()
 * cagrisina gecilir. */
class ProfileViewModelFactory(private val username: String?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(username) as T
    }
}
