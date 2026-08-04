package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.GifDto
import com.umuterayaltay.sosyal.nativeapp.network.GifsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * `Disabled` bir HATA DEĞİL — backend'de KLIPY_API_KEY tanımlı değilse
 * ({"gifs": [], "disabled": true} döner, bkz. api_gif_search() docstring'i).
 * Bilinçli olarak [Error]'dan AYRI bir dal: çağıran taraf (MediaPickerSheet)
 * bunu "GIF sekmesini gizle/devre dışı göster" olarak yorumlamalı, "arama
 * başarısız oldu, tekrar dene" değil.
 */
sealed class GifSearchResult {
    data class Success(val gifs: List<GifDto>) : GifSearchResult()
    data object Disabled : GifSearchResult()
    data class Error(val code: String?) : GifSearchResult()
}

/**
 * GIF arama/paylaşma — app/api_v1/gifs.py api_gif_search() (Klipy proxy) için
 * repository (Faz 5 Dalga 3B). StickersRepository/HashtagRepository ile AYNI
 * gerekçeyle Room cache YOK (arama sonuçları canlı/geçici, kalıcılık gereksiz).
 */
class GifsRepository(
    private val gifsApi: GifsApi,
) {
    suspend fun search(query: String): GifSearchResult = withContext(Dispatchers.IO) {
        try {
            val response = gifsApi.searchGifs(query)
            val body = response.body()
            when {
                response.isSuccessful && body?.disabled == true -> GifSearchResult.Disabled
                response.isSuccessful && body != null && body.error == null ->
                    GifSearchResult.Success(body.gifs ?: emptyList())
                else -> GifSearchResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            GifSearchResult.Error("network_error")
        } catch (e: Exception) {
            GifSearchResult.Error("unknown_error")
        }
    }
}
