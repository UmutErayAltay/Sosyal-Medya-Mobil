package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.network.SessionDto
import com.umuterayaltay.sosyal.nativeapp.repository.SessionsResult
import com.umuterayaltay.sosyal.nativeapp.repository.SimpleActionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ActiveSessionsEvent {
    data object SessionExpired : ActiveSessionsEvent()
    /** revokeOthers() BAŞARILI olup liste yeniden yüklendiğinde yayınlanır —
     * ekran bunu SADECE "Diğer Tüm Oturumları Sonlandır" onay diyaloğunu
     * kapatmak için dinler (SettingsEvent.Deactivated'ın AYNI deseni). */
    data object OthersRevoked : ActiveSessionsEvent()
}

/**
 * "Aktif Oturumlar" ekranı için ViewModel — BlockedUsersViewModel'in AYNI
 * temel iskeleti (load() + tek satır aksiyonu), ama İKİ farklı aksiyon var:
 *
 * 1) [revoke] — tek bir cihazı iptal eder. BAŞARILI olunca backend hangi satırın
 *    silindiğini zaten BİZE söylüyor (biz çağırdık), bu yüzden BlockedUsersViewModel.
 *    unblock() ile AYNI "gereksiz reload yapma" kararı: satır listeden LOCAL
 *    olarak filtrelenip çıkarılır, tam yeniden yükleme YAPILMAZ.
 * 2) [revokeOthers] — mevcut cihaz HARİÇ TÜM oturumları iptal eder. Backend
 *    hangi satırların silindiğini DÖNMEDİĞİ için burada tam tersi karar
 *    doğru: BAŞARILI sonrası [load] ile TAM yeniden yükleme yapılır (en
 *    güvenlisi tazelemek — SettingsViewModel.deactivate() sonrası navigasyon
 *    yerine burada basitçe reload yeterli, ekran aynı kalıyor).
 *
 * is_current olan satır için UI baştan revoke butonu GÖSTERMEZ (backend zaten
 * 400 use_logout döner, ama kullanıcıya baştan sunmamak daha temiz bir deneyim
 * — bkz. ActiveSessionsScreen.kt).
 */
class ActiveSessionsViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _sessions = MutableStateFlow<List<SessionDto>>(emptyList())
    val sessions: StateFlow<List<SessionDto>> = _sessions.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Şu an revoke isteği devam eden satırın id'si — o satırda spinner
     * gösterip butonu devre dışı bırakmak için (aynı anda birden fazla
     * satıra tıklanmasın diye). */
    private val _revokingId = MutableStateFlow<String?>(null)
    val revokingId: StateFlow<String?> = _revokingId.asStateFlow()

    private val _revokingOthers = MutableStateFlow(false)
    val revokingOthers: StateFlow<Boolean> = _revokingOthers.asStateFlow()

    private val _revokeOthersError = MutableStateFlow<String?>(null)
    val revokeOthersError: StateFlow<String?> = _revokeOthersError.asStateFlow()

    private val _events = MutableSharedFlow<ActiveSessionsEvent>()
    val events: SharedFlow<ActiveSessionsEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            when (val result = settingsRepository.getSessions()) {
                is SessionsResult.Success -> _sessions.value = result.sessions
                is SessionsResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ActiveSessionsEvent.SessionExpired)
                    } else {
                        _error.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun clearRevokeOthersError() {
        _revokeOthersError.value = null
    }

    fun revoke(sessionId: String) {
        if (_revokingId.value != null) return
        viewModelScope.launch {
            _revokingId.value = sessionId
            when (val result = settingsRepository.revokeSession(sessionId)) {
                is SimpleActionResult.Success -> {
                    _sessions.value = _sessions.value.filterNot { it.id == sessionId }
                }
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ActiveSessionsEvent.SessionExpired)
                    } else {
                        _error.value = "İşlem başarısız, lütfen tekrar deneyin"
                    }
                }
            }
            _revokingId.value = null
        }
    }

    fun revokeOthers() {
        if (_revokingOthers.value) return
        viewModelScope.launch {
            _revokingOthers.value = true
            _revokeOthersError.value = null
            when (val result = settingsRepository.revokeOtherSessions()) {
                is SimpleActionResult.Success -> {
                    load()
                    _events.emit(ActiveSessionsEvent.OthersRevoked)
                }
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(ActiveSessionsEvent.SessionExpired)
                    } else {
                        _revokeOthersError.value = "İşlem başarısız, lütfen tekrar deneyin"
                    }
                }
            }
            _revokingOthers.value = false
        }
    }
}
