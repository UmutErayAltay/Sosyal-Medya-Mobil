package com.umuterayaltay.sosyal.nativeapp.network

import com.google.gson.Gson
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://sosyalmedyadeneme.onrender.com/api/v1/"
private const val GITHUB_BASE_URL = "https://api.github.com/"

object RetrofitClient {

    private val gson = Gson()

    /**
     * GitHub Releases API için AYRI bir Retrofit örneği (2026-08-09, uygulama
     * içi güncelleme kontrolü) — [create]'in DÖNDÜRDÜĞÜ istemcinin AKSİNE
     * `AuthInterceptor` YOK. Bu BİLİNÇLİ: `create()`'in interceptor'ı OUR
     * OWN backend'e (sosyalmedyadeneme.onrender.com) giden HER isteğe kendi
     * API bearer token'ımızı ekliyor — aynı Retrofit/OkHttpClient'ı
     * github.com'a karşı reuse etmek, o token'ı ÜÇÜNCÜ TARAF bir domaine
     * SIZDIRIRDI (gerçek bir güvenlik hatası olurdu, sadece teorik değil).
     */
    fun createGithub(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    fun create(tokenStore: TokenStore): Retrofit {
        // Level.BODY DEĞİL: login isteğinin gövdesi şifreyi düz metin içeriyor
        // (bir test sırasında gerçek bir şifrenin Logcat'e yazılıp oradan
        // kopyalandığı görüldü) — HEADERS, gövdeyi hiç loglamadan yöntem/URL/
        // header/durum kodu gibi hata ayıklama için yeterli bilgiyi verir.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // 30sn'den 60sn'e çıkarıldı — CreatePostViewModel artık 25MB'a
            // kadar video yükleyebiliyor (bkz. api_create_post() video/is_reel
            // desteği), yavaş bir bağlantıda 30sn tüm gövdeyi yazmaya
            // yetmeyebilirdi. SADECE büyütüldü, başka istemci genel timeout'u
            // küçültülmedi (diğer isteklerde 60sn hâlâ makul bir üst sınır).
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * Backend hata gövdesi ({"error": "...", "message": "..."}) 2xx olmayan
     * yanıtlarda Retrofit/Gson tarafından OTOMATİK parse edilmez (converter
     * sadece başarılı response body'yi çevirir) — errorBody() ham string'ini
     * burada manuel parse ediyoruz.
     */
    fun <T> parseErrorCode(response: Response<T>): String? {
        val raw = response.errorBody()?.string() ?: return null
        return try {
            gson.fromJson(raw, ErrorBodyDto::class.java)?.error
        } catch (e: Exception) {
            null
        }
    }
}

data class ErrorBodyDto(val error: String?, val message: String?)
