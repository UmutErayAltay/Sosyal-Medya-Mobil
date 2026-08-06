package com.umuterayaltay.sosyal.nativeapp.repository

import com.google.firebase.messaging.FirebaseMessaging
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.ForgotPasswordRequest
import com.umuterayaltay.sosyal.nativeapp.network.GoogleLoginRequest
import com.umuterayaltay.sosyal.nativeapp.network.LoginRequest
import com.umuterayaltay.sosyal.nativeapp.network.LoginResponse
import com.umuterayaltay.sosyal.nativeapp.network.RegisterRequest
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Login sonucu — backend'in döndüğü hata kodlarını (bkz. app/api_v1.py) ayırt eder. */
sealed class AuthResult {
    data class Success(val user: UserDto) : AuthResult()
    data class Error(val code: String?, val message: String?) : AuthResult()
}

/** GET /realtime-token sonucu — RealtimeConnectionManager bunu tüketir.
 * error HER ZAMAN (503 unavailable / 401 relogin_required / network_error/
 * unknown_error) çağıran tarafın SESSİZCE polling'e düşmesi gereken bir
 * sinyaldir (bkz. RealtimeTokenResponse yorumu, ApiModels.kt). */
sealed class RealtimeTokenResult {
    data class Success(
        val accessToken: String,
        val supabaseUrl: String,
        val supabasePublishableKey: String,
    ) : RealtimeTokenResult()
    data class Error(val code: String?) : RealtimeTokenResult()
}

/** POST /auth/forgot-password sonucu. Backend HER DURUMDA {"ok":true} döner
 * (kullanıcı-enumeration koruması) — bu yüzden Success burada SADECE "istek
 * ağ/sunucu seviyesinde başarıyla ulaştı" anlamına gelir, "e-posta var mıydı"
 * bilgisini TAŞIMAZ (bkz. ForgotPasswordRequest yorumu, ApiModels.kt). */
sealed class ForgotPasswordResult {
    data object Success : ForgotPasswordResult()
    data class Error(val code: String?) : ForgotPasswordResult()
}

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {

    fun isLoggedIn(): Boolean = !tokenStore.getToken().isNullOrBlank()

    /** [code] opsiyonel — 2FA aktif hesapta ilk denemede backend 403 mfa_required
     * döner (bkz. AuthViewModel.NeedsCode); çağıran taraf AYNI email+password'ü
     * code eklenmiş halde tekrar gönderir. */
    suspend fun login(email: String, password: String, deviceName: String?, code: String? = null): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.login(
                    LoginRequest(
                        email = email,
                        password = password,
                        deviceName = deviceName,
                        code = code,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    suspend fun register(email: String, password: String, username: String, deviceName: String?): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        username = username,
                        deviceName = deviceName,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    /** [idToken] Android Credential Manager'ın ürettiği Google ID token'ı (bkz.
     * GoogleSignInHelper.kt). [code] login()'deki AYNI 2FA-retry deseni —
     * mfa_required dönerse çağıran taraf AYNI idToken'ı code eklenmiş halde
     * tekrar gönderir. [nonce] bu iterasyonda BİLİNÇLİ olarak kullanılmıyor
     * (kapsam dışı: replay-koruması). */
    suspend fun loginWithGoogle(idToken: String, nonce: String?, code: String?, deviceName: String?): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val response = authApi.googleLogin(
                    GoogleLoginRequest(
                        idToken = idToken,
                        nonce = nonce,
                        code = code,
                        deviceName = deviceName,
                    )
                )
                parseAuthResponse(response)
            } catch (e: IOException) {
                AuthResult.Error("network_error", e.message)
            } catch (e: Exception) {
                AuthResult.Error("unknown_error", e.message)
            }
        }

    /** login()/register()/googleLogin() ÜÇÜ de AYNI {token,user}/{error,message}
     * şeklini döner (LoginResponse reuse edilir) — parse mantığı TEK yerde. */
    private fun parseAuthResponse(response: Response<LoginResponse>): AuthResult {
        val body = response.body()
        return if (response.isSuccessful && body?.token != null && body.user != null) {
            tokenStore.saveToken(body.token)
            AuthResult.Success(body.user)
        } else {
            val code = body?.error ?: RetrofitClient.parseErrorCode(response)
            AuthResult.Error(code, body?.message)
        }
    }

    /** ProfileViewModel'in username=null (kendi profilim) durumunda kullaniciyi
     * cozmesi icin - AuthApi.me()'yi sarar, hata/401 durumunda null doner
     * (cagiran taraf null'i "oturum gecersiz" olarak yorumlar). */
    suspend fun getCurrentUser(): UserDto? = withContext(Dispatchers.IO) {
        try {
            val response = authApi.me()
            if (response.isSuccessful) response.body()?.user else null
        } catch (e: Exception) {
            null
        }
    }

    /** Native Realtime bağlantısı için taze bir Supabase Auth access token'ı
     * ister (bkz. RealtimeConnectionManager, app/api_v1.py api_realtime_token()
     * docstring'i — "Realtime hiçbir zaman ÇEKİRDEK bir özellik değildir").
     * Buradaki HER hata çağıran tarafın SESSİZCE polling'e düşmesi gereken bir
     * sinyaldir — ana bearer token'a (bu repository'nin diğer metotlarının
     * kullandığı, TokenStore'daki) HİÇ dokunulmaz.
     *
     * [force]: çağıran taraf (bkz. CallSignalingManager.connectOnce) art arda
     * subscribe/CHANNEL_ERROR başarısızlığı gördüğünde true geçer — backend'in
     * normal SAAT bazlı yenileme kısayolunu atlatıp Supabase'den GERÇEKTEN
     * taze bir token ister (bir JWT imza anahtarı rotasyonu token'ı saat
     * açısından hâlâ geçerliyken anlık geçersiz kılabilir, aksi halde sistem
     * bozuk token'ı süresi dolana kadar sonsuza kadar sunardı). */
    suspend fun getRealtimeToken(force: Boolean = false): RealtimeTokenResult = withContext(Dispatchers.IO) {
        try {
            val response = authApi.getRealtimeToken(force = if (force) 1 else null)
            val body = response.body()
            val accessToken = body?.accessToken
            val supabaseUrl = body?.supabaseUrl
            val supabaseKey = body?.supabasePublishableKey
            if (response.isSuccessful && accessToken != null && supabaseUrl != null && supabaseKey != null) {
                RealtimeTokenResult.Success(accessToken, supabaseUrl, supabaseKey)
            } else {
                RealtimeTokenResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            RealtimeTokenResult.Error("network_error")
        } catch (e: Exception) {
            RealtimeTokenResult.Error("unknown_error")
        }
    }

    /** [email] enumeration koruması backend'de zaten var — bu yüzden burada
     * "e-posta bulunamadı" gibi bir dal YOK, sadece ağ/sunucu hatası ayrımı var
     * (rate_limited/network_error/unknown_error). Backend başarılı yanıtta
     * HER ZAMAN {"ok":true} döndüğü için ok==true DIŞINDA bir gövde gelirse de
     * güvenli tarafta kalınıp Error("unknown_error") döndürülür. */
    suspend fun forgotPassword(email: String): ForgotPasswordResult = withContext(Dispatchers.IO) {
        try {
            val response = authApi.forgotPassword(ForgotPasswordRequest(email = email))
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                ForgotPasswordResult.Success
            } else {
                ForgotPasswordResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ForgotPasswordResult.Error("network_error")
        } catch (e: Exception) {
            ForgotPasswordResult.Error("unknown_error")
        }
    }

    /** Sadece bu cihazın token'ını iptal eder — API çağrısı başarısız olsa bile
     * yerel token her zaman temizlenir (kullanıcı cihazda "çıkış yapmış" görünmeli). */
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            try {
                // FCM token'ını temizlemeden ÖNCE (Faz 5 Dalga 4A) sunucuya "bu
                // token artık bu kullanıcıya ait değil" bilgisi iletiliyor —
                // aksi halde çıkış yapan kullanıcı bu cihazda kalan eski bir
                // FCM token üzerinden başka birinin push bildirimini almaya
                // devam edebilirdi. Hata olursa logout akışı KESİNTİYE
                // UĞRAMAZ (bkz. ServiceLocator.pushRepository.unregisterToken
                // zaten kendi try/catch'inde network/unknown hatasını yutar,
                // burada da ayrıca sarmalanır — FirebaseMessaging.getInstance()
                // .token'ın kendisi de başarısız olabilir).
                try {
                    val fcmToken = currentFcmToken()
                    if (fcmToken != null) {
                        ServiceLocator.pushRepository.unregisterToken(fcmToken)
                    }
                } catch (e: Exception) {
                    // Token alınamadı/sunucuya ulaşılamadı — logout'u ENGELLEMEZ.
                }
                authApi.logout()
            } catch (e: Exception) {
                // Ağ hatası olsa bile yerelde çıkış yapılır — sunucu tarafında token
                // zaten süresi dolana/başka bir yolla iptal edilene kadar geçerli kalabilir,
                // ama bu MVP'de kabul edilebilir (spesifikasyon: proaktif doğrulama yok).
            } finally {
                tokenStore.clearToken()
            }
        }
    }

    /** FirebaseMessaging.getInstance().token bir Play Services Task<String>
     * döner — projede kotlinx-coroutines-play-services BAĞIMLILIĞI YOK (bkz.
     * app/build.gradle.kts, sadece coroutines-android var), bu yüzden Task'ın
     * .await() uzantısı KULLANILAMAZ. Aynı callback->suspend dönüşümü elle
     * (suspendCancellableCoroutine ile) yapılıyor — yeni bir bağımlılık
     * EKLEMEDEN. */
    private suspend fun currentFcmToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> continuation.resume(token) }
            .addOnFailureListener { e -> continuation.resumeWithException(e) }
    }
}
