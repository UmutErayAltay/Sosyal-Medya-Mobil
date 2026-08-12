package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.LinkPreviewApi
import com.umuterayaltay.sosyal.nativeapp.network.LinkPreviewDto
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class LinkPreviewResult {
    data class Success(val preview: LinkPreviewDto) : LinkPreviewResult()
    // Backend ok:false'u HTTP 200 ile döner (önizleme yok, HATA değil) —
    // HashtagRepository/diğer repository'lerdeki "Error" ile KARIŞTIRILMAZ,
    // ayrı bir durum: UI kart hiç göstermeden sessizce geçer.
    object NotAvailable : LinkPreviewResult()
    data class Error(val code: String?) : LinkPreviewResult()
}

/**
 * Post/mesajlarda paylaşılan linkler için Open Graph önizleme — HashtagRepository
 * ile AYNI desen (Room cache YOK, canlı istek; backend zaten kendi tarafında
 * link_previews tablosuyla cache'liyor, native tarafta ayrıca cache'lemeye
 * gerek yok — sayfa oturumu içi in-memory cache UI katmanında tutulur).
 */
class LinkPreviewRepository(
    private val linkPreviewApi: LinkPreviewApi,
) {
    suspend fun getPreview(url: String): LinkPreviewResult = withContext(Dispatchers.IO) {
        try {
            val response = linkPreviewApi.getPreview(url)
            val body = response.body()
            when {
                response.isSuccessful && body?.ok == true -> LinkPreviewResult.Success(body)
                response.isSuccessful && body?.ok == false -> LinkPreviewResult.NotAvailable
                else -> LinkPreviewResult.Error(RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            LinkPreviewResult.Error("network_error")
        } catch (e: Exception) {
            LinkPreviewResult.Error("unknown_error")
        }
    }
}
