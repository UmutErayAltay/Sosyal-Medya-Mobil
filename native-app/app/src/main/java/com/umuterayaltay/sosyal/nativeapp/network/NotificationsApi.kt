package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Bildirimler ekranı için endpoint'ler — app/api_v1.py api_list_notifications()/
 * api_unread_notifications_count() ile birebir eşleşir (bkz. ApiModels.kt
 * NotificationDto yorumu). MessagingApi/ProfileApi ile AYNI desen: bildirimler
 * kendi başına bir domain olduğu için AYRI bir dosyada toplandı (mesajlaşmanın
 * veya profilin bir parçası değil), ayrı bir GroupApi/ReelsApi ile AYNI
 * gerekçe.
 */
interface NotificationsApi {
    @GET("notifications")
    suspend fun getNotifications(@Query("page") page: Int): Response<NotificationsResponse>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): Response<UnreadCountResponse>
}
