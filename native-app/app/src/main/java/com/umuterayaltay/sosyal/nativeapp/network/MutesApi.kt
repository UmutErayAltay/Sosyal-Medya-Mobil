package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Sessize alma (Faz 5, Dalga 2A) — app/api_v1/mutes.py api_toggle_mute_user()/
 * api_toggle_mute_post() ile birebir eşleşir. HashtagApi.toggleHashtagFollow()
 * ile AYNI desen: gövdesiz POST, yanıt {"ok","action"} şekli.
 */
interface MutesApi {
    @POST("users/{userId}/mute")
    suspend fun toggleUserMute(@Path("userId") userId: String): Response<ToggleMuteResponse>

    @POST("posts/{postId}/mute")
    suspend fun toggleMutePost(@Path("postId") postId: String): Response<ToggleMuteResponse>
}
