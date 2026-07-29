package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileStatsDto
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ProfileResult
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleFollowResult
import com.umuterayaltay.sosyal.nativeapp.repository.toDomain
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProfileEvent {
    data object SessionExpired : ProfileEvent()
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
    private val tokenStore = ServiceLocator.tokenStore

    private var resolvedUsername: String? = requestedUsername

    private val _profile = MutableStateFlow<ProfileDto?>(null)
    val profile: StateFlow<ProfileDto?> = _profile.asStateFlow()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _likedPosts = MutableStateFlow<List<Post>>(emptyList())
    val likedPosts: StateFlow<List<Post>> = _likedPosts.asStateFlow()

    private val _bookmarkedPosts = MutableStateFlow<List<Post>>(emptyList())
    val bookmarkedPosts: StateFlow<List<Post>> = _bookmarkedPosts.asStateFlow()

    private val _archivedPosts = MutableStateFlow<List<Post>>(emptyList())
    val archivedPosts: StateFlow<List<Post>> = _archivedPosts.asStateFlow()

    private val _isSelf = MutableStateFlow(false)
    val isSelf: StateFlow<Boolean> = _isSelf.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _isPendingRequest = MutableStateFlow(false)
    val isPendingRequest: StateFlow<Boolean> = _isPendingRequest.asStateFlow()

    private val _isPrivate = MutableStateFlow(false)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _isBlockedByMe = MutableStateFlow(false)
    val isBlockedByMe: StateFlow<Boolean> = _isBlockedByMe.asStateFlow()

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
                    _stats.value = body.stats
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
