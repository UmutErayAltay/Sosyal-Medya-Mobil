package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Mesajlaşma ekranı için 5 endpoint — app/api_v1.py satır ~1289-1577 (bkz.
 * ApiModels.kt DTO yorumları) ile birebir eşleşir. ProfileApi.kt ile AYNI
 * desen: kendi dosyasında, tek bir domain'in tüm endpoint'lerini toplar.
 *
 * Yeni biriyle konuşma başlatmak için AYRI bir "kullanıcı ara" endpoint'i
 * İCAT EDİLMEDİ — mevcut DiscoverApi.search(type="users") reuse edilir
 * (bkz. MessagingRepository ve NewMessageScreen).
 */
interface MessagingApi {
    @GET("messages/conversations")
    suspend fun getConversations(): Response<ConversationsResponse>

    @GET("messages/conversations/{id}")
    suspend fun getConversationDetail(
        @Path("id") conversationId: String,
        @Query("page") page: Int,
    ): Response<ConversationDetailResponse>

    @POST("messages/conversations/{id}/send")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body request: SendMessageRequest,
    ): Response<SendMessageResponse>

    @POST("messages/start/{username}")
    suspend fun startConversation(@Path("username") username: String): Response<StartConversationResponse>

    @POST("messages/conversations/{id}/mark-read")
    suspend fun markRead(@Path("id") conversationId: String): Response<SimpleOkResponse>
}
