package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Yeniden paylaşma (repost) — app/api_v1/reposts.py api_repost_post() ile
 * birebir eşleşir (backend AYRI bir ajan turunda zaten yazıldı/test edildi/
 * push'landı, bu SADECE tüketici taraf). MutesApi/BookmarksApi ile AYNI
 * desen: gövdeli tek POST.
 */
interface RepostsApi {
    @POST("posts/{postId}/repost")
    suspend fun repostPost(
        @Path("postId") postId: String,
        @Body body: RepostRequest = RepostRequest(),
    ): Response<RepostResponse>
}
