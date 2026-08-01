package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Anket oy verme — app/api_v1/polls.py api_vote_poll() ile birebir eşleşir
 * (Faz 5, Dalga 1A). Tek uç: oy ver / oyu değiştir / oyu geri al AYNI çağrıdır
 * (backend 3-yönlü toggle uygular, client'ın hangi durumda olduğunu bilmesi
 * gerekmez). Yanıtta dönen options/total_votes/my_vote GÜNCEL durumdur —
 * client yüzdeleri kendisi hesaplamaz.
 *
 * Okuma ucu YOK: anket verisi zaten her post JSON'unda `poll` alanı olarak
 * geliyor (attach_polls), ayrı bir GET İCAT EDİLMEDİ.
 */
interface PollsApi {
    @POST("polls/{pollId}/vote")
    suspend fun votePoll(
        @Path("pollId") pollId: String,
        @Body body: VotePollRequest,
    ): Response<VotePollResponse>
}
