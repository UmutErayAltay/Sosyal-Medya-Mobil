package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<LoginResponse>

    @POST("auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<LogoutResponse>

    @GET("auth/me")
    suspend fun me(): Response<MeResponse>

    // Mesajlaşmaya ÖZEL değil (ana bearer token'a bağlı genel bir auth
    // endpoint'i — app/api_v1.py api_realtime_token()) — bu yüzden
    // MessagingApi'ye DEĞİL, buraya eklendi (bkz. RealtimeConnectionManager).
    @GET("realtime-token")
    suspend fun getRealtimeToken(): Response<RealtimeTokenResponse>

    // FAZ5: Şifre sıfırlama (Dalga 1C) — auth YOK (login/register gibi açık
    // endpoint), HER DURUMDA {"ok":true} döner (enumeration koruması backend'de
    // zaten yapılıyor, bkz. ForgotPasswordRequest yorumu ApiModels.kt).
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<SimpleOkResponse>
}

interface FeedApi {
    @GET("feed")
    suspend fun getFeed(
        @Query("cursor") cursor: Int,
        @Query("limit") limit: Int,
    ): Response<FeedResponse>
}

interface DiscoverApi {
    @GET("discover")
    suspend fun getDiscover(@Query("page") page: Int): Response<DiscoverResponse>

    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("type") type: String,
        @Query("date_from") dateFrom: String?,
        @Query("date_to") dateTo: String?,
    ): Response<SearchResponse>

    @POST("search/save")
    suspend fun saveSearch(@Body request: SaveSearchRequest): Response<SimpleOkResponse>

    @POST("search/history/clear")
    suspend fun clearSearchHistory(): Response<SimpleOkResponse>

    @POST("search/history/{id}/delete")
    suspend fun deleteSearchHistoryItem(@Path("id") id: String): Response<SimpleOkResponse>

    @POST("search/saved/{id}/delete")
    suspend fun deleteSavedSearchItem(@Path("id") id: String): Response<SimpleOkResponse>
}

interface ReelsApi {
    @GET("reels")
    suspend fun getReels(@Query("page") page: Int): Response<ReelsResponse>
}
