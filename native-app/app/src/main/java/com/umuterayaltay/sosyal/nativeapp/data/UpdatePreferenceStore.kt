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

    // Delta güncelleme planı: kurulu APK'nın SHA-256'sı otorite kimlik
    // (updated_at sadece ucuz ön-kontrol olarak kalıyor, yukarıda). Hesaplama
    // ~1 sn sürdüğü için dosya boyutu/mtime değişmediyse ÖNBELLEKTEN okunur.
    fun getInstalledApkCache(): Triple<String, Long, Long>? {
        val sha = prefs.getString(KEY_INSTALLED_SHA, null) ?: return null
        val len = prefs.getLong(KEY_INSTALLED_LEN, -1L)
        val mtime = prefs.getLong(KEY_INSTALLED_MTIME, -1L)
        if (len < 0 || mtime < 0) return null
        return Triple(sha, len, mtime)
    }

    fun setInstalledApkCache(sha256: String, length: Long, mtime: Long) {
        prefs.edit()
            .putString(KEY_INSTALLED_SHA, sha256)
            .putLong(KEY_INSTALLED_LEN, length)
            .putLong(KEY_INSTALLED_MTIME, mtime)
            .apply()
    }

    /** Teşhis amaçlı — "ok" / "sha_mismatch" / "no_base" / "oom" / hata
     * mesajı. Kullanıcıya gösterilmez, sadece hata ayıklamaya yardımcı. */
    fun setLastDeltaOutcome(value: String) {
        prefs.edit().putString(KEY_LAST_DELTA_OUTCOME, value).apply()
    }

    fun getLastDeltaOutcome(): String? = prefs.getString(KEY_LAST_DELTA_OUTCOME, null)

    companion object {
        private const val PREFS_NAME = "sosyal_native_update_prefs"
        private const val KEY_LAST_ASSET_UPDATED_AT = "last_asset_updated_at"
        private const val KEY_INSTALLED_SHA = "installed_apk_sha256"
        private const val KEY_INSTALLED_LEN = "installed_apk_len"
        private const val KEY_INSTALLED_MTIME = "installed_apk_mtime"
        private const val KEY_LAST_DELTA_OUTCOME = "last_delta_outcome"
    }
}
