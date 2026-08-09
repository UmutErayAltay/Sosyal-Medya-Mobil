package com.umuterayaltay.sosyal.nativeapp.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Şu ana kadar İNDİRİLEN/kurulan GitHub Release asset'inin `updated_at`
 * damgasını saklar (2026-08-09, uygulama içi güncelleme) — ThemePreferenceStore
 * ile AYNI düz SharedPreferences deseni (hassas veri değil). Bu proje
 * versionCode/versionName'i HER APK yeniden yüklemesinde BÜMEDİĞİ için
 * (bkz. GithubApi.kt yorumu) sürüm karşılaştırması BUNUN üzerinden yapılıyor.
 *
 * İLK ÇALIŞTIRMADA (hiç kayıt yokken) `null` döner — [UpdateRepository]
 * bunu "henüz hiç kontrol edilmedi" olarak yorumlar, YANLIŞLIKLA "her zaman
 * güncelleme var" DEMEZ (ilk kontrolde gelen değer doğrudan GÜNCEL kabul
 * edilip kaydedilir, kullanıcıya HEMEN bir güncelleme dayatılmaz).
 */
class UpdatePreferenceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastKnownAssetUpdatedAt(): String? = prefs.getString(KEY_LAST_ASSET_UPDATED_AT, null)

    fun setLastKnownAssetUpdatedAt(value: String) {
        prefs.edit().putString(KEY_LAST_ASSET_UPDATED_AT, value).apply()
    }

    companion object {
        private const val PREFS_NAME = "sosyal_native_update_prefs"
        private const val KEY_LAST_ASSET_UPDATED_AT = "last_asset_updated_at"
    }
}
