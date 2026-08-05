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
import retrofit2.http.Query

/**
 * Mesajlaşma ekranı için endpoint'ler — app/api_v1.py satır ~1289-1577 (bkz.
 * ApiModels.kt DTO yorumları) ile birebir eşleşir. ProfileApi.kt ile AYNI
 * desen: kendi dosyasında, tek bir domain'in tüm endpoint'lerini toplar.
 *
 * Yeni biriyle konuşma başlatmak için AYRI bir "kullanıcı ara" endpoint'i
 * İCAT EDİLMEDİ — mevcut DiscoverApi.search(type="users") reuse edilir
 * (bkz. MessagingRepository ve NewMessageScreen).
 *
 * Grup yönetimi (Faz 4) — 7 yeni endpoint, AYNI dosya/interface'e eklendi
 * (grup, mesajlaşmanın bir parçası, ayrı bir GroupApi İCAT EDİLMEDİ).
 */
interface MessagingApi {
    @GET("messages/conversations")
    suspend fun getConversations(): Response<ConversationsResponse>

    @GET("messages/conversations/{id}")
    suspend fun getConversationDetail(
        @Path("id") conversationId: String,
        @Query("page") page: Int,
    ): Response<ConversationDetailResponse>

    // multipart/form-data — content (opsiyonel metin), reply_to_id (opsiyonel),
    // image (opsiyonel dosya), sticker_id/gif_url (opsiyonel, Faz 5 Dalga 3B) —
    // en az content VEYA image VEYA sticker_id VEYA gif_url dolu olmalı (backend
    // hepsi boşsa 400 {"error":"empty"} döner). SettingsApi.editProfile()
    // ile AYNI desen (bkz. dosya yorumu). SendMessageRequest (JSON) artık
    // KULLANILMIYOR, ApiModels.kt'den kaldırıldı.
    @Multipart
    @POST("messages/conversations/{id}/send")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Part("content") content: RequestBody,
        @Part("reply_to_id") replyToId: RequestBody?,
        @Part image: MultipartBody.Part?,
        @Part("sticker_id") stickerId: RequestBody?,
        @Part("gif_url") gifUrl: RequestBody?,
    ): Response<SendMessageResponse>

    @POST("messages/start/{username}")
    suspend fun startConversation(@Path("username") username: String): Response<StartConversationResponse>

    @POST("messages/conversations/{id}/mark-read")
    suspend fun markRead(@Path("id") conversationId: String): Response<SimpleOkResponse>

    @POST("messages/group/new")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<CreateGroupResponse>

    @POST("messages/group/{id}/rename")
    suspend fun renameGroup(
        @Path("id") id: String,
        @Body request: RenameGroupRequest,
    ): Response<RenameGroupResponse>

    @GET("messages/group/{id}/members")
    suspend fun getGroupMembers(@Path("id") id: String): Response<GroupMembersResponse>

    @POST("messages/group/{id}/members/add")
    suspend fun addGroupMembers(
        @Path("id") id: String,
        @Body request: AddGroupMembersRequest,
    ): Response<AddGroupMembersResponse>

    @POST("messages/group/{id}/members/{userId}/remove")
    suspend fun removeGroupMember(
        @Path("id") id: String,
        @Path("userId") userId: String,
    ): Response<SimpleOkResponse>

    @POST("messages/group/{id}/members/{userId}/toggle-admin")
    suspend fun toggleGroupAdmin(
        @Path("id") id: String,
        @Path("userId") userId: String,
    ): Response<ToggleAdminResponse>

    @POST("messages/group/{id}/leave")
    suspend fun leaveGroup(@Path("id") id: String): Response<SimpleOkResponse>

    // ---- Mesaj gelişmiş işlemleri (Faz 5 Dalga 1B) — app/api_v1/messaging.py
    // "Mesaj gelişmiş işlemleri" bölümü ile birebir eşleşir (bkz. ApiModels.kt
    // yorumları). Sohbeti sessize alma da AYNI dalgada (backend'de aynı dosya/
    // bölümde) olduğu için burada, ayrı bir dosya İCAT EDİLMEDİ.

    @POST("messages/{id}/delete")
    suspend fun deleteMessage(@Path("id") messageId: String): Response<SimpleOkResponse>

    @POST("messages/{id}/edit")
    suspend fun editMessage(
        @Path("id") messageId: String,
        @Body request: EditMessageRequest,
    ): Response<EditMessageResponse>

    @POST("messages/{id}/react")
    suspend fun reactToMessage(
        @Path("id") messageId: String,
        @Body request: ReactRequest,
    ): Response<ReactResponse>

    @POST("messages/{id}/pin")
    suspend fun pinMessage(@Path("id") messageId: String): Response<PinResponse>

    @GET("messages/forward-targets")
    suspend fun getForwardTargets(): Response<ForwardTargetsResponse>

    @POST("messages/{id}/forward")
    suspend fun forwardMessage(
        @Path("id") messageId: String,
        @Body request: ForwardMessageRequest,
    ): Response<ForwardMessageResponse>

    @POST("messages/conversations/{id}/mute")
    suspend fun muteConversation(@Path("id") conversationId: String): Response<MuteConversationResponse>

    @GET("messages/conversations/{id}/search")
    suspend fun searchInConversation(
        @Path("id") conversationId: String,
        @Query("q") query: String,
        @Query("offset") offset: Int,
    ): Response<MessageSearchResponse>

    @GET("messages/search")
    suspend fun searchAllMessages(
        @Query("q") query: String,
        @Query("offset") offset: Int,
    ): Response<MessageSearchResponse>

    // ---- Grup sesli/görüntülü arama (native görev — LiveKit) — app/api_v1/
    // messaging.py api_call_token() (commit 5a03122) ile birebir eşleşir.
    // 503 {"error":"group_calls_not_configured"} LiveKit env var'ları henüz
    // ayarlanmamışsa, 404 {"error":"not_found"} katılımcı değilse VEYA
    // konuşma grup değilse (bkz. CallViewModel/CallScreen).
    @POST("messages/conversations/{id}/call-token")
    suspend fun getCallToken(@Path("id") conversationId: String): Response<CallTokenResponse>
}
