package com.umuterayaltay.sosyal.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Bearer token'ı diskte şifreli saklar (EncryptedSharedPreferences).
 *
 * AuthInterceptor bu sınıfı senkron (getToken()) okur — SharedPreferences
 * okuması zaten hızlı/blocking olduğu için ayrı bir coroutine/suspend fonksiyon
 * gerekmiyor (ağ isteği interceptor zaten arkaplan thread'inde çalışır).
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "sosyal_native_secure_prefs"
        private const val KEY_TOKEN = "auth_token"
    }
}
