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
 * Profil ayarları için 7 endpoint — app/api_v1.py "# ----------------------- PROFİL
 * AYARLARI" başlığı altındaki fonksiyonlarla (bkz. ApiModels.kt DTO yorumları)
 * birebir eşleşir. ProfileApi/MessagingApi/InteractionsApi ile AYNI desen: tek
 * bir domain'in tüm endpoint'lerini kendi dosyasında toplar.
 *
 * editProfile multipart/form-data (JSON DEĞİL) — InteractionsApi.createPost()
 * ile AYNI kodlama deseni: opsiyonel bir avatar dosyası içerebiliyor. is_private/
 * hide_last_seen backend'de _bool_form_field() ile "true"/"false" string'i
 * bekliyor (native her zaman açık bir string gönderir) — bu yüzden Boolean
 * DEĞİL RequestBody olarak taşınır (çağıran taraf SettingsRepository'de
 * "true"/"false" metnine çevirir).
 */
interface SettingsApi {
    @Multipart
    @POST("profile/edit")
    suspend fun editProfile(
        @Part("full_name") fullName: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part("username") username: RequestBody,
        @Part("is_private") isPrivate: RequestBody,
        @Part("hide_last_seen") hideLastSeen: RequestBody,
        @Part avatar: MultipartBody.Part?,
    ): Response<EditProfileResponse>

    @GET("notifications/preferences")
    suspend fun getNotificationPreferences(): Response<NotificationPreferencesResponse>

    @POST("notifications/preferences")
    suspend fun updateNotificationPreferences(
        @Body preferences: NotificationPreferencesDto,
    ): Response<SimpleOkResponse>

    @GET("close-friends")
    suspend fun getCloseFriends(): Response<CloseFriendsResponse>

    @POST("close-friends/add")
    suspend fun addCloseFriend(@Body request: AddCloseFriendRequest): Response<SimpleOkResponse>

    @POST("close-friends/{userId}/remove")
    suspend fun removeCloseFriend(@Path("userId") userId: String): Response<SimpleOkResponse>

    @POST("profile/deactivate")
    suspend fun deactivateAccount(@Body request: DeactivateAccountRequest): Response<SimpleOkResponse>
}
