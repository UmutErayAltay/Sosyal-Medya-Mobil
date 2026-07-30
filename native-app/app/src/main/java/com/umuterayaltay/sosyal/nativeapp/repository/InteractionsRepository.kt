package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddCommentRequest
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.network.InteractionsApi
import com.umuterayaltay.sosyal.nativeapp.network.LikeRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ToggleLikeResult {
    data class Success(val liked: Boolean, val reaction: String?, val count: Int) : ToggleLikeResult()
    data class Error(val code: String?) : ToggleLikeResult()
}

data class PostDetail(val post: Post, val comments: List<CommentDto>)

sealed class PostDetailResult {
    data class Success(val data: PostDetail) : PostDetailResult()
    data class Error(val code: String?) : PostDetailResult()
}

sealed class AddCommentResult {
    data class Success(val comment: CommentDto) : AddCommentResult()
    data class Error(val code: String?) : AddCommentResult()
}

/**
 * Post beğeni + yorum (post detay) için repository — DiscoverRepository/
 * ProfileRepository ile AYNI hata yönetimi deseni (IOException -> network_error,
 * diğer Exception -> unknown_error, backend {error} varsa onu kullan). Room
 * cache YOK — DiscoverRepository/ReelsRepository'deki AYNI bilinçli kapsam
 * kararı (canlı etkileşim verisi, offline-first ihtiyacı yok).
 */
class InteractionsRepository(
    private val interactionsApi: InteractionsApi,
) {

    suspend fun toggleLike(postId: String, reaction: String? = null): ToggleLikeResult =
        withContext(Dispatchers.IO) {
            try {
                val response = interactionsApi.toggleLike(postId, LikeRequest(reaction))
                val body = response.body()
                if (response.isSuccessful && body != null && body.error == null) {
                    ToggleLikeResult.Success(body.liked, body.reaction, body.count)
                } else {
                    ToggleLikeResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ToggleLikeResult.Error("network_error")
            } catch (e: Exception) {
                ToggleLikeResult.Error("unknown_error")
            }
        }

    suspend fun getPostDetail(postId: String): PostDetailResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.getPostDetail(postId)
            val body = response.body()
            val post = body?.post
            if (response.isSuccessful && body != null && body.error == null && post != null) {
                PostDetailResult.Success(PostDetail(post.toDomain(), body.comments ?: emptyList()))
            } else {
                PostDetailResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            PostDetailResult.Error("network_error")
        } catch (e: Exception) {
            PostDetailResult.Error("unknown_error")
        }
    }

    suspend fun addComment(postId: String, content: String, parentCommentId: String? = null): AddCommentResult =
        withContext(Dispatchers.IO) {
            try {
                val response = interactionsApi.addComment(
                    postId,
                    AddCommentRequest(content = content, parentCommentId = parentCommentId),
                )
                val body = response.body()
                val comment = body?.comment
                if (response.isSuccessful && body != null && body.error == null && comment != null) {
                    AddCommentResult.Success(comment)
                } else {
                    AddCommentResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                AddCommentResult.Error("network_error")
            } catch (e: Exception) {
                AddCommentResult.Error("unknown_error")
            }
        }
}
