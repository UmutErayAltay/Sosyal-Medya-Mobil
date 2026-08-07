package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddCommentRequest
import com.umuterayaltay.sosyal.nativeapp.network.CommentDto
import com.umuterayaltay.sosyal.nativeapp.network.EditPostRequest
import com.umuterayaltay.sosyal.nativeapp.network.InteractionsApi
import com.umuterayaltay.sosyal.nativeapp.network.LikeRequest
import com.umuterayaltay.sosyal.nativeapp.network.ReactCommentRequest
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

// Post yönetimi (düzenle/sil/arşivle/sabitle) — app/api_v1/posts.py'nin
// birebir mirror'ı, ownership ihlalinde backend HER ZAMAN "not_found" (404)
// döner (enumeration koruması), ayrı bir "forbidden" varyantı YOK.
sealed class EditPostResult {
    data class Success(val content: String, val visibility: String?, val editedAt: String?) : EditPostResult()
    data class Error(val code: String?) : EditPostResult()
}

sealed class DeletePostResult {
    object Success : DeletePostResult()
    data class Error(val code: String?) : DeletePostResult()
}

sealed class ToggleArchiveResult {
    data class Success(val isArchived: Boolean) : ToggleArchiveResult()
    data class Error(val code: String?) : ToggleArchiveResult()
}

sealed class TogglePinResult {
    data class Success(val pinned: Boolean) : TogglePinResult()
    data class Error(val code: String?) : TogglePinResult()
}

// Yorum mutasyonları (sil/beğen/tepki) — app/api_v1/interactions.py'nin
// birebir mirror'ı.
sealed class DeleteCommentResult {
    object Success : DeleteCommentResult()
    data class Error(val code: String?) : DeleteCommentResult()
}

sealed class ToggleCommentLikeResult {
    data class Success(val liked: Boolean, val count: Int) : ToggleCommentLikeResult()
    data class Error(val code: String?) : ToggleCommentLikeResult()
}

sealed class ReactCommentResult {
    /** [reaction] null olabilir — aynı emoji tekrar gönderilince tepki SİLİNMİŞ demektir. */
    data class Success(val reaction: String?) : ReactCommentResult()
    data class Error(val code: String?) : ReactCommentResult()
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

    /** stickerId/gifUrl (Faz 5 Dalga 3B) opsiyonel — content boşsa bile biri
     * doluysa backend kabul eder (api_add_comment ile AYNI kural: üçü de boşsa
     * "empty" döner), burada ayrıca bir ön-kontrol YAPILMIYOR. */
    suspend fun addComment(
        postId: String,
        content: String,
        parentCommentId: String? = null,
        stickerId: String? = null,
        gifUrl: String? = null,
    ): AddCommentResult =
        withContext(Dispatchers.IO) {
            try {
                val response = interactionsApi.addComment(
                    postId,
                    AddCommentRequest(
                        content = content,
                        parentCommentId = parentCommentId,
                        stickerId = stickerId,
                        gifUrl = gifUrl,
                    ),
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
     * görsel deseninin AKSİNE burada sabit bir dosya adı YETERSİZ. `gifUrl`
     * (Faz 5 Dalga 3B) SADECE görsel/video YOKSA backend'de kullanılır
     * (api_create_post()'daki AYNI mutually-exclusive kural) — burada ayrıca
     * bir ön-kontrol YAPILMIYOR, tek doğruluk kaynağı backend. `pollOptions`
     * (Faz 5 Dalga 4C) — web'in routes/posts.py create_post()'undaki AYNI
     * sözleşme: en fazla 4 seçenek, `poll_option_1..4` form alanlarına
     * sırayla yazılır, listedeki eleman sayısı 4'ten azsa kalan alanlar null
     * geçilir (Retrofit o alanları isteğe hiç eklemez). Görsel/video ile
     * mutually-exclusive DEĞİL — GIF'in aksine birlikte gönderilebilir.
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
        gifUrl: String? = null,
        pollOptions: List<String> = emptyList(),
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
            val gifUrlBody: RequestBody? = gifUrl?.toRequestBody("text/plain".toMediaTypeOrNull())
            // pollOptions[0..3] -> poll_option_1..4, index sınırların dışındaysa null
            // (gifUrlBody'nin null-atlama deseniyle AYNI).
            val pollOption1Body: RequestBody? = pollOptions.getOrNull(0)
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val pollOption2Body: RequestBody? = pollOptions.getOrNull(1)
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val pollOption3Body: RequestBody? = pollOptions.getOrNull(2)
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val pollOption4Body: RequestBody? = pollOptions.getOrNull(3)
                ?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = interactionsApi.createPost(
                contentBody, visibilityBody, imagePart, videoPart, isReelBody, gifUrlBody,
                pollOption1Body, pollOption2Body, pollOption3Body, pollOption4Body,
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

    suspend fun editPost(postId: String, content: String, visibility: String? = null): EditPostResult =
        withContext(Dispatchers.IO) {
            try {
                val response = interactionsApi.editPost(postId, EditPostRequest(content, visibility))
                val body = response.body()
                if (response.isSuccessful && body != null && body.ok) {
                    EditPostResult.Success(body.content ?: content, body.visibility, body.editedAt)
                } else {
                    EditPostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                EditPostResult.Error("network_error")
            } catch (e: Exception) {
                EditPostResult.Error("unknown_error")
            }
        }

    suspend fun deletePost(postId: String): DeletePostResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.deletePost(postId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.ok) {
                DeletePostResult.Success
            } else {
                DeletePostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeletePostResult.Error("network_error")
        } catch (e: Exception) {
            DeletePostResult.Error("unknown_error")
        }
    }

    suspend fun toggleArchive(postId: String): ToggleArchiveResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.toggleArchive(postId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.ok) {
                ToggleArchiveResult.Success(body.isArchived)
            } else {
                ToggleArchiveResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleArchiveResult.Error("network_error")
        } catch (e: Exception) {
            ToggleArchiveResult.Error("unknown_error")
        }
    }

    suspend fun togglePin(postId: String): TogglePinResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.togglePin(postId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.ok) {
                TogglePinResult.Success(body.pinned)
            } else {
                TogglePinResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            TogglePinResult.Error("network_error")
        } catch (e: Exception) {
            TogglePinResult.Error("unknown_error")
        }
    }

    suspend fun deleteComment(commentId: String): DeleteCommentResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.deleteComment(commentId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.ok) {
                DeleteCommentResult.Success
            } else {
                DeleteCommentResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeleteCommentResult.Error("network_error")
        } catch (e: Exception) {
            DeleteCommentResult.Error("unknown_error")
        }
    }

    suspend fun toggleCommentLike(commentId: String): ToggleCommentLikeResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.toggleCommentLike(commentId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ToggleCommentLikeResult.Success(body.liked, body.count)
            } else {
                ToggleCommentLikeResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleCommentLikeResult.Error("network_error")
        } catch (e: Exception) {
            ToggleCommentLikeResult.Error("unknown_error")
        }
    }

    suspend fun reactComment(commentId: String, reaction: String): ReactCommentResult = withContext(Dispatchers.IO) {
        try {
            val response = interactionsApi.reactComment(commentId, ReactCommentRequest(reaction))
            val body = response.body()
            if (response.isSuccessful && body != null && body.ok) {
                ReactCommentResult.Success(body.reaction)
            } else {
                ReactCommentResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ReactCommentResult.Error("network_error")
        } catch (e: Exception) {
            ReactCommentResult.Error("unknown_error")
        }
    }
}
