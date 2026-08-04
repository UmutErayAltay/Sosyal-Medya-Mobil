package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SharePostRequest
import com.umuterayaltay.sosyal.nativeapp.network.SharesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class SharePostResult {
    data class Success(val sentCount: Int) : SharePostResult()
    data class Error(val code: String?) : SharePostResult()
}

/**
 * Post paylaşımı (DM'e gönder) — MutesRepository ile AYNI desen (Room cache
 * YOK). Hedef kullanıcı id'leri PostShareSheet'te DiscoverApi kullanıcı
 * aramasından toplanır (bkz. SharesApi.kt yorumu — forward-targets REUSE
 * EDİLMEDİ, konuşma id'si döndürüyor).
 */
class SharesRepository(
    private val sharesApi: SharesApi,
) {
    suspend fun sharePost(postId: String, userIds: List<String>, note: String = ""): SharePostResult =
        withContext(Dispatchers.IO) {
            try {
                val response = sharesApi.sharePost(postId, SharePostRequest(userIds, note))
                val body = response.body()
                if (response.isSuccessful && body != null && body.error == null) {
                    SharePostResult.Success(body.sent)
                } else {
                    SharePostResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SharePostResult.Error("network_error")
            } catch (e: Exception) {
                SharePostResult.Error("unknown_error")
            }
        }
}
