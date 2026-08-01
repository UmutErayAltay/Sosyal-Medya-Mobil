package com.umuterayaltay.sosyal.nativeapp.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Post beğeni + yorum (post detay) + post OLUŞTURMA için endpoint'ler —
 * app/api_v1.py satır ~1582-1801 (beğeni/yorum) ve "# ----------------------- POST
 * OLUŞTURMA" başlığı altındaki api_create_post() (bkz. ApiModels.kt DTO
 * yorumları) ile birebir eşleşir. ProfileApi/MessagingApi ile AYNI desen: tek
 * bir domain'in tüm endpoint'lerini kendi dosyasında toplar.
 */
interface InteractionsApi {
    // Retrofit interface metodu abstract olmak zorunda (dinamik proxy ile
    // implemente edilir) - Kotlin'de abstract metotta varsayılan parametre
    // değeri desteklenmez, bu yüzden @Body burada ZORUNLU; "reaction=null"
    // isteyen çağıran taraf InteractionsRepository'de LikeRequest(null) inşa eder.
    @POST("posts/{id}/like")
    suspend fun toggleLike(
        @Path("id") postId: String,
        @Body request: LikeRequest,
    ): Response<ToggleLikeResponse>

    @GET("posts/{id}")
    suspend fun getPostDetail(@Path("id") postId: String): Response<PostDetailResponse>

    @POST("posts/{id}/comments")
    suspend fun addComment(
        @Path("id") postId: String,
        @Body request: AddCommentRequest,
    ): Response<AddCommentResponse>

    // multipart/form-data — JSON DEĞİL, çünkü opsiyonel bir görsel/video
    // dosyası içerebiliyor (backend api_create_post() ile AYNI kodlama).
    // `image`/`video` null geçilirse Retrofit bu parçayı isteğe hiç eklemez
    // (backend'de request.files.get(...) None döner, has_image/has_video
    // False olur) — eski (sadece görsel) çağrı yerleri BOZULMAZ.
    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part("content") content: RequestBody,
        @Part("visibility") visibility: RequestBody,
        @Part image: MultipartBody.Part?,
        @Part video: MultipartBody.Part?,
        @Part("is_reel") isReel: RequestBody,
    ): Response<CreatePostResponse>
}
