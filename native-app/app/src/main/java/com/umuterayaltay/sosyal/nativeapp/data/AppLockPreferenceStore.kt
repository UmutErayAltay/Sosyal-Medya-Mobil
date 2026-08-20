package com.umuterayaltay.sosyal.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Uygulama kilidi (2026-08-21) tercihi — ThemePreferenceStore'la AYNI desen
 * (düz SharedPreferences yeterli, EncryptedSharedPreferences GEREKMEZ: burada
 * saklanan tek şey bir açık/kapalı bayrak, TokenStore'daki gibi hassas bir
 * SIR değil). Varsayılan KAPALI — kullanıcı Ayarlar'dan BİLEREK açar (mevcut
 * kullanıcıların davranışı sessizce değişmesin diye).
 *
 * [enabled] bir StateFlow — MainActivity'nin `setContent` bloğu, tema
 * tercihiyle AYNI şekilde, Ayarlar'da değiştirilince ANINDA (uygulama
 * yeniden başlatılmadan) tepki verir.
 */
class AppLockPreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    companion object {
        private const val PREFS_NAME = "sosyal_native_applock_prefs"
        private const val KEY_ENABLED = "applock_enabled"
    }
}
