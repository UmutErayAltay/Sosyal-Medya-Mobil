package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.ReelsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ReelsPageResult {
    data class Success(val posts: List<Post>, val hasMore: Boolean, val page: Int) : ReelsPageResult()
    data class Error(val code: String?) : ReelsPageResult()
}

/**
 * Dikey video akışı (Reels) için repository — DiscoverRepository.getDiscoverPage()
 * ile AYNI desen/hata yönetimi. Room cache YOK: DiscoverRepository'deki AYNI
 * bilinçli kapsam kararı — video akışının offline-first olması bu iterasyonda
 * gerekmiyor (bkz. app/api_v1.py api_reels()).
 */
class ReelsRepository(
    private val reelsApi: ReelsApi,
) {

    suspend fun getReelsPage(page: Int): ReelsPageResult = withContext(Dispatchers.IO) {
        try {
            val response = reelsApi.getReels(page)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ReelsPageResult.Success(
                    posts = (body.posts ?: emptyList()).map { it.toDomain() },
                    hasMore = body.hasMore,
                    page = body.page,
                )
            } else {
                ReelsPageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ReelsPageResult.Error("network_error")
        } catch (e: Exception) {
            ReelsPageResult.Error("unknown_error")
        }
    }
}
