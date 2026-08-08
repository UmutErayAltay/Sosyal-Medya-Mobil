package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.StickerDto
import com.umuterayaltay.sosyal.nativeapp.network.StickersApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// storage_helper.py ALLOWED_EXTENSIONS/ALLOWED_MIMES ile AYNI eşleme —
// ConversationViewModel.kt'deki AYNI sabitin (mesaj görseli) bir kopyası,
// ayrı bir "ortak yardımcı" dosyası İCAT EDİLMEDİ (sadece 2 kullanım yeri var).
private val IMAGE_MIME_TO_EXTENSION = mapOf(
    "image/png" to ".png",
    "image/jpeg" to ".jpg",
    "image/gif" to ".gif",
    "image/webp" to ".webp",
)

sealed class StickersResult {
    data class Success(val stickers: List<StickerDto>) : StickersResult()
    data class Error(val code: String?) : StickersResult()
}

sealed class StickerDetailResult {
    data class Success(val sticker: StickerDto) : StickerDetailResult()
    data class Error(val code: String?) : StickerDetailResult()
}

sealed class CreateStickerResult {
    data class Success(val id: String, val imageUrl: String) : CreateStickerResult()
    data class Error(val code: String?) : CreateStickerResult()
}

/** save/remove — MessagingRepository.MarkReadResult ile AYNI ok/error deseni. */
sealed class SaveStickerResult {
    data object Success : SaveStickerResult()
    data class Error(val code: String?) : SaveStickerResult()
}

sealed class RemoveStickerResult {
    data object Success : RemoveStickerResult()
    data class Error(val code: String?) : RemoveStickerResult()
}

/**
 * Sticker backend'i (app/api_v1/stickers.py, Faz 5 Dalga 1C) için repository —
 * HashtagRepository/MessagingRepository ile AYNI gerekçeyle Room cache YOK. Bu
 * turda UI/tüketici YOK — sadece network+repository katmanı, Dalga 3'te başka
 * bir ajan mesaj/yorum'a sticker seçici ekleyecek.
 */
class StickersRepository(
    private val stickersApi: StickersApi,
) {

    suspend fun getMyStickers(): StickersResult = withContext(Dispatchers.IO) {
        try {
            val response = stickersApi.getMyStickers()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                StickersResult.Success(body.stickers ?: emptyList())
            } else {
                StickersResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            StickersResult.Error("network_error")
        } catch (e: Exception) {
            StickersResult.Error("unknown_error")
        }
    }

    // GET /stickers/{id} yanıtı SARMALANMAMIŞ (doğrudan sticker satırı) — hata
    // durumunda backend {"error":"not_found"} (404) veya {"error":"stickers_not_available"}
    // (503) döner, o gövde de AYNI StickerDto tipine parse edilmeye ÇALIŞILIR ama
    // id/imageUrl alanları olmadığından Gson null bırakır — bu yüzden id boş
    // gelirse de hata olarak ele alınır.
    suspend fun getSticker(id: String): StickerDetailResult = withContext(Dispatchers.IO) {
        try {
            val response = stickersApi.getSticker(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                StickerDetailResult.Success(body)
            } else {
                StickerDetailResult.Error(RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            StickerDetailResult.Error("network_error")
        } catch (e: Exception) {
            StickerDetailResult.Error("unknown_error")
        }
    }

    suspend fun createSticker(imageBytes: ByteArray, imageMimeType: String?): CreateStickerResult =
        withContext(Dispatchers.IO) {
            try {
                val imageBody = imageBytes.toRequestBody((imageMimeType ?: "image/jpeg").toMediaTypeOrNull())
                // 2026-08-08 — MessagingRepository.sendMessage()'daki AYNI kök
                // neden/düzeltme: sabit "sticker_image" (UZANTISIZ) dosya adı
                // backend'in _get_extension() kontrolünü HER ZAMAN başarısız
                // kılıyordu (upload_failed). Gerçek MIME'dan gerçek uzantı üretilir.
                val ext = IMAGE_MIME_TO_EXTENSION[imageMimeType] ?: ".jpg"
                val imagePart = MultipartBody.Part.createFormData("image", "upload$ext", imageBody)
                val response = stickersApi.createSticker(imagePart)
                val body = response.body()
                if (response.isSuccessful && body?.id != null && body.imageUrl != null) {
                    CreateStickerResult.Success(body.id, body.imageUrl)
                } else {
                    CreateStickerResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                CreateStickerResult.Error("network_error")
            } catch (e: Exception) {
                CreateStickerResult.Error("unknown_error")
            }
        }

    suspend fun saveSticker(id: String): SaveStickerResult = withContext(Dispatchers.IO) {
        try {
            val response = stickersApi.saveSticker(id)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                SaveStickerResult.Success
            } else {
                SaveStickerResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            SaveStickerResult.Error("network_error")
        } catch (e: Exception) {
            SaveStickerResult.Error("unknown_error")
        }
    }

    // Backend save/remove/{id} her zaman {"ok":true} döner (migration yoksa da)
    // — ama ağ/sunucu seviyeli hatalar (network_error/unknown_error) yine de
    // ayırt edilir.
    suspend fun removeSticker(id: String): RemoveStickerResult = withContext(Dispatchers.IO) {
        try {
            val response = stickersApi.removeSticker(id)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                RemoveStickerResult.Success
            } else {
                RemoveStickerResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            RemoveStickerResult.Error("network_error")
        } catch (e: Exception) {
            RemoveStickerResult.Error("unknown_error")
        }
    }
}
