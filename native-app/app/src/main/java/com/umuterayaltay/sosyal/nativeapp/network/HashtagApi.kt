package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Hashtag detay + gündem için endpoint'ler — app/api_v1.py api_hashtag_posts()/
 * api_toggle_hashtag_follow()/api_trending() ile birebir eşleşir (Faz 4 sonrası
 * eksik giderme SONUNCUSU, native Android). ProfileApi.toggleBlock() ile AYNI
 * desen: backend zaten tag'i küçük harfe çeviriyor (tag.lower()), burada
 * AYRICA lowercase'e ÇEVRİLMEDİ.
 */
interface HashtagApi {
    @GET("hashtag/{tag}")
    suspend fun getHashtagPosts(@Path("tag") tag: String): Response<HashtagPostsResponse>

    @POST("hashtag/{tag}/follow")
    suspend fun toggleHashtagFollow(@Path("tag") tag: String): Response<ToggleHashtagFollowResponse>

    @GET("trending")
    suspend fun getTrending(): Response<TrendingResponse>
}
