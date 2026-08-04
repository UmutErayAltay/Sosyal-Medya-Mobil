package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.ReportRequest
import com.umuterayaltay.sosyal.nativeapp.network.ReportsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ReportResult {
    data object Success : ReportResult()
    data class Error(val code: String?) : ReportResult()
}

/**
 * Şikayet (report) — MutesRepository ile AYNI desen. `targetType` genel
 * bırakıldı (backend "post"/"comment"/"user" kabul ediyor) ama bu turda
 * native UI'dan SADECE PostActionsSheet'teki "Bildir" aksiyonu (post)
 * çağırıyor, bkz. [reportPost].
 */
class ReportsRepository(
    private val reportsApi: ReportsApi,
) {
    suspend fun report(targetType: String, targetId: String): ReportResult = withContext(Dispatchers.IO) {
        try {
            val response = reportsApi.report(ReportRequest(targetType, targetId))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                ReportResult.Success
            } else {
                ReportResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ReportResult.Error("network_error")
        } catch (e: Exception) {
            ReportResult.Error("unknown_error")
        }
    }

    suspend fun reportPost(postId: String): ReportResult = report("post", postId)
}
