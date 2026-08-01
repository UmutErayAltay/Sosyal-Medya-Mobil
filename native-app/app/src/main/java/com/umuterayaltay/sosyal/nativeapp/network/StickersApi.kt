package com.umuterayaltay.sosyal.nativeapp.network

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Sticker backend'i (app/api_v1/stickers.py, Faz 5 Dalga 1C — backend TAMAMEN
 * bitti/doğrulandı, bkz. StickerDto/StickersResponse/CreateStickerResponse
 * yorumları ApiModels.kt). BU turda UI/tüketici YOK — sadece network+repository
 * katmanı, Dalga 3'te başka bir ajan mesaj/yorum'a sticker seçici ekleyecek.
 */
interface StickersApi {

    @GET("stickers/mine")
    suspend fun getMyStickers(): Response<StickersResponse>

    // Yanıt SARMALANMAMIŞ — doğrudan sticker satırı ({"id","image_url",...}),
    // bu yüzden StickerDto'ya DİREKT parse edilir (StickersResponse DEĞİL).
    @GET("stickers/{id}")
    suspend fun getSticker(@Path("id") id: String): Response<StickerDto>

    @Multipart
    @POST("stickers/new")
    suspend fun createSticker(@Part image: MultipartBody.Part): Response<CreateStickerResponse>

    @POST("stickers/{id}/save")
    suspend fun saveSticker(@Path("id") id: String): Response<SimpleOkResponse>

    @POST("stickers/{id}/remove")
    suspend fun removeSticker(@Path("id") id: String): Response<SimpleOkResponse>
}
