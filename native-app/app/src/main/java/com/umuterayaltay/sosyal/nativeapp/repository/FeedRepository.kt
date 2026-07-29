package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.local.PostDao
import com.umuterayaltay.sosyal.nativeapp.network.FeedApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class FeedRefreshResult {
    data object Success : FeedRefreshResult()
    data class Error(val code: String?) : FeedRefreshResult()
}

private const val FIRST_PAGE_LIMIT = 20

/**
 * "Cache önce göster, sonra tazele" deseni: [observePosts] Room'daki mevcut
 * cache'i hemen emit eder (Flow), [refresh] ağdan ilk sayfayı çekip Room'u
 * günceller — Room güncellenince observePosts'a bağlı Compose ekranı otomatik
 * yeniden çizilir (tek yönlü veri akışı, ekstra callback gerekmiyor).
 */
class FeedRepository(
    private val feedApi: FeedApi,
    private val postDao: PostDao,
) {

    fun observePosts(): Flow<List<Post>> =
        postDao.getAll().map { entities -> entities.map { it.toDomain() } }

    suspend fun refresh(): FeedRefreshResult = withContext(Dispatchers.IO) {
        try {
            val response = feedApi.getFeed(cursor = 0, limit = FIRST_PAGE_LIMIT)
            val body = response.body()
            if (response.isSuccessful && body?.posts != null) {
                val now = System.currentTimeMillis()
                val entities = body.posts.map { it.toEntity(cachedAt = now) }
                postDao.clearAll()
                postDao.insertAll(entities)
                FeedRefreshResult.Success
            } else {
                val code = body?.error ?: RetrofitClient.parseErrorCode(response)
                FeedRefreshResult.Error(code)
            }
        } catch (e: IOException) {
            FeedRefreshResult.Error("network_error")
        } catch (e: Exception) {
            FeedRefreshResult.Error("unknown_error")
        }
    }
}
