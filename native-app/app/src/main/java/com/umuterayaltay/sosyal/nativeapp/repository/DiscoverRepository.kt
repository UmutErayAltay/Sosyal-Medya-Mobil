package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.DiscoverApi
import com.umuterayaltay.sosyal.nativeapp.network.HashtagSearchDto
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SaveSearchRequest
import com.umuterayaltay.sosyal.nativeapp.network.SavedSearchItemDto
import com.umuterayaltay.sosyal.nativeapp.network.SearchHistoryItemDto
import com.umuterayaltay.sosyal.nativeapp.network.SimpleOkResponse
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

sealed class DiscoverPageResult {
    data class Success(val posts: List<Post>, val hasMore: Boolean, val page: Int) : DiscoverPageResult()
    data class Error(val code: String?) : DiscoverPageResult()
}

data class SearchResults(
    val users: List<UserSearchDto>,
    val posts: List<Post>,
    val hashtags: List<HashtagSearchDto>,
    val recentSearches: List<SearchHistoryItemDto>,
    val savedSearches: List<SavedSearchItemDto>,
)

sealed class SearchResult {
    data class Success(val data: SearchResults) : SearchResult()
    data class Error(val code: String?) : SearchResult()
}

/** Kaydet/sil/temizle gibi ikincil eylemler için ortak sonuç — SearchResult'tan
 * ayrı tutuldu çünkü bunlar bir liste/veri döndürmez, sadece ok/error taşır. */
sealed class SearchActionResult {
    data object Success : SearchActionResult()
    data class Error(val code: String?) : SearchActionResult()
}

/**
 * Keşfet akışı + arama (kullanıcı/post/hashtag + geçmiş/kayıtlı aramalar) için
 * repository. FeedRepository'nin AKSİNE Room cache YOK — bu BİLİNÇLİ bir kapsam
 * kararı: arama/keşfet "canlı" veri, Feed'deki gibi offline-first ihtiyacı yok.
 */
class DiscoverRepository(
    private val discoverApi: DiscoverApi,
) {

    suspend fun getDiscoverPage(page: Int): DiscoverPageResult = withContext(Dispatchers.IO) {
        try {
            val response = discoverApi.getDiscover(page)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                DiscoverPageResult.Success(
                    posts = (body.posts ?: emptyList()).map { it.toDomain() },
                    hasMore = body.hasMore,
                    page = body.page,
                )
            } else {
                DiscoverPageResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DiscoverPageResult.Error("network_error")
        } catch (e: Exception) {
            DiscoverPageResult.Error("unknown_error")
        }
    }

    suspend fun search(q: String, type: String, dateFrom: String?, dateTo: String?): SearchResult =
        withContext(Dispatchers.IO) {
            try {
                val response = discoverApi.search(
                    q = q,
                    type = type,
                    dateFrom = dateFrom?.takeIf { it.isNotBlank() },
                    dateTo = dateTo?.takeIf { it.isNotBlank() },
                )
                val body = response.body()
                if (response.isSuccessful && body != null && body.error == null) {
                    SearchResult.Success(
                        SearchResults(
                            users = body.users ?: emptyList(),
                            posts = (body.posts ?: emptyList()).map { it.toDomain() },
                            hashtags = body.hashtags ?: emptyList(),
                            recentSearches = body.recentSearches ?: emptyList(),
                            savedSearches = body.savedSearches ?: emptyList(),
                        )
                    )
                } else {
                    SearchResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SearchResult.Error("network_error")
            } catch (e: Exception) {
                SearchResult.Error("unknown_error")
            }
        }

    suspend fun saveSearch(q: String, label: String?): SearchActionResult =
        runAction { discoverApi.saveSearch(SaveSearchRequest(q = q, label = label)) }

    suspend fun clearHistory(): SearchActionResult =
        runAction { discoverApi.clearSearchHistory() }

    suspend fun deleteHistoryItem(id: String): SearchActionResult =
        runAction { discoverApi.deleteSearchHistoryItem(id) }

    suspend fun deleteSavedSearch(id: String): SearchActionResult =
        runAction { discoverApi.deleteSavedSearchItem(id) }

    /** search()/login()'deki IOException (network_error) vs genel Exception ayrımıyla
     * TUTARLI, ok/error şekilli tüm ikincil eylem endpoint'leri için tek ortak yol. */
    private suspend fun runAction(call: suspend () -> Response<SimpleOkResponse>): SearchActionResult =
        withContext(Dispatchers.IO) {
            try {
                val response = call()
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    SearchActionResult.Success
                } else {
                    SearchActionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SearchActionResult.Error("network_error")
            } catch (e: Exception) {
                SearchActionResult.Error("unknown_error")
            }
        }
}
