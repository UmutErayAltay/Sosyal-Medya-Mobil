package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * GIF arama/paylaşma — app/api_v1/gifs.py api_gif_search() ile birebir eşleşir
 * (Faz 5 Dalga 3B). Web app/gifs.py gif_search()'ün AYNI Klipy proxy mantığı,
 * sadece token-authenticated. Klipy API anahtarı HER ZAMAN sunucuda kalır —
 * bu endpoint sadece proxy, native taraf anahtarı hiç görmez.
 */
interface GifsApi {
    @GET("gif/search")
    suspend fun searchGifs(@Query("q") query: String): Response<GifSearchResponse>
}
