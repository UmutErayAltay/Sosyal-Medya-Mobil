package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.MentionSuggestionDto
import com.umuterayaltay.sosyal.nativeapp.network.MentionsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class MentionSearchResult {
    data class Success(val users: List<MentionSuggestionDto>) : MentionSearchResult()
    data class Error(val code: String?) : MentionSearchResult()
}

/** @etiketleme otomatik tamamlama — ReportsRepository ile AYNI ince desen
 * (tek endpoint'lik konu, kendi dosyasında). Otomatik tamamlama kritik
 * olmadığı için (web'in mentionAutocomplete.js'indeki "sessizce yut" ile
 * AYNI felsefe) çağıran taraf hata durumunda genelde sessizce dropdown'ı
 * boş bırakır, ayrı bir kullanıcı-görünür hata YOKTUR. */
class MentionsRepository(
    private val mentionsApi: MentionsApi,
) {
    suspend fun search(query: String): MentionSearchResult = withContext(Dispatchers.IO) {
        try {
            val response = mentionsApi.search(query)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                MentionSearchResult.Success(body.users ?: emptyList())
            } else {
                MentionSearchResult.Error(RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            MentionSearchResult.Error("network_error")
        } catch (e: Exception) {
            MentionSearchResult.Error("unknown_error")
        }
    }
}
