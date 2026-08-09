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
    // 2026-08-09 (kullanıcı isteği: "1'den fazla görsel ekleme olsun, instadaki
    // gibi kaydırmalı olabilir") — `image` (tekil) `images` (çoklu, en fazla 4)
    // ile DEĞİŞTİRİLDİ: backend artık HEM yeni `images` alanını (öncelikli)
    // HEM eski tekil `image` alanını (geriye dönük uyumluluk) kabul ediyor
    // (bkz. api_v1/interactions.py::api_create_post()), ama BU istemci ARTIK
    // SADECE `images`'i kullanıyor — eski tekil alan sadece henüz güncellenmemiş
    // KURULU uygulamalar için backend'de yaşıyor, burada tekrar İCAT EDİLMEDİ.
    // Boş liste geçilirse Retrofit hiçbir "images" parçası eklemez (backend'de
    // getlist("images") boş döner) — aynı `video` null-atlama deseniyle TUTARLI.
    // `gif_url` (Faz 5 Dalga 3B) SADECE görsel/video YOKSA backend'de kullanılır.
    // `poll_option_1..4` (Faz 5 Dalga 4C) — web'in routes/posts.py
    // create_post()'undaki AYNI sözleşme: request.form.get(f"poll_option_{i}")
    // ile birebir isim eşleşmesi, en az 2 dolu seçenek "anket var" sayılır,
    // görsel/video ile mutually-exclusive DEĞİL (GIF'in aksine birlikte olabilir).
    @Multipart
    @POST("posts")
    suspend fun createPost(
        @Part("content") content: RequestBody,
        @Part("visibility") visibility: RequestBody,
        @Part images: List<MultipartBody.Part>,
        @Part video: MultipartBody.Part?,
        @Part("is_reel") isReel: RequestBody,
        @Part("gif_url") gifUrl: RequestBody?,
        @Part("poll_option_1") pollOption1: RequestBody?,
        @Part("poll_option_2") pollOption2: RequestBody?,
        @Part("poll_option_3") pollOption3: RequestBody?,
        @Part("poll_option_4") pollOption4: RequestBody?,
    ): Response<CreatePostResponse>

    // Post yönetimi (sahibinin kendi postuna uyguladığı yaşam döngüsü
    // işlemleri) — app/api_v1/posts.py ile birebir, bkz. ApiModels.kt DTO
    // yorumları.
    @POST("posts/{id}/edit")
    suspend fun editPost(
        @Path("id") postId: String,
        @Body request: EditPostRequest,
    ): Response<EditPostResponse>

    @POST("posts/{id}/delete")
    suspend fun deletePost(@Path("id") postId: String): Response<DeletePostResponse>

    @POST("posts/{id}/archive")
    suspend fun toggleArchive(@Path("id") postId: String): Response<ArchivePostResponse>

    @POST("posts/{id}/pin")
    suspend fun togglePin(@Path("id") postId: String): Response<PinPostResponse>

    // Yorum mutasyonları — app/api_v1/interactions.py'nin BİREBİR mirror'ı,
    // bkz. ApiModels.kt DTO yorumları.
    @POST("comments/{id}/delete")
    suspend fun deleteComment(@Path("id") commentId: String): Response<DeleteCommentResponse>

    @POST("comments/{id}/like")
    suspend fun toggleCommentLike(@Path("id") commentId: String): Response<ToggleCommentLikeResponse>

    @POST("comments/{id}/react")
    suspend fun reactComment(
        @Path("id") commentId: String,
        @Body request: ReactCommentRequest,
    ): Response<ReactCommentResponse>
}
