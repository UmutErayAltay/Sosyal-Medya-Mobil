package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.CreateStoryResponse
import com.umuterayaltay.sosyal.nativeapp.network.HighlightDto
import com.umuterayaltay.sosyal.nativeapp.network.HighlightItemDto
import com.umuterayaltay.sosyal.nativeapp.network.ReactToStoryRequest
import com.umuterayaltay.sosyal.nativeapp.network.ReplyToStoryRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SaveHighlightRequest
import com.umuterayaltay.sosyal.nativeapp.network.StoriesApi
import com.umuterayaltay.sosyal.nativeapp.network.StoryBarItemDto
import com.umuterayaltay.sosyal.nativeapp.network.StoryDto
import com.umuterayaltay.sosyal.nativeapp.network.UpdateHighlightRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// ---- Domain modelleri — Post.kt'deki (PostDto -> Post) AYNI desen: UI
// katmanı Gson DTO'larına DEĞİL bu sade modellere bağımlı olur. ----

/** Feed'in üstündeki hikaye çubuğu satırı — active_stories_bar()'ın grup özeti. */
data class StoryBarItem(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val allSeen: Boolean,
)

/** Tek bir hikaye — attach_story_poll()'un eklediği poll REUSE edilen Poll
 * (repository/Post.kt) tipiyle taşınır, ayrı bir StoryPoll İCAT EDİLMEDİ. */
data class Story(
    val id: String,
    val userId: String,
    val imageUrl: String?,
    val videoUrl: String?,
    val caption: String?,
    val createdAt: String?,
    val visibility: String?,
    val backgroundColor: String?,
    val captionPositionX: Double,
    val captionPositionY: Double,
    val poll: Poll?,
    // 2026-08-09 — GIF/sticker overlay (bkz. ApiModels.kt StoryDto yorumu).
    val overlayImageUrl: String?,
    val overlayImagePositionX: Double,
    val overlayImagePositionY: Double,
    val overlayImageScale: Double,
)

data class UserStories(
    val username: String,
    val avatarUrl: String?,
    val isMine: Boolean,
    val stories: List<Story>,
)

data class Highlight(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val itemCount: Int,
)

data class HighlightItem(
    val id: String,
    val imageUrl: String?,
    val videoUrl: String?,
    val caption: String?,
    val originalCreatedAt: String?,
)

data class HighlightView(
    val title: String,
    val isMine: Boolean,
    val items: List<HighlightItem>,
)

private fun StoryBarItemDto.toDomain() = StoryBarItem(
    userId = userId,
    username = username ?: "Bilinmeyen",
    avatarUrl = avatarUrl,
    allSeen = allSeen,
)

private fun StoryDto.toDomain() = Story(
    id = id,
    userId = userId,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    caption = caption,
    createdAt = createdAt,
    visibility = visibility,
    backgroundColor = backgroundColor,
    captionPositionX = captionPositionX ?: 0.5,
    captionPositionY = captionPositionY ?: 0.75,
    overlayImageUrl = overlayImageUrl,
    overlayImagePositionX = overlayImagePositionX ?: 0.5,
    overlayImagePositionY = overlayImagePositionY ?: 0.5,
    overlayImageScale = overlayImageScale ?: 1.0,
    poll = poll?.let { p ->
        Poll(
            id = p.id,
            options = (p.options ?: emptyList()).map {
                PollOption(id = it.id, text = it.text, votes = it.votes, pct = it.pct)
            },
            totalVotes = p.totalVotes,
            myVote = p.myVote,
            positionX = p.positionX,
            positionY = p.positionY,
            scale = p.scale,
        )
    },
)

private fun HighlightDto.toDomain() = Highlight(
    id = id,
    title = title ?: "",
    coverUrl = coverUrl,
    itemCount = itemCount,
)

private fun HighlightItemDto.toDomain() = HighlightItem(
    id = id,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    caption = caption,
    originalCreatedAt = originalCreatedAt,
)

// ---- Sonuç tipleri — işlem başına sealed class, diğer repository'lerdeki
// (PollsRepository/InteractionsRepository) AYNI standart hata-eşleme deseni:
// IOException -> network_error, diğer Exception -> unknown_error, backend
// {error} varsa onu kullan. ----

sealed class StoryBarResult {
    data class Success(val items: List<StoryBarItem>) : StoryBarResult()
    data class Error(val code: String?) : StoryBarResult()
}

sealed class UserStoriesResult {
    data class Success(val data: UserStories) : UserStoriesResult()
    data class Error(val code: String?) : UserStoriesResult()
}

sealed class CreateStoryResult {
    data class Success(val storyId: String?, val pollError: Boolean) : CreateStoryResult()
    data class Error(val code: String?) : CreateStoryResult()
}

sealed class ReactToStoryResult {
    data class Success(val conversationId: String?) : ReactToStoryResult()
    data class Error(val code: String?) : ReactToStoryResult()
}

sealed class ReplyToStoryResult {
    data class Success(val conversationId: String?) : ReplyToStoryResult()
    data class Error(val code: String?) : ReplyToStoryResult()
}

sealed class DeleteStoryResult {
    data object Success : DeleteStoryResult()
    data class Error(val code: String?) : DeleteStoryResult()
}

sealed class SaveHighlightResult {
    data class Success(val highlightId: String?) : SaveHighlightResult()
    data class Error(val code: String?) : SaveHighlightResult()
}

sealed class HighlightsResult {
    data class Success(val highlights: List<Highlight>) : HighlightsResult()
    data class Error(val code: String?) : HighlightsResult()
}

sealed class HighlightViewResult {
    data class Success(val data: HighlightView) : HighlightViewResult()
    data class Error(val code: String?) : HighlightViewResult()
}

sealed class DeleteHighlightResult {
    data object Success : DeleteHighlightResult()
    data class Error(val code: String?) : DeleteHighlightResult()
}

sealed class UpdateHighlightResult {
    data object Success : UpdateHighlightResult()
    data class Error(val code: String?) : UpdateHighlightResult()
}

/**
 * Hikayeler (Stories) — app/api_v1/stories.py'nin native tüketicisi (Faz 5,
 * Dalga 2C). Room cache YOK — DiscoverRepository/ReelsRepository/PollsRepository
 * ile AYNI bilinçli kapsam kararı: hikayeler zaten 24 saatte kaybolan geçici
 * veri, offline-first anlamsız.
 */
class StoriesRepository(
    private val storiesApi: StoriesApi,
) {

    suspend fun getBar(): StoryBarResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.getStoriesBar()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                StoryBarResult.Success((body.stories ?: emptyList()).map { it.toDomain() })
            } else {
                StoryBarResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            StoryBarResult.Error("network_error")
        } catch (e: Exception) {
            StoryBarResult.Error("unknown_error")
        }
    }

    /** SADECE story viewer'ın İLK açılışında (init) bir kez çağrılmalı — GET
     * içinde yan etki var (kendi hikayen DEĞİLSE story_views upsert edilir,
     * bkz. backend api_user_stories() docstring'i). Tekrar tekrar çağırmak
     * double-count YAPMAZ (upsert) ama gereksiz network isteğidir. */
    suspend fun getUserStories(userId: String): UserStoriesResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.getUserStories(userId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                UserStoriesResult.Success(
                    UserStories(
                        username = body.username ?: "Bilinmeyen",
                        avatarUrl = body.avatarUrl,
                        isMine = body.isMine,
                        stories = (body.stories ?: emptyList()).map { it.toDomain() },
                    )
                )
            } else {
                UserStoriesResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            UserStoriesResult.Error("network_error")
        } catch (e: Exception) {
            UserStoriesResult.Error("unknown_error")
        }
    }

    /**
     * Yeni hikaye — app/api_v1/stories.py api_create_story() ile AYNI kısıt:
     * caption/image/video/poll hepsi boşsa backend "empty_story" döner, burada
     * ayrı bir ön-kontrol YAPILMIYOR (tek doğruluk kaynağı backend, CreatePost
     * deseniyle TUTARLI). `videoFileName` GERÇEK içerik tipiyle eşleşen bir
     * uzantı taşımalı (InteractionsRepository.createPost ile AYNI gerekçe).
     */
    suspend fun createStory(
        caption: String,
        imageBytes: ByteArray?,
        imageMimeType: String?,
        imageFileName: String?,
        videoBytes: ByteArray?,
        videoMimeType: String?,
        videoFileName: String?,
        visibility: String,
        backgroundColor: String?,
        captionPositionX: Float?,
        captionPositionY: Float?,
        pollOptions: List<String>,
        pollPositionX: Float?,
        pollPositionY: Float?,
        pollScale: Float?,
        overlayImageUrl: String? = null,
        overlayImagePositionX: Float? = null,
        overlayImagePositionY: Float? = null,
        overlayImageScale: Float? = null,
    ): CreateStoryResult = withContext(Dispatchers.IO) {
        try {
            fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaTypeOrNull())
            fun textOrNull(value: String?): RequestBody? = value?.let { text(it) }

            val imagePart: MultipartBody.Part? = imageBytes?.let { bytes ->
                val body = bytes.toRequestBody(imageMimeType?.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("image", imageFileName ?: "image.jpg", body)
            }
            val videoPart: MultipartBody.Part? = videoBytes?.let { bytes ->
                val body = bytes.toRequestBody(videoMimeType?.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("video", videoFileName ?: "upload.mp4", body)
            }
            val opt = { index: Int -> textOrNull(pollOptions.getOrNull(index)) }

            val response = storiesApi.createStory(
                caption = text(caption),
                image = imagePart,
                video = videoPart,
                visibility = text(visibility),
                backgroundColor = textOrNull(backgroundColor),
                captionPositionX = textOrNull(captionPositionX?.toString()),
                captionPositionY = textOrNull(captionPositionY?.toString()),
                pollOption1 = opt(0),
                pollOption2 = opt(1),
                pollOption3 = opt(2),
                pollOption4 = opt(3),
                pollPositionX = textOrNull(pollPositionX?.toString()),
                pollPositionY = textOrNull(pollPositionY?.toString()),
                pollScale = textOrNull(pollScale?.toString()),
                overlayImageUrl = textOrNull(overlayImageUrl),
                overlayImagePositionX = textOrNull(overlayImagePositionX?.toString()),
                overlayImagePositionY = textOrNull(overlayImagePositionY?.toString()),
                overlayImageScale = textOrNull(overlayImageScale?.toString()),
            )
            val body: CreateStoryResponse? = response.body()
            if (response.isSuccessful && body != null && body.error == null && body.ok) {
                CreateStoryResult.Success(body.storyId, body.pollError)
            } else {
                CreateStoryResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CreateStoryResult.Error("network_error")
        } catch (e: Exception) {
            CreateStoryResult.Error("unknown_error")
        }
    }

    suspend fun reactToStory(storyId: String, emoji: String): ReactToStoryResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.reactToStory(storyId, ReactToStoryRequest(emoji))
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null && body.ok) {
                ReactToStoryResult.Success(body.conversationId)
            } else {
                ReactToStoryResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ReactToStoryResult.Error("network_error")
        } catch (e: Exception) {
            ReactToStoryResult.Error("unknown_error")
        }
    }

    suspend fun replyToStory(storyId: String, text: String): ReplyToStoryResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.replyToStory(storyId, ReplyToStoryRequest(text))
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null && body.ok) {
                ReplyToStoryResult.Success(body.conversationId)
            } else {
                ReplyToStoryResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ReplyToStoryResult.Error("network_error")
        } catch (e: Exception) {
            ReplyToStoryResult.Error("unknown_error")
        }
    }

    suspend fun deleteStory(storyId: String): DeleteStoryResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.deleteStory(storyId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                DeleteStoryResult.Success
            } else {
                DeleteStoryResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeleteStoryResult.Error("network_error")
        } catch (e: Exception) {
            DeleteStoryResult.Error("unknown_error")
        }
    }

    suspend fun saveHighlight(
        storyId: String,
        highlightId: String? = null,
        newTitle: String? = null,
    ): SaveHighlightResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.saveHighlight(storyId, SaveHighlightRequest(highlightId, newTitle))
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null && body.ok) {
                SaveHighlightResult.Success(body.highlightId)
            } else {
                SaveHighlightResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            SaveHighlightResult.Error("network_error")
        } catch (e: Exception) {
            SaveHighlightResult.Error("unknown_error")
        }
    }

    suspend fun getHighlights(userId: String): HighlightsResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.getHighlights(userId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                HighlightsResult.Success((body.highlights ?: emptyList()).map { it.toDomain() })
            } else {
                HighlightsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            HighlightsResult.Error("network_error")
        } catch (e: Exception) {
            HighlightsResult.Error("unknown_error")
        }
    }

    suspend fun viewHighlight(highlightId: String): HighlightViewResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.viewHighlight(highlightId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                HighlightViewResult.Success(
                    HighlightView(
                        title = body.title ?: "",
                        isMine = body.isMine,
                        items = (body.items ?: emptyList()).map { it.toDomain() },
                    )
                )
            } else {
                HighlightViewResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            HighlightViewResult.Error("network_error")
        } catch (e: Exception) {
            HighlightViewResult.Error("unknown_error")
        }
    }

    suspend fun deleteHighlight(highlightId: String): DeleteHighlightResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.deleteHighlight(highlightId)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                DeleteHighlightResult.Success
            } else {
                DeleteHighlightResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeleteHighlightResult.Error("network_error")
        } catch (e: Exception) {
            DeleteHighlightResult.Error("unknown_error")
        }
    }

    /** HighlightsScreen'deki "Düzenle" aksiyonu — saveHighlight/deleteHighlight
     * ile AYNI desen. Kapak değiştirme (coverUrl) bu turda native UI'dan hiç
     * ÇAĞRILMIYOR (SADECE başlık düzenleniyor, kapsam dışı bırakıldı — bkz.
     * HighlightsScreen.kt yorumu), parametre yine de backend sözleşmesiyle
     * TAM eşleşsin diye burada duruyor. */
    suspend fun updateHighlight(
        highlightId: String,
        title: String? = null,
        coverUrl: String? = null,
    ): UpdateHighlightResult = withContext(Dispatchers.IO) {
        try {
            val response = storiesApi.updateHighlight(highlightId, UpdateHighlightRequest(title, coverUrl))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                UpdateHighlightResult.Success
            } else {
                UpdateHighlightResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            UpdateHighlightResult.Error("network_error")
        } catch (e: Exception) {
            UpdateHighlightResult.Error("unknown_error")
        }
    }
}
