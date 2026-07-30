package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Profil ekrani icin 8 endpoint - app/api_v1.py satir ~607-1221 (bkz.
 * ApiModels.kt DTO yorumlari) ile birebir eslesir. Backend URL segmentleri
 * SIRALI (/follow-requests/<follower_id>/accept) - Retrofit path parametre
 * ADI (followerId) backend'deki isimle (follower_id) eslesmek ZORUNDA degil,
 * sadece {followerId} template'i ile @Path("followerId") ayni olmali yeter.
 */
interface ProfileApi {
    @GET("profile/{username}")
    suspend fun getProfile(@Path("username") username: String): Response<ProfileResponse>

    @GET("profile/{username}/followers")
    suspend fun getFollowers(@Path("username") username: String): Response<FollowListResponse>

    @GET("profile/{username}/following")
    suspend fun getFollowing(@Path("username") username: String): Response<FollowListResponse>

    @GET("profile/insights")
    suspend fun getInsights(@Query("days") days: Int): Response<InsightsResponse>

    @POST("profile/{username}/follow")
    suspend fun toggleFollow(@Path("username") username: String): Response<ToggleFollowResponse>

    @GET("follow-requests")
    suspend fun getFollowRequests(): Response<FollowRequestsResponse>

    @POST("follow-requests/{followerId}/accept")
    suspend fun acceptFollowRequest(@Path("followerId") followerId: String): Response<SimpleOkResponse>

    @POST("follow-requests/{followerId}/reject")
    suspend fun rejectFollowRequest(@Path("followerId") followerId: String): Response<SimpleOkResponse>

    // ---- Engelleme (Faz 4 sonrası eksik giderme — bkz. ApiModels.kt "Engelleme"
    // bölüm notu). toggleFollow ile AYNI konuma (ProfileApi) bilinçli olarak
    // eklendi: block de username'e bağlı bir profil aksiyonu, ProfileViewModel
    // zaten profileRepository'yi kullanıyor - SettingsApi'ye taşımak gereksiz
    // bir çapraz-repository bağımlılığı yaratırdı.
    @POST("block/{username}")
    suspend fun toggleBlock(@Path("username") username: String): Response<ToggleBlockResponse>

    @GET("blocked")
    suspend fun getBlockedUsers(): Response<BlockedUsersResponse>
}
