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

object RetrofitClient {

    private val gson = Gson()

    fun create(tokenStore: TokenStore): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
