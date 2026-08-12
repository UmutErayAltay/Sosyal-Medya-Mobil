package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Post/mesajlarda paylaşılan linkler için Open Graph önizleme kartı —
 * app/api_v1/link_preview.py api_link_preview() ile birebir eşleşir
 * (MentionsApi ile AYNI "dosya-başına-konu" deseni).
 */
interface LinkPreviewApi {
    @GET("link-preview")
    suspend fun getPreview(@Query("url") url: String): Response<LinkPreviewDto>
}
