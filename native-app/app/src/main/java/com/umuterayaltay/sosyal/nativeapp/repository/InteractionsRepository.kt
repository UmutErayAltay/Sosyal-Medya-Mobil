package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddCommentRequest
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.network.InteractionsApi
import com.umuterayaltay.sosyal.nativeapp.network.LikeRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
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

sealed class CreatePostResult {
    data class Success(val post: Post) : CreatePostResult()
    data class Error(val code: String?) : CreatePostResult()
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

    /**
     * Yeni post oluşturur — app/api_v1.py api_create_post() ile AYNI kısıt:
     * content VE imageBytes/videoBytes ikisi de boşsa backend zaten "empty"
     * döner, burada ayrıca bir ön-kontrol YAPILMIYOR (tek doğruluk kaynağı
     * backend olsun diye — CreatePostViewModel.submit() UI tarafında aynı
     * kontrolü zaten yapıyor). `videoFileName` GERÇEK içerik tipiyle eşleşen
     * bir uzantı taşımalı (ör. "upload.mp4") — backend hem uzantı HEM
     * sniff edilmiş MIME'ı doğruluyor (storage_helper.py upload_video),
     * görsel deseninin AKSİNE burada sabit bir dosya adı YETERSİZ.
     */
    suspend fun createPost(
        content: String,
        visibility: String,
        imageBytes: ByteArray?,
        imageMimeType: String?,
        imageFileName: String?,
        videoBytes: ByteArray? = null,
        videoMimeType: String? = null,
        videoFileName: String? = null,
        isReel: Boolean = false,
    ): CreatePostResult = withContext(Dispatchers.IO) {
        try {
            val contentBody: RequestBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
            val visibilityBody: RequestBody = visibility.toRequestBody("text/plain".toMediaTypeOrNull())
            val isReelBody: RequestBody = (if (isReel) "true" else "false")
                .toRequestBody("text/plain".toMediaTypeOrNull())
            val imagePart: MultipartBody.Part? = imageBytes?.let { bytes ->
                val imageBody = bytes.toRequestBody(imageMimeType?.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", imageFileName ?: "image.jpg", imageBody)
            }
            val videoPart: MultipartBody.Part? = videoBytes?.let { bytes ->
                val videoBody = bytes.toRequestBody(videoMimeType?.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("video", videoFileName ?: "upload.mp4", videoBody)
            }

            val response = interactionsApi.createPost(
                contentBody, visibilityBody, imagePart, videoPart, isReelBody,
            )
            val body = response.body()
            val post = body?.post
            if (response.isSuccessful && body != null && body.error == null && post != null) {
                CreatePostResult.Success(post.toDomain())
            } else {
                CreatePostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CreatePostResult.Error("network_error")
        } catch (e: Exception) {
            CreatePostResult.Error("unknown_error")
        }
    }
}
