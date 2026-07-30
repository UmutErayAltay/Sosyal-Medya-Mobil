package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.FeedRefreshResult
import com.umuterayaltay.sosyal.nativeapp.repository.Post
import com.umuterayaltay.sosyal.nativeapp.repository.ToggleLikeResult
import com.umuterayaltay.sosyal.nativeapp.repository.UnreadCountResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
}

class FeedViewModel : ViewModel() {

    private val feedRepository = ServiceLocator.feedRepository
    private val interactionsRepository = ServiceLocator.interactionsRepository
    private val notificationsRepository = ServiceLocator.notificationsRepository
    private val tokenStore = ServiceLocator.tokenStore

    val posts: StateFlow<List<Post>> = feedRepository.observePosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                is FeedRefreshResult.Success -> _uiState.value = FeedUiState.Success
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

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "Akış yüklenemedi, lütfen tekrar deneyin"
    }
}
