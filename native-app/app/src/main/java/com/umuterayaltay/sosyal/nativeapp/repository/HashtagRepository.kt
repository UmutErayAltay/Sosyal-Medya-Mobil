package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.HashtagApi
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class HashtagPostsResult {
    data class Success(val posts: List<Post>, val isFollowing: Boolean) : HashtagPostsResult()
    data class Error(val code: String?) : HashtagPostsResult()
}

sealed class ToggleHashtagFollowResult {
    data class Success(val following: Boolean) : ToggleHashtagFollowResult()
    data class Error(val code: String?) : ToggleHashtagFollowResult()
}

sealed class TrendingResult {
    data class Success(val tags: List<HashtagSearchDto>) : TrendingResult()
    data class Error(val code: String?) : TrendingResult()
}

/**
 * Hashtag detay sayfası + gündem için repository — DiscoverRepository'nin
 * AKSİNE Room cache YOK (NotificationsRepository/MessagingRepository'deki AYNI
 * kapsam kararı: canlı liste, offline-first bu MVP turunda gereksiz).
 */
class HashtagRepository(
    private val hashtagApi: HashtagApi,
) {

    suspend fun getHashtagPosts(tag: String): HashtagPostsResult = withContext(Dispatchers.IO) {
        try {
            val response = hashtagApi.getHashtagPosts(tag)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                HashtagPostsResult.Success(
                    posts = (body.posts ?: emptyList()).map { it.toDomain() },
                    isFollowing = body.isFollowing,
                )
            } else {
                HashtagPostsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            HashtagPostsResult.Error("network_error")
        } catch (e: Exception) {
            HashtagPostsResult.Error("unknown_error")
        }
    }

    suspend fun toggleFollow(tag: String): ToggleHashtagFollowResult = withContext(Dispatchers.IO) {
        try {
            val response = hashtagApi.toggleHashtagFollow(tag)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ToggleHashtagFollowResult.Success(body.following)
            } else {
                ToggleHashtagFollowResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleHashtagFollowResult.Error("network_error")
        } catch (e: Exception) {
            ToggleHashtagFollowResult.Error("unknown_error")
        }
    }

    suspend fun getTrending(): TrendingResult = withContext(Dispatchers.IO) {
        try {
            val response = hashtagApi.getTrending()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                TrendingResult.Success(body.tags ?: emptyList())
            } else {
                TrendingResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            TrendingResult.Error("network_error")
        } catch (e: Exception) {
            TrendingResult.Error("unknown_error")
        }
    }
}
