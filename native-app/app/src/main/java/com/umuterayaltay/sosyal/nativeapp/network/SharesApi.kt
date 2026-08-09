package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Post paylaşımı (DM'e gönder) — app/api_v1/messaging.py api_share_post()
 * ile birebir eşleşir. BİLİNÇLİ SAPMA (görev tanımından): backend yorumu
 * "GET /messages/forward-targets zaten REUSE edilebilir" diyor ama o uç
 * nokta KONUŞMA id'si döndürüyor (_build_convos() -> c["id"] = conversations.id),
 * burası ise GERÇEK profil id'si (user_ids) bekliyor
 * (_get_or_create_conversation(me, target_id) target_id'yi bir KULLANICI
 * id'si olarak sorguluyor). Konuşma id'sini user_id yerine göndermek yanlış/
 * var olmayan bir alıcıya post paylaşmaya çalışırdı (conversation_participants
 * FK ihlali veya sessizce yanlış hedef) — bu yüzden hedef seçimi burada
 * DiscoverApi'nin kullanıcı aramasından (gerçek profil id'leri) yapılır,
 * bkz. PostShareSheet.kt.
 */
interface SharesApi {
    @POST("posts/{postId}/share")
    suspend fun sharePost(
        @Path("postId") postId: String,
        @Body body: SharePostRequest,
    ): Response<SharePostResponse>

    // 2026-08-09 (kullanıcı isteği: "gönderme kısmında en çok konuştuğun,
    // son zamanlarda çok konuştuğun gibi profiller gözüksün") — web'in
    // AYNISI (app/messaging/creation.py::share_targets()), api_v1/messaging.py'e
    // birebir mirror'landı: q boşsa takip edilenler listesi (varsayılan
    // öneri), q 2+ karakterse username araması — düz JSON dizisi döner.
    @GET("messages/share-targets")
    suspend fun getShareTargets(@Query("q") q: String? = null): Response<List<UserSearchDto>>
}
