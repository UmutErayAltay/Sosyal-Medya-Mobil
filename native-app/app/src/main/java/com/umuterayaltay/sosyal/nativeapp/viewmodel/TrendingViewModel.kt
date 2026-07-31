package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.repository.TrendingResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TrendingEvent {
    data object SessionExpired : TrendingEvent()
}

/**
 * Gündem ekranı için ViewModel — web'in /gundem'iyle AYNI çağrı
 * (_trending_hashtags(sb, hours=24, limit=50)). NotificationsViewModel'deki
 * basit load()/loading/error üçlüsü, ama sayfalama YOK — backend zaten tek
 * seferde (limit=50) tüm listeyi döner.
 */
class TrendingViewModel : ViewModel() {

    private val hashtagRepository = ServiceLocator.hashtagRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _tags = MutableStateFlow<List<HashtagSearchDto>>(emptyList())
    val tags: StateFlow<List<HashtagSearchDto>> = _tags.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _events = MutableSharedFlow<TrendingEvent>()
    val events: SharedFlow<TrendingEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = hashtagRepository.getTrending()) {
                is TrendingResult.Success -> _tags.value = result.tags
                is TrendingResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(TrendingEvent.SessionExpired)
                    } else {
                        _error.value = when (result.code) {
                            "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
                            else -> "Gündem yüklenemedi, lütfen tekrar deneyin"
                        }
                    }
                }
            }
            _loading.value = false
        }
    }
}
