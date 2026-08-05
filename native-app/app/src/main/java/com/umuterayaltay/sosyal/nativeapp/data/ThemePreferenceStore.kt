package com.umuterayaltay.sosyal.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Kullanıcının aydınlık/koyu tema tercihi — SYSTEM varsayılan (mevcut
 * "OS temasını takip et" davranışını bozmamak için). */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

/**
 * Kullanıcının aydınlık/koyu/sistem tema tercihini diskte saklar.
 *
 * TokenStore'un aksine (bkz. TokenStore.kt yorumu) burada EncryptedSharedPreferences
 * KULLANILMIYOR — tema tercihi hassas veri değil, düz SharedPreferences yeterli.
 *
 * [themeMode] bir StateFlow olarak expose edilir ki MainActivity tema
 * değişimini UYGULAMA YENİDEN BAŞLATILMADAN, anında yakalayabilsin (bkz.
 * MainActivity.setContent { SosyalNativeTheme(...) }).
 */
class ThemePreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "sosyal_native_theme_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
