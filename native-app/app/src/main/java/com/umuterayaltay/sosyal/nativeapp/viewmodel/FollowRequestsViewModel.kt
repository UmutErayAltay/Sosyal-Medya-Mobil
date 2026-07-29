package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.FollowUserDto
import com.umuterayaltay.sosyal.nativeapp.repository.FollowRequestsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FollowRequestsEvent {
    data object SessionExpired : FollowRequestsEvent()
}

/**
 * Bana gelen bekleyen takip istekleri. accept/reject sonrasi basit yaklasim
 * secildi: optimistic kaldirma yerine listeyi YENIDEN fetch ediyoruz (bu
 * aksiyonlar sik tetiklenmiyor, ekstra state senkron riskine degmez).
 */
class FollowRequestsViewModel : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _requests = MutableStateFlow<List<FollowUserDto>>(emptyList())
    val requests: StateFlow<List<FollowUserDto>> = _requests.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<FollowRequestsEvent>()
    val events: SharedFlow<FollowRequestsEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = profileRepository.getFollowRequests()) {
                is FollowRequestsResult.Success -> _requests.value = result.users
                is FollowRequestsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(FollowRequestsEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun accept(id: String) {
        viewModelScope.launch {
            profileRepository.acceptFollowRequest(id)
            load()
        }
    }

    fun reject(id: String) {
        viewModelScope.launch {
            profileRepository.rejectFollowRequest(id)
            load()
        }
    }
}
