package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.MutesApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ToggleUserMuteResult {
    data class Success(val muted: Boolean) : ToggleUserMuteResult()
    data class Error(val code: String?) : ToggleUserMuteResult()
}

sealed class ToggleMutePostResult {
    data class Success(val muted: Boolean) : ToggleMutePostResult()
    data class Error(val code: String?) : ToggleMutePostResult()
}

/**
 * Sessize alma (Faz 5, Dalga 2A) için repository — HashtagRepository ile AYNI
 * gerekçeyle Room cache YOK (canlı toggle, offline-first ihtiyacı yok).
 */
class MutesRepository(
    private val mutesApi: MutesApi,
) {

    suspend fun toggleUserMute(userId: String): ToggleUserMuteResult = withContext(Dispatchers.IO) {
        try {
            val response = mutesApi.toggleUserMute(userId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                ToggleUserMuteResult.Success(body.muted)
            } else {
                ToggleUserMuteResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleUserMuteResult.Error("network_error")
        } catch (e: Exception) {
            ToggleUserMuteResult.Error("unknown_error")
        }
    }

    suspend fun toggleMutePost(postId: String): ToggleMutePostResult = withContext(Dispatchers.IO) {
        try {
            val response = mutesApi.toggleMutePost(postId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                ToggleMutePostResult.Success(body.muted)
            } else {
                ToggleMutePostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleMutePostResult.Error("network_error")
        } catch (e: Exception) {
            ToggleMutePostResult.Error("unknown_error")
        }
    }
}
