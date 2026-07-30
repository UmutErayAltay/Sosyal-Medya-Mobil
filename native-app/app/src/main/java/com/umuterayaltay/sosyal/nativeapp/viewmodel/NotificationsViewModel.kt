package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.NotificationDto
import com.umuterayaltay.sosyal.nativeapp.repository.NotificationsResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NotificationsEvent {
    data object SessionExpired : NotificationsEvent()
}

/**
 * Bildirimler ekranı için ViewModel — sayfalama deseni ConversationViewModel.
 * loadOlder()'daki gibi basit tutuldu (sonsuz kaydırma DEĞİL, "Daha fazla
 * yükle" butonu — bkz. görev tanımı, sonsuz kaydırma şart değil). Loading/
 * error/event üçlüsü InboxViewModel/FollowRequestsViewModel ile AYNI desen.
 */
class NotificationsViewModel : ViewModel() {

    private val notificationsRepository = ServiceLocator.notificationsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private var page = 1

    private val _notifications = MutableStateFlow<List<NotificationDto>>(emptyList())
    val notifications: StateFlow<List<NotificationDto>> = _notifications.asStateFlow()

    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<NotificationsEvent>()
    val events: SharedFlow<NotificationsEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            page = 1
            when (val result = notificationsRepository.getNotifications(page)) {
                is NotificationsResult.Success -> {
                    _notifications.value = result.notifications
                    _hasNext.value = result.hasNext
                }
                is NotificationsResult.Error -> handleError(result.code)
            }
            _loading.value = false
        }
    }

    fun loadMore() {
        if (_loadingMore.value || _loading.value || !_hasNext.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            val nextPage = page + 1
            when (val result = notificationsRepository.getNotifications(nextPage)) {
                is NotificationsResult.Success -> {
                    page = nextPage
                    _hasNext.value = result.hasNext
                    _notifications.value = _notifications.value + result.notifications
                }
                is NotificationsResult.Error -> handleError(result.code, silent = true)
            }
            _loadingMore.value = false
        }
    }

    private suspend fun handleError(code: String?, silent: Boolean = false) {
        if (code == "unauthorized") {
            tokenStore.clearToken()
            _events.emit(NotificationsEvent.SessionExpired)
            return
        }
        if (silent) return
        _error.value = when (code) {
            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
            else -> "Bildirimler yüklenemedi, lütfen tekrar deneyin"
        }
    }
}
