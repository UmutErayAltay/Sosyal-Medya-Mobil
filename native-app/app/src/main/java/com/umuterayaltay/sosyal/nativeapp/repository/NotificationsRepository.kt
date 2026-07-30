package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.NotificationDto
import com.umuterayaltay.sosyal.nativeapp.network.NotificationsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class NotificationsResult {
    data class Success(val notifications: List<NotificationDto>, val hasNext: Boolean) : NotificationsResult()
    data class Error(val code: String?) : NotificationsResult()
}

sealed class UnreadCountResult {
    data class Success(val count: Int) : UnreadCountResult()
    data class Error(val code: String?) : UnreadCountResult()
}

/**
 * Bildirimler ekranı için repository — MessagingRepository ile AYNI gerekçeyle
 * Room cache YOK (canlı veri, ayrı bir yerel önbellek bu MVP turunda
 * gereksiz). Sayfa çekildiğinde backend zaten o sayfadaki okunmamışları
 * okundu işaretliyor (bkz. app/api_v1.py api_list_notifications() docstring'i)
 * — native taraf ConversationViewModel.markRead() gibi AYRICA bir "okundu
 * işaretle" çağrısı YAPMAZ, gerek yok.
 */
class NotificationsRepository(
    private val notificationsApi: NotificationsApi,
) {

    suspend fun getNotifications(page: Int): NotificationsResult = withContext(Dispatchers.IO) {
        try {
            val response = notificationsApi.getNotifications(page)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                NotificationsResult.Success(body.notifications ?: emptyList(), body.hasNext)
            } else {
                NotificationsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            NotificationsResult.Error("network_error")
        } catch (e: Exception) {
            NotificationsResult.Error("unknown_error")
        }
    }

    suspend fun getUnreadCount(): UnreadCountResult = withContext(Dispatchers.IO) {
        try {
            val response = notificationsApi.getUnreadCount()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                UnreadCountResult.Success(body.count)
            } else {
                UnreadCountResult.Error(RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            UnreadCountResult.Error("network_error")
        } catch (e: Exception) {
            UnreadCountResult.Error("unknown_error")
        }
    }
}
