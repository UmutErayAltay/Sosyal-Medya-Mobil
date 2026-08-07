package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * @etiketleme otomatik tamamlama — app/api_v1/mentions.py api_search_mentions()
 * ile birebir eşleşir (ReportsApi/RepostsApi ile AYNI "dosya-başına-konu"
 * deseni — bkz. o dosyanın kendi yorumu). Şu an SADECE yorum/yanıt kutusunda
 * kullanılır (PostDetailScreen), web'in post composer'ındaki kullanımı
 * kapsam dışı bırakıldı.
 */
interface MentionsApi {
    @GET("mentions/search")
    suspend fun search(@Query("q") query: String): Response<MentionSearchResponse>
}
