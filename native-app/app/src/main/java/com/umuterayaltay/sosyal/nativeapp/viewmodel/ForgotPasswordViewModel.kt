package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.ForgotPasswordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ForgotPasswordUiState {
    data object Idle : ForgotPasswordUiState()
    data object Loading : ForgotPasswordUiState()
    // Backend HER DURUMDA {"ok":true} döndüğü için Success burada "e-posta
    // gerçekten var mıydı" bilgisini TAŞIMAZ — enumeration koruması native
    // tarafta da korunur (bkz. AuthRepository.ForgotPasswordResult yorumu).
    data object Success : ForgotPasswordUiState()
    data class Error(val message: String) : ForgotPasswordUiState()
}

/**
 * "Şifremi unuttum" ekranı için ayrı ViewModel — LoginUiState/RegisterUiState'le
 * KARIŞTIRILMASIN diye bilinçli olarak ayrı tutuldu. AuthRepository.forgotPassword()
 * zaten AuthRepository'de (ServiceLocator.authRepository reuse edilir, yeni bir
 * DI GEREKMEZ). Uygulama-içi sıfırlama (deep link) YOK — kullanıcı e-postadaki
 * linkle mevcut web sayfasında ("/reset-password") tamamlar, bu ekran SADECE
 * e-posta gönderme isteğini tetikler.
 */
class ForgotPasswordViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun sendResetLink(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.value = ForgotPasswordUiState.Error("E-posta adresi gerekli")
            return
        }
        _uiState.value = ForgotPasswordUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.forgotPassword(trimmedEmail)) {
                is ForgotPasswordResult.Success -> _uiState.value = ForgotPasswordUiState.Success
                is ForgotPasswordResult.Error -> _uiState.value = ForgotPasswordUiState.Error(mapErrorMessage(result.code))
            }
        }
    }

    fun resetState() {
        _uiState.value = ForgotPasswordUiState.Idle
    }

    // "e-posta yok" gibi bir kod backend'den ZATEN gelmez (enumeration koruması
    // — her durumda ok:true) — burada SADECE ağ/sunucu seviyeli hatalar var.
    private fun mapErrorMessage(code: String?): String = when (code) {
        "rate_limited" -> "Çok fazla deneme yapıldı, lütfen biraz sonra tekrar deneyin"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "İstek gönderilemedi, lütfen tekrar deneyin"
    }
}
