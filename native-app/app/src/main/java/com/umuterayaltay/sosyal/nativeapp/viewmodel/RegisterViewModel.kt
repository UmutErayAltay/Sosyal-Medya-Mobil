package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data object Success : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}

/**
 * "Kayıt Ol" ekranı için ayrı ViewModel — LoginUiState/AuthViewModel'le
 * KARIŞTIRILMASIN diye bilinçli olarak ayrı tutuldu (register akışının kendi
 * Idle/Loading/Success/Error döngüsü var, 2FA'ya girmez — app/api_v1.py
 * register() hiç mfa_required döndürmez, sadece login() ve google() döndürür).
 * AuthRepository.register() zaten AuthRepository'de (ServiceLocator.authRepository
 * reuse edilir, yeni bir DI GEREKMEZ).
 */
class RegisterViewModel : ViewModel() {

    private val authRepository = ServiceLocator.authRepository

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(email: String, password: String, username: String) {
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()
        if (trimmedEmail.isBlank() || password.isBlank() || trimmedUsername.isBlank()) {
            _uiState.value = RegisterUiState.Error("Tüm alanları doldur")
            return
        }
        if (trimmedUsername.length < 3) {
            _uiState.value = RegisterUiState.Error("Kullanıcı adı en az 3 karakter olmalı")
            return
        }
        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            when (
                val result = authRepository.register(
                    trimmedEmail,
                    password,
                    trimmedUsername,
                    deviceName = android.os.Build.MODEL,
                )
            ) {
                is AuthResult.Success -> _uiState.value = RegisterUiState.Success
                is AuthResult.Error -> _uiState.value = RegisterUiState.Error(mapErrorMessage(result.code))
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }

    private fun mapErrorMessage(code: String?): String = when (code) {
        "missing_fields" -> "Tüm alanları doldur"
        "short_username" -> "Kullanıcı adı en az 3 karakter olmalı"
        "username_taken" -> "Bu kullanıcı adı zaten kullanılıyor"
        "email_taken" -> "Bu e-posta zaten kayıtlı"
        "register_failed" -> "Kayıt oluşturulamadı, lütfen tekrar deneyin"
        "token_creation_failed" -> "Kayıt oluşturuldu ama giriş yapılamadı, lütfen giriş yapmayı deneyin"
        "rate_limited" -> "Çok fazla deneme yapıldı, lütfen biraz sonra tekrar deneyin"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        else -> "Kayıt oluşturulamadı, lütfen tekrar deneyin"
    }
}
