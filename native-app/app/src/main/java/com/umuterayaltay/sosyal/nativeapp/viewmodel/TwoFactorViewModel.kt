package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.SimpleActionResult
import com.umuterayaltay.sosyal.nativeapp.repository.TwoFactorEnrollResult
import com.umuterayaltay.sosyal.nativeapp.repository.TwoFactorStatusResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TwoFactorEvent {
    data object SessionExpired : TwoFactorEvent()
}

/** Etkinleştirme (enroll) diyalogunun hangi adımda olduğunu tutar —
 * AwaitingPassword (şifre giriliyor, henüz backend'e enroll isteği gitmedi),
 * AwaitingCode (enroll BAŞARILI oldu, factorId/secret elde edildi, Authenticator'daki
 * 6 haneli kod bekleniyor). */
sealed class EnrollStep {
    data object AwaitingPassword : EnrollStep()
    data class AwaitingCode(val factorId: String, val secret: String) : EnrollStep()
}

/**
 * "Güvenlik (2FA)" ekranı için ViewModel — SettingsViewModel'deki deaktivasyon
 * diyalogu ile AYNI hata yönetimi/loading deseni (backend {error} -> Türkçe
 * mesaj, IOException -> network_error, "unauthorized" -> token temizle +
 * SessionExpired). Ekran açılınca GET /2fa/status ile mevcut durum yüklenir;
 * enroll/disable BAŞARILI olunca status TEKRAR GET EDİLMEZ, yerel [enabled]
 * state'i doğrudan güncellenir (görev tanımı gereği bilinçli kısayol).
 */
class TwoFactorViewModel : ViewModel() {

    private val settingsRepository = ServiceLocator.settingsRepository
    private val tokenStore = ServiceLocator.tokenStore

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private val _enabled = MutableStateFlow<Boolean?>(null)
    val enabled: StateFlow<Boolean?> = _enabled.asStateFlow()

    // ---- Etkinleştirme (enroll) diyalogu ----
    private val _showEnrollDialog = MutableStateFlow(false)
    val showEnrollDialog: StateFlow<Boolean> = _showEnrollDialog.asStateFlow()

    private val _enrollStep = MutableStateFlow<EnrollStep>(EnrollStep.AwaitingPassword)
    val enrollStep: StateFlow<EnrollStep> = _enrollStep.asStateFlow()

    private val _enrollBusy = MutableStateFlow(false)
    val enrollBusy: StateFlow<Boolean> = _enrollBusy.asStateFlow()

    private val _enrollError = MutableStateFlow<String?>(null)
    val enrollError: StateFlow<String?> = _enrollError.asStateFlow()

    // ---- Kapatma (disable) diyalogu ----
    private val _showDisableDialog = MutableStateFlow(false)
    val showDisableDialog: StateFlow<Boolean> = _showDisableDialog.asStateFlow()

    private val _disableBusy = MutableStateFlow(false)
    val disableBusy: StateFlow<Boolean> = _disableBusy.asStateFlow()

    private val _disableError = MutableStateFlow<String?>(null)
    val disableError: StateFlow<String?> = _disableError.asStateFlow()

    private val _events = MutableSharedFlow<TwoFactorEvent>()
    val events: SharedFlow<TwoFactorEvent> = _events

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _loadError.value = null
            when (val result = settingsRepository.getTwoFactorStatus()) {
                is TwoFactorStatusResult.Success -> _enabled.value = result.enabled
                is TwoFactorStatusResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _events.emit(TwoFactorEvent.SessionExpired)
                    } else {
                        _loadError.value = "Yüklenemedi, lütfen tekrar deneyin"
                    }
                }
            }
            _loading.value = false
        }
    }

    fun openEnrollDialog() {
        _enrollStep.value = EnrollStep.AwaitingPassword
        _enrollError.value = null
        _showEnrollDialog.value = true
    }

    fun dismissEnrollDialog() {
        if (_enrollBusy.value) return
        _showEnrollDialog.value = false
        _enrollStep.value = EnrollStep.AwaitingPassword
        _enrollError.value = null
    }

    /** Adım 1: şifre ile enroll başlatır. Başarılıysa AwaitingCode adımına geçer
     * (dialog KAPANMAZ, aynı diyalog bir sonraki adımı render eder). */
    fun submitPassword(password: String) {
        if (_enrollBusy.value) return
        if (password.isBlank()) {
            _enrollError.value = "Şifreni girmelisin"
            return
        }
        viewModelScope.launch {
            _enrollBusy.value = true
            _enrollError.value = null
            when (val result = settingsRepository.enrollTwoFactor(password)) {
                is TwoFactorEnrollResult.Success -> {
                    _enrollStep.value = EnrollStep.AwaitingCode(result.factorId, result.secret)
                }
                is TwoFactorEnrollResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _showEnrollDialog.value = false
                        _events.emit(TwoFactorEvent.SessionExpired)
                    } else {
                        _enrollError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _enrollBusy.value = false
        }
    }

    /** Adım 2: Authenticator'daki 6 haneli kodla doğrular. Başarılıysa diyalog
     * kapanır, [enabled] YEREL olarak true'ya çevrilir (status TEKRAR GET edilmez). */
    fun submitVerifyCode(password: String, code: String) {
        val step = _enrollStep.value
        if (step !is EnrollStep.AwaitingCode || _enrollBusy.value) return
        if (code.isBlank()) {
            _enrollError.value = "Doğrulama kodu gerekli"
            return
        }
        viewModelScope.launch {
            _enrollBusy.value = true
            _enrollError.value = null
            when (
                val result = settingsRepository.verifyTwoFactorEnroll(password, step.factorId, code.trim())
            ) {
                is SimpleActionResult.Success -> {
                    _showEnrollDialog.value = false
                    _enrollStep.value = EnrollStep.AwaitingPassword
                    _enabled.value = true
                }
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _showEnrollDialog.value = false
                        _events.emit(TwoFactorEvent.SessionExpired)
                    } else {
                        _enrollError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _enrollBusy.value = false
        }
    }

    fun openDisableDialog() {
        _disableError.value = null
        _showDisableDialog.value = true
    }

    fun dismissDisableDialog() {
        if (_disableBusy.value) return
        _showDisableDialog.value = false
        _disableError.value = null
    }

    /** Backend password VE code'u AYNI istekte birlikte ister (bkz.
     * TwoFactorDisableRequest docstring'i) — iki ayrı adım DEĞİL. */
    fun disable(password: String, code: String) {
        if (_disableBusy.value) return
        if (password.isBlank()) {
            _disableError.value = "Şifreni girmelisin"
            return
        }
        if (code.isBlank()) {
            _disableError.value = "Doğrulama kodu gerekli"
            return
        }
        viewModelScope.launch {
            _disableBusy.value = true
            _disableError.value = null
            when (val result = settingsRepository.disableTwoFactor(password, code.trim())) {
                is SimpleActionResult.Success -> {
                    _showDisableDialog.value = false
                    _enabled.value = false
                }
                is SimpleActionResult.Error -> {
                    if (result.code == "unauthorized") {
                        tokenStore.clearToken()
                        _showDisableDialog.value = false
                        _events.emit(TwoFactorEvent.SessionExpired)
                    } else {
                        _disableError.value = mapErrorMessage(result.code)
                    }
                }
            }
            _disableBusy.value = false
        }
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "password_required" -> "Şifreni girmelisin"
        "code_required" -> "Doğrulama kodu gerekli"
        "invalid_password" -> "Şifre yanlış"
        "invalid_code" -> "Geçersiz doğrulama kodu"
        "invalid_code_format" -> "Kod 6 haneli rakamlardan oluşmalı"
        "missing_factor_id" -> "Bir şeyler ters gitti, lütfen tekrar dene"
        "already_enabled" -> "İki adımlı doğrulama zaten aktif"
        "no_active_2fa" -> "İki adımlı doğrulama zaten kapalı"
        "rate_limited" -> "Çok fazla deneme yapıldı, biraz sonra tekrar dene"
        else -> "İşlem başarısız oldu, lütfen tekrar deneyin"
    }
}
