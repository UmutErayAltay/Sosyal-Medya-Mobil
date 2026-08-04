package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.PushApi
import com.umuterayaltay.sosyal.nativeapp.network.RegisterTokenRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** register/unregister ikisi de AYNI {"ok"}/{"error"} şeklini döner —
 * MutesRepository'nin ToggleXxxResult desenindeki gibi ayrı bir "data" alanı
 * TAŞIMADIĞI için (sadece başarı/başarısızlık) tek bir sonuç tipi yeterli. */
sealed class PushTokenResult {
    data object Success : PushTokenResult()
    data class Error(val code: String?) : PushTokenResult()
}

/**
 * FCM push token kaydı (Faz 5, Dalga 4A) için repository — MutesRepository/
 * AuthRepository.forgotPassword() ile AYNI hata deseni (IOException->
 * network_error, generic Exception->unknown_error). Room cache YOK (bkz.
 * MutesRepository gerekçesi — token kaydı canlı bir toggle, offline-first
 * ihtiyacı yok).
 */
class PushRepository(
    private val pushApi: PushApi,
) {

    suspend fun registerToken(token: String): PushTokenResult = withContext(Dispatchers.IO) {
        try {
            val response = pushApi.register(RegisterTokenRequest(token))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                PushTokenResult.Success
            } else {
                PushTokenResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            PushTokenResult.Error("network_error")
        } catch (e: Exception) {
            PushTokenResult.Error("unknown_error")
        }
    }

    suspend fun unregisterToken(token: String): PushTokenResult = withContext(Dispatchers.IO) {
        try {
            val response = pushApi.unregister(RegisterTokenRequest(token))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                PushTokenResult.Success
            } else {
                PushTokenResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            PushTokenResult.Error("network_error")
        } catch (e: Exception) {
            PushTokenResult.Error("unknown_error")
        }
    }
}
