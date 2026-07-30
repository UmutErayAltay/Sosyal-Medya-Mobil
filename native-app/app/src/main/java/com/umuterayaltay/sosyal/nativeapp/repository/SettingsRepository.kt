package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.AddCloseFriendRequest
import com.umuterayaltay.sosyal.nativeapp.network.DeactivateAccountRequest
import com.umuterayaltay.sosyal.nativeapp.network.NotificationPreferencesDto
import com.umuterayaltay.sosyal.nativeapp.network.ProfileDto
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SettingsApi
import com.umuterayaltay.sosyal.nativeapp.network.SimpleOkResponse
import com.umuterayaltay.sosyal.nativeapp.network.UserSearchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException

sealed class EditProfileResult {
    data class Success(val profile: ProfileDto) : EditProfileResult()
    data class Error(val code: String?) : EditProfileResult()
}

sealed class NotificationPreferencesResult {
    data class Success(val preferences: NotificationPreferencesDto) : NotificationPreferencesResult()
    data class Error(val code: String?) : NotificationPreferencesResult()
}

sealed class CloseFriendsResult {
    data class Success(val users: List<UserSearchDto>) : CloseFriendsResult()
    data class Error(val code: String?) : CloseFriendsResult()
}

/** ok/error şekilli ikincil eylemler (bildirim tercihi kaydet, yakın arkadaş
 * ekle/çıkar) için ortak sonuç — DiscoverRepository.SearchActionResult /
 * ProfileRepository.FollowActionResult ile AYNI desen. */
sealed class SimpleActionResult {
    data object Success : SimpleActionResult()
    data class Error(val code: String?) : SimpleActionResult()
}

sealed class DeactivateResult {
    data object Success : DeactivateResult()
    data class Error(val code: String?) : DeactivateResult()
}

/**
 * Profil ayarları (profil düzenleme, bildirim tercihleri, yakın arkadaşlar,
 * hesap deaktivasyonu) için repository — DiscoverRepository/ProfileRepository/
 * InteractionsRepository ile AYNI hata yönetimi deseni (IOException ->
 * network_error, diğer Exception -> unknown_error, backend {error} varsa onu
 * kullan). Room cache YOK — AYNI bilinçli kapsam kararı (canlı ayar verisi,
 * offline-first ihtiyacı yok).
 */
class SettingsRepository(
    private val settingsApi: SettingsApi,
) {

    /** [avatarBytes] null ise avatar hiç değiştirilmez (backend request.files.get
     * ("avatar") None döner, avatar_url mevcut değerinde kalır) — InteractionsRepository.
     * createPost()'taki AYNI "null -> parçayı hiç ekleme" deseni. */
    suspend fun editProfile(
        fullName: String,
        bio: String,
        username: String,
        isPrivate: Boolean,
        hideLastSeen: Boolean,
        avatarBytes: ByteArray?,
        avatarMimeType: String?,
    ): EditProfileResult = withContext(Dispatchers.IO) {
        try {
            fun text(value: String): RequestBody = value.toRequestBody("text/plain".toMediaTypeOrNull())
            val avatarPart: MultipartBody.Part? = avatarBytes?.let { bytes ->
                val avatarBody = bytes.toRequestBody(avatarMimeType?.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("avatar", "avatar.jpg", avatarBody)
            }

            val response = settingsApi.editProfile(
                fullName = text(fullName),
                bio = text(bio),
                username = text(username),
                // _bool_form_field() backend'de SADECE "true" string'ini true sayar,
                // her başka değer (eksik dahil) false — burada her zaman açık bir
                // "true"/"false" gönderilir (fail-closed'a bilerek uyulur).
                isPrivate = text(if (isPrivate) "true" else "false"),
                hideLastSeen = text(if (hideLastSeen) "true" else "false"),
                avatar = avatarPart,
            )
            val body = response.body()
            val profile = body?.profile
            if (response.isSuccessful && body?.ok == true && profile != null) {
                EditProfileResult.Success(profile)
            } else {
                EditProfileResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            EditProfileResult.Error("network_error")
        } catch (e: Exception) {
            EditProfileResult.Error("unknown_error")
        }
    }

    suspend fun getNotificationPreferences(): NotificationPreferencesResult = withContext(Dispatchers.IO) {
        try {
            val response = settingsApi.getNotificationPreferences()
            val body = response.body()
            val prefs = body?.preferences
            if (response.isSuccessful && body?.error == null && prefs != null) {
                NotificationPreferencesResult.Success(prefs)
            } else {
                NotificationPreferencesResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            NotificationPreferencesResult.Error("network_error")
        } catch (e: Exception) {
            NotificationPreferencesResult.Error("unknown_error")
        }
    }

    /** Backend per-field DEĞİL, TAM seti tek istekte kabul ediyor — bu yüzden
     * her "kaydet" tıklamasında güncel yerel state'in TAMAMI gönderilir. */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferencesDto): SimpleActionResult =
        runAction { settingsApi.updateNotificationPreferences(preferences) }

    suspend fun getCloseFriends(): CloseFriendsResult = withContext(Dispatchers.IO) {
        try {
            val response = settingsApi.getCloseFriends()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                CloseFriendsResult.Success(body.users ?: emptyList())
            } else {
                CloseFriendsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            CloseFriendsResult.Error("network_error")
        } catch (e: Exception) {
            CloseFriendsResult.Error("unknown_error")
        }
    }

    suspend fun addCloseFriend(userId: String): SimpleActionResult =
        runAction { settingsApi.addCloseFriend(AddCloseFriendRequest(userId)) }

    /** Backend idempotent (satır yoksa da ok=true döner) — burada ayrıca bir
     * ön-kontrol YAPILMIYOR, tek doğruluk kaynağı backend. */
    suspend fun removeCloseFriend(userId: String): SimpleActionResult =
        runAction { settingsApi.removeCloseFriend(userId) }

    /**
     * Hesabı deaktive eder. BAŞARILI olursa bu isteği taşıyan Bearer token'ın
     * KENDİSİ sunucu tarafında iptal edilir (bkz. app/api_v1.py
     * api_deactivate_account() docstring'i, logout()'daki AYNI desen) — bu
     * yüzden çağıran taraf (DeactivateAccount akışını yöneten ViewModel)
     * Success dönünce MUTLAKA TokenStore.clearToken() çağırmalı, aksi halde
     * artık sunucuda geçersiz olan bir token'la sonraki her istek 401 döner.
     * Repository burada BİLEREK tokenStore'a dokunmuyor (diğer repository'ler
     * de aynı sorumluluk ayrımını izliyor: token temizleme her zaman
     * ViewModel/çağıran katmanın işi, bkz. CreatePostViewModel/ProfileViewModel).
     */
    suspend fun deactivateAccount(password: String?): DeactivateResult = withContext(Dispatchers.IO) {
        try {
            val response = settingsApi.deactivateAccount(DeactivateAccountRequest(password))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                DeactivateResult.Success
            } else {
                DeactivateResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            DeactivateResult.Error("network_error")
        } catch (e: Exception) {
            DeactivateResult.Error("unknown_error")
        }
    }

    /** DiscoverRepository.runAction ile AYNI desen — ok/error şekilli tüm
     * ikincil eylem endpoint'leri için tek ortak yol. */
    private suspend fun runAction(call: suspend () -> Response<SimpleOkResponse>): SimpleActionResult =
        withContext(Dispatchers.IO) {
            try {
                val response = call()
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    SimpleActionResult.Success
                } else {
                    SimpleActionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                SimpleActionResult.Error("network_error")
            } catch (e: Exception) {
                SimpleActionResult.Error("unknown_error")
            }
        }
}
