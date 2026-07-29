package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<LogoutResponse>

    @GET("auth/me")
    suspend fun me(): Response<MeResponse>
}

interface FeedApi {
    @GET("feed")
    suspend fun getFeed(
        @Query("cursor") cursor: Int,
        @Query("limit") limit: Int,
    ): Response<FeedResponse>
}
