package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.LoginRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun login(email: String, password: String, deviceName: String?): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(
                    LoginRequest(
                        email = email,
                        password = password,
                        deviceName = deviceName,
                    )
                )
                val body = response.body()
                if (response.isSuccessful && body?.token != null && body.user != null) {
                    tokenStore.saveToken(body.token)
                    AuthResult.Success(body.user)
                } else {
                    val code = body?.error ?: RetrofitClient.parseErrorCode(response)
                    AuthResult.Error(code, body?.message)
                }
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
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
