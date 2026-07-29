package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

class AuthViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("E-posta ve şifre gerekli")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (val result = authRepository.login(email.trim(), password, deviceName = android.os.Build.MODEL)) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success
                is AuthResult.Error -> _uiState.value = LoginUiState.Error(mapErrorMessage(result.code))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = LoginUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "invalid_credentials" -> "E-posta veya şifre hatalı"
        "mfa_required" -> "Bu hesapta 2FA aktif, native uygulama henüz desteklemiyor"
        "missing_credentials" -> "E-posta ve şifre gerekli"
        "banned" -> "Bu hesap askıya alınmış"
        "rate_limited" -> "Çok fazla deneme yapıldı, lütfen biraz sonra tekrar deneyin"
        "token_creation_failed" -> "Giriş yapılamadı, lütfen tekrar deneyin"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "Giriş yapılamadı, lütfen tekrar deneyin"
    }
}
