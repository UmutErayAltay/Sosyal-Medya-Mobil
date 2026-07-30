package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.CloseFriendsResult
import com.umuterayaltay.sosyal.nativeapp.repository.SearchResult
import com.umuterayaltay.sosyal.nativeapp.repository.SimpleActionResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class CloseFriendsEvent {
    data object SessionExpired : CloseFriendsEvent()
}

/**
 * "Yakın Arkadaşlar" ekranı için ViewModel. Kullanıcı aramak için YENİ bir
 * endpoint İCAT EDİLMEDİ — NewMessageViewModel'deki AYNI desen: mevcut
 * DiscoverRepository.search(type="users") debounce'lı reuse edilir. add/remove
 * sonrası FollowRequestsViewModel'deki AYNI basit yaklaşım: optimistic
 * güncelleme yerine listeyi yeniden fetch ederiz (bu aksiyonlar sık
 * tetiklenmiyor, ekstra state senkron riskine değmez).
 */
@OptIn(FlowPreview::class)
class CloseFriendsViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val discoverRepository = ServiceLocator.discoverRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _friends = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val friends: StateFlow<List<UserSearchDto>> = _friends.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserSearchDto>>(emptyList())
    val searchResults: StateFlow<List<UserSearchDto>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<CloseFriendsEvent>()
    val events: SharedFlow<CloseFriendsEvent> = _events

    init {
        load()
        _searchQuery
            .debounce(400)
            .distinctUntilChanged()
            .onEach { q -> if (q.length >= 2) runSearch(q) else _searchResults.value = emptyList() }
            .launchIn(viewModelScope)
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = settingsRepository.getCloseFriends()) {
                is CloseFriendsResult.Success -> _friends.value = result.users
                is CloseFriendsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(CloseFriendsEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun onSearchQueryChange(text: String) {
        _searchQuery.value = text
    }

    private fun runSearch(q: String) {
        viewModelScope.launch {
            _searching.value = true
            when (val result = discoverRepository.search(q, type = "users", dateFrom = null, dateTo = null)) {
                is SearchResult.Success -> _searchResults.value = result.data.users
                is SearchResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(CloseFriendsEvent.SessionExpired)
                    }
                }
            }
            _searching.value = false
        }
    }

    fun add(userId: String) {
        viewModelScope.launch {
            when (val result = settingsRepository.addCloseFriend(userId)) {
                is SimpleActionResult.Success -> load()
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(CloseFriendsEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "cannot_add_self" -> "Kendini ekleyemezsin"
                            "blocked" -> "Bu kullanıcı engellenmiş"
                            else -> "Eklenemedi, lütfen tekrar deneyin"
                        }
                    }
                }
            }
        }
    }

    fun remove(userId: String) {
        viewModelScope.launch {
            when (val result = settingsRepository.removeCloseFriend(userId)) {
                is SimpleActionResult.Success -> load()
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(CloseFriendsEvent.SessionExpired)
                    } else {
                        _error.value = "Çıkarılamadı, lütfen tekrar deneyin"
                    }
                }
            }
        }
    }
}
