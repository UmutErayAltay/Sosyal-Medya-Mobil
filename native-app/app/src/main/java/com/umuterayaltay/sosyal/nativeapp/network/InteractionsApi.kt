package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Post beğeni + yorum (post detay) için 3 endpoint — app/api_v1.py satır
 * ~1582-1801 (bkz. ApiModels.kt DTO yorumları) ile birebir eşleşir.
 * ProfileApi/MessagingApi ile AYNI desen: tek bir domain'in tüm endpoint'lerini
 * kendi dosyasında toplar.
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
}
