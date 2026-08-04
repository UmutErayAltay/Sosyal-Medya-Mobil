package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.BookmarksApi
import com.umuterayaltay.sosyal.nativeapp.network.CollectionDto
import com.umuterayaltay.sosyal.nativeapp.network.CreateCollectionRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SetBookmarkCollectionRequest
import com.umuterayaltay.sosyal.nativeapp.network.ToggleBookmarkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ToggleBookmarkResult {
    data class Success(val bookmarked: Boolean) : ToggleBookmarkResult()
    data class Error(val code: String?) : ToggleBookmarkResult()
}

sealed class CollectionsResult {
    data class Success(val collections: List<CollectionDto>) : CollectionsResult()
    data class Error(val code: String?) : CollectionsResult()
}

sealed class CreateCollectionResult {
    data class Success(val id: String, val name: String) : CreateCollectionResult()
    data class Error(val code: String?) : CreateCollectionResult()
}

sealed class DeleteCollectionResult {
    object Success : DeleteCollectionResult()
    data class Error(val code: String?) : DeleteCollectionResult()
}

sealed class SetBookmarkCollectionResult {
    object Success : SetBookmarkCollectionResult()
    data class Error(val code: String?) : SetBookmarkCollectionResult()
}

/**
 * Kaydedilenler + koleksiyon (Faz 5, Dalga 3A) için repository —
 * HashtagRepository/MutesRepository ile AYNI gerekçeyle Room cache YOK
 * (canlı toggle, offline-first ihtiyacı yok).
 */
class BookmarksRepository(
    private val bookmarksApi: BookmarksApi,
) {

    suspend fun toggleBookmark(postId: String, collectionId: String? = null): ToggleBookmarkResult =
        withContext(Dispatchers.IO) {
            try {
                val response = bookmarksApi.toggleBookmark(postId, ToggleBookmarkRequest(collectionId))
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    ToggleBookmarkResult.Success(body.bookmarked)
                } else {
                    ToggleBookmarkResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                ToggleBookmarkResult.Error("network_error")
            } catch (e: Exception) {
                ToggleBookmarkResult.Error("unknown_error")
            }
        }

    suspend fun getCollections(): CollectionsResult = withContext(Dispatchers.IO) {
        try {
            val response = bookmarksApi.getCollections()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                CollectionsResult.Success(body.collections ?: emptyList())
            } else {
                CollectionsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CollectionsResult.Error("network_error")
        } catch (e: Exception) {
            CollectionsResult.Error("unknown_error")
        }
    }

    suspend fun createCollection(name: String): CreateCollectionResult = withContext(Dispatchers.IO) {
        try {
            val response = bookmarksApi.createCollection(CreateCollectionRequest(name))
            val body = response.body()
            if (response.isSuccessful && body?.id != null && body.name != null) {
                CreateCollectionResult.Success(body.id, body.name)
            } else {
                CreateCollectionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CreateCollectionResult.Error("network_error")
        } catch (e: Exception) {
            CreateCollectionResult.Error("unknown_error")
        }
    }

    suspend fun deleteCollection(collectionId: String): DeleteCollectionResult = withContext(Dispatchers.IO) {
        try {
            val response = bookmarksApi.deleteCollection(collectionId)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                DeleteCollectionResult.Success
            } else {
                DeleteCollectionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeleteCollectionResult.Error("network_error")
        } catch (e: Exception) {
            DeleteCollectionResult.Error("unknown_error")
        }
    }

    suspend fun setBookmarkCollection(postId: String, collectionId: String?): SetBookmarkCollectionResult =
        withContext(Dispatchers.IO) {
            try {
                val response = bookmarksApi.setBookmarkCollection(
                    postId, SetBookmarkCollectionRequest(collectionId)
                )
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    SetBookmarkCollectionResult.Success
                } else {
                    SetBookmarkCollectionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SetBookmarkCollectionResult.Error("network_error")
            } catch (e: Exception) {
                SetBookmarkCollectionResult.Error("unknown_error")
            }
        }
}
