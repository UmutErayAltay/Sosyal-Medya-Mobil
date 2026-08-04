package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Şikayet (report) — app/api_v1/report.py (native görev tanımı) ile birebir
 * eşleşir: `{"target_type": "post"|"comment"|"user", "target_id": "..."}`.
 * Native UI'dan bu turda SADECE "post" gönderilir (PostActionsSheet'teki
 * "Bildir" aksiyonu), interface genel bırakıldı çünkü backend genel.
 */
interface ReportsApi {
    @POST("report")
    suspend fun report(@Body body: ReportRequest): Response<SimpleOkResponse>
}
