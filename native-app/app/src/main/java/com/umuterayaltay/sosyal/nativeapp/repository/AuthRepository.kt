package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.GoogleLoginRequest
import com.umuterayaltay.sosyal.nativeapp.network.LoginRequest
import com.umuterayaltay.sosyal.nativeapp.network.LoginResponse
import com.umuterayaltay.sosyal.nativeapp.network.RegisterRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/** Login sonucu — backend'in döndüğü hata kodlarını (bkz. app/api_v1.py) ayırt eder. */
sealed class AuthResult {
    data class Success(val user: UserDto) : AuthResult()
    data class Error(val code: String?, val message: String?) : AuthResult()
}

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {

    fun isLoggedIn(): Boolean = !tokenStore.getToken().isNullOrBlank()

    /** [code] opsiyonel — 2FA aktif hesapta ilk denemede backend 403 mfa_required
     * döner (bkz. AuthViewModel.NeedsCode); çağıran taraf AYNI email+password'ü
     * code eklenmiş halde tekrar gönderir. */
    suspend fun login(email: String, password: String, deviceName: String?, code: String? = null): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(
                    LoginRequest(
                        email = email,
                        password = password,
                        deviceName = deviceName,
                        code = code,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    suspend fun register(email: String, password: String, username: String, deviceName: String?): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        username = username,
                        deviceName = deviceName,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    /** [idToken] Android Credential Manager'ın ürettiği Google ID token'ı (bkz.
     * GoogleSignInHelper.kt). [code] login()'deki AYNI 2FA-retry deseni —
     * mfa_required dönerse çağıran taraf AYNI idToken'ı code eklenmiş halde
     * tekrar gönderir. [nonce] bu iterasyonda BİLİNÇLİ olarak kullanılmıyor
     * (kapsam dışı: replay-koruması). */
    suspend fun loginWithGoogle(idToken: String, nonce: String?, code: String?, deviceName: String?): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.googleLogin(
                    GoogleLoginRequest(
                        idToken = idToken,
                        nonce = nonce,
                        code = code,
                        deviceName = deviceName,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    /** login()/register()/googleLogin() ÜÇÜ de AYNI {token,user}/{error,message}
     * şeklini döner (LoginResponse reuse edilir) — parse mantığı TEK yerde. */
    private fun parseAuthResponse(response: Response<LoginResponse>): AuthResult {
        val body = response.body()
        return if (response.isSuccessful && body?.token != null && body.user != null) {
            tokenStore.saveToken(body.token)
            AuthResult.Success(body.user)
        } else {
            val code = body?.error ?: RetrofitClient.parseErrorCode(response)
            AuthResult.Error(code, body?.message)
        }
    }

    /** ProfileViewModel'in username=null (kendi profilim) durumunda kullaniciyi
     * cozmesi icin - AuthApi.me()'yi sarar, hata/401 durumunda null doner
     * (cagiran taraf null'i "oturum gecersiz" olarak yorumlar). */
    suspend fun getCurrentUser(): UserDto? = withContext(Dispatchers.IO) {
        try {
            val response = authApi.me()
            if (response.isSuccessful) response.body()?.user else null
        } catch (e: Exception) {
            null
        }
    }

    /** Sadece bu cihazın token'ını iptal eder — API çağrısı başarısız olsa bile
     * yerel token her zaman temizlenir (kullanıcı cihazda "çıkış yapmış" görünmeli). */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                authApi.logout()
            } catch (e: Exception) {
                // Ağ hatası olsa bile yerelde çıkış yapılır — sunucu tarafında token
                // zaten süresi dolana/başka bir yolla iptal edilene kadar geçerli kalabilir,
                // ama bu MVP'de kabul edilebilir (spesifikasyon: proaktif doğrulama yok).
            } finally {
                tokenStore.clearToken()
            }
        }
    }
}
