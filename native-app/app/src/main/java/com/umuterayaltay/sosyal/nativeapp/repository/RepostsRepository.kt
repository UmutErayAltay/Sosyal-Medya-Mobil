package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.RepostRequest
import com.umuterayaltay.sosyal.nativeapp.network.RepostsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class RepostResult {
    data class Success(val postId: String?) : RepostResult()
    data class Error(val code: String?) : RepostResult()
}

/**
 * Yeniden paylaşma (repost) — MutesRepository/BookmarksRepository ile AYNI
 * desen: Room cache YOK (fire-and-forget aksiyon, orijinal post üzerinde
 * kalıcı bir "repostedByMe" durumu TUTULMUYOR — backend her repost'u YENİ
 * bir post satırı olarak oluşturuyor, toggle değil).
 */
class RepostsRepository(
    private val repostsApi: RepostsApi,
) {
    suspend fun repost(postId: String, content: String = ""): RepostResult = withContext(Dispatchers.IO) {
        try {
            val response = repostsApi.repostPost(postId, RepostRequest(content))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                RepostResult.Success(body.postId)
            } else {
                RepostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            RepostResult.Error("network_error")
        } catch (e: Exception) {
            RepostResult.Error("unknown_error")
        }
    }
}
