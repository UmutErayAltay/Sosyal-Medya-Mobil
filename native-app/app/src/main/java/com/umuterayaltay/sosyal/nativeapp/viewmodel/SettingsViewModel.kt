package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.DeactivateResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SettingsEvent {
    /** Deaktivasyon başarılı — token ZATEN bu ViewModel içinde temizlendi
     * (bkz. deactivate()), UI sadece login'e navigasyonu tetiklemeli. */
    data object Deactivated : SettingsEvent()
    data object SessionExpired : SettingsEvent()
}

/**
 * "Ayarlar" menü ekranı için ViewModel — menünün kendisi (Profili Düzenle/
 * Bildirim Tercihleri/Yakın Arkadaşlar satırları) SADECE navigasyon linki,
 * state gerektirmez. Bu ViewModel'in TEK sorumluluğu "Hesabı Deaktive Et"
 * diyalogunun API çağrısını ve token temizleme akışını yönetmek.
 *
 * ÖNEMLİ: deactivate() BAŞARILI olunca token'ın kendisi sunucu tarafında
 * ZATEN iptal edilmiş olur (bkz. app/api_v1.py api_deactivate_account()) — bu
 * yüzden burada TokenStore.clearToken() de MUTLAKA çağrılır (aksi halde eski,
 * artık geçersiz bir token'la sonraki her istek 401 döner). SettingsScreen
 * bu event'i alınca sadece onDeactivated() callback'ini çağırır (NavHost
 * login'e yönlendirir) — screen kendisi token'a dokunmaz.
 */
class SettingsViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _deactivating = MutableStateFlow(false)
    val deactivating: StateFlow<Boolean> = _deactivating.asStateFlow()

    private val _deactivateError = MutableStateFlow<String?>(null)
    val deactivateError: StateFlow<String?> = _deactivateError.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events

    fun clearDeactivateError() {
        _deactivateError.value = null
    }

    fun deactivate(password: String?) {
        if (_deactivating.value) return
        viewModelScope.launch {
            _deactivating.value = true
            _deactivateError.value = null
            when (val result = settingsRepository.deactivateAccount(password?.takeIf { it.isNotBlank() })) {
                is DeactivateResult.Success -> {
                    tokenStore.clearToken()
                    _events.emit(SettingsEvent.Deactivated)
                }
                is DeactivateResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(SettingsEvent.SessionExpired)
                    } else {
                        _deactivateError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _deactivating.value = false
        }
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "password_required" -> "Şifreni girmelisin"
        "invalid_password" -> "Şifre yanlış"
        "rate_limited" -> "Çok fazla deneme yapıldı, biraz sonra tekrar dene"
        else -> "Deaktivasyon başarısız oldu, lütfen tekrar deneyin"
    }
}
