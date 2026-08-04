package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * FCM push token kaydı (Faz 5, Dalga 4A) — app/api_v1/push.py
 * api_push_register()/api_push_unregister() ile birebir eşleşir (backend
 * PARALEL bir ajan tarafından yazıldı, bkz. görev sözleşmesi). MutesApi ile
 * AYNI desen: gövdeli POST, yanıt {"ok"} şekli (SimpleOkResponse reuse edilir).
 */
interface PushApi {
    @POST("push/register")
    suspend fun register(@Body body: RegisterTokenRequest): Response<SimpleOkResponse>

    @POST("push/unregister")
    suspend fun unregister(@Body body: RegisterTokenRequest): Response<SimpleOkResponse>
}
