package com.umuterayaltay.sosyal.nativeapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** NeedsCode durumunun HANGİ akıştan geldiğini ayırt eder — email/şifre akışı
 * AYNI email+password'ü code eklenmiş halde tekrar gönderir, Google akışı AYNI
 * idToken'ı code eklenmiş halde tekrar gönderir (bkz. AuthRepository.login/
 * loginWithGoogle docstring'i). */
sealed class PendingCredential {
    data class EmailPassword(val email: String, val password: String) : PendingCredential()
    data class Google(val idToken: String) : PendingCredential()
}

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()

    /** Backend 403 mfa_required döndü — kullanıcı Authenticator uygulamasındaki
     * 6 haneli kodu girmeli (bkz. submitLoginCode/submitGoogleLoginCode). */
    data class NeedsCode(val pending: PendingCredential) : LoginUiState()
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
        val trimmedEmail = email.trim()
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (
                val result = authRepository.login(trimmedEmail, password, deviceName = android.os.Build.MODEL)
            ) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success
                is AuthResult.Error -> {
                    if (result.code == "mfa_required") {
                        _uiState.value = LoginUiState.NeedsCode(
                            PendingCredential.EmailPassword(trimmedEmail, password)
                        )
                    } else {
                        _uiState.value = LoginUiState.Error(mapErrorMessage(result.code))
                    }
                }
            }
        }
    }

    /** 2FA kodu ile login()'i TEKRAR dener — invalid_code Error olarak döner
     * (NeedsCode'a GERİ dönmez, spesifikasyon gereği kullanıcı normal login
     * formuna düşer ve tekrar dener — bkz. görev tanımı). */
    fun submitLoginCode(email: String, password: String, code: String) {
        if (code.isBlank()) {
            _uiState.value = LoginUiState.Error("Doğrulama kodu gerekli")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (
                val result = authRepository.login(
                    email,
                    password,
                    deviceName = android.os.Build.MODEL,
                    code = code.trim(),
                )
            ) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success
                is AuthResult.Error -> _uiState.value = LoginUiState.Error(mapErrorMessage(result.code))
            }
        }
    }

    /** [idToken] Credential Manager'dan (bkz. GoogleSignInHelper) alınan Google
     * ID token'ı — login()'deki AYNI mfa_required → NeedsCode geçişi burada da
     * geçerli (Google varyantı, PendingCredential.Google). */
    fun loginWithGoogle(idToken: String, nonce: String? = null) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (
                val result = authRepository.loginWithGoogle(
                    idToken,
                    nonce = nonce,
                    code = null,
                    deviceName = android.os.Build.MODEL,
                )
            ) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success
                is AuthResult.Error -> {
                    if (result.code == "mfa_required") {
                        _uiState.value = LoginUiState.NeedsCode(PendingCredential.Google(idToken))
                    } else {
                        _uiState.value = LoginUiState.Error(mapErrorMessage(result.code))
                    }
                }
            }
        }
    }

    /** Google akışı için 2FA kodu — submitLoginCode ile AYNI davranış
     * (invalid_code Error'a düşer, NeedsCode'a GERİ dönmez). */
    fun submitGoogleLoginCode(idToken: String, code: String) {
        if (code.isBlank()) {
            _uiState.value = LoginUiState.Error("Doğrulama kodu gerekli")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            when (
                val result = authRepository.loginWithGoogle(
                    idToken,
                    nonce = null,
                    code = code.trim(),
                    deviceName = android.os.Build.MODEL,
                )
            ) {
                is AuthResult.Success -> _uiState.value = LoginUiState.Success
                is AuthResult.Error -> _uiState.value = LoginUiState.Error(mapErrorMessage(result.code))
            }
        }
    }

    /** Credential Manager çağrısının kendisi (GetCredentialException) — sunucuya
     * hiç istek gitmeden — başarısız olduğunda LoginScreen'in çağırdığı fonksiyon.
     * Kullanıcı hesap seçmeden iptal ederse (NoCredentialException) bu ÇAĞRILMAZ,
     * LoginScreen sessizce geri döner (hata SPAM'lemesin diye). */
    fun onGoogleSignInFailed() {
        _uiState.value = LoginUiState.Error("Google ile giriş başarısız")
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
        "invalid_code" -> "Geçersiz doğrulama kodu"
        "missing_credentials" -> "E-posta ve şifre gerekli"
        "banned" -> "Bu hesap askıya alınmış"
        "rate_limited" -> "Çok fazla deneme yapıldı, lütfen biraz sonra tekrar deneyin"
        "token_creation_failed" -> "Giriş yapılamadı, lütfen tekrar deneyin"
        "network_error" -> "Bağlantı hatası — internet bağlantınızı kontrol edin"
        "invalid_token" -> "Google ile giriş başarısız"
        "missing_token" -> "Google ile giriş başarısız"
        // Normalde bu durumda NeedsCode state'i kullanılır (bkz. login()/
        // loginWithGoogle()), bu satır sadece beklenmedik bir yol için fallback.
        "mfa_required" -> "Doğrulama kodu gerekli"
        else -> "Giriş yapılamadı, lütfen tekrar deneyin"
    }
}
