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
