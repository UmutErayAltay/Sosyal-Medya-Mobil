package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.FollowUserDto
import com.umuterayaltay.sosyal.nativeapp.repository.FollowListResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FollowListKind { Followers, Following }

sealed class FollowListEvent {
    data object SessionExpired : FollowListEvent()
}

/** Takipci/takip edilen listesi icin basit ViewModel - username+kind sabit,
 * tek bir users listesi + loading/error. */
class FollowListViewModel(
    private val username: String,
    private val kind: FollowListKind,
) : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _users = MutableStateFlow<List<FollowUserDto>>(emptyList())
    val users: StateFlow<List<FollowUserDto>> = _users.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<FollowListEvent>()
    val events: SharedFlow<FollowListEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = when (kind) {
                FollowListKind.Followers -> profileRepository.getFollowers(username)
                FollowListKind.Following -> profileRepository.getFollowing(username)
            }
            when (result) {
                is FollowListResult.Success -> {
                    _users.value = result.users
                    _title.value = result.title
                }
                is FollowListResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FollowListEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }
}

class FollowListViewModelFactory(
    private val username: String,
    private val kind: FollowListKind,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FollowListViewModel(username, kind) as T
    }
}
