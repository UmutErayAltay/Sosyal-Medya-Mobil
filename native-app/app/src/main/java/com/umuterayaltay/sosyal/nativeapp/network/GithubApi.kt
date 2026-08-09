package com.umuterayaltay.sosyal.nativeapp.network

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET

/**
 * GitHub Releases REST API — SADECE en son yayının APK asset'ini bulup
 * güncel mi diye karşılaştırmak için (2026-08-09, kullanıcı isteği: "her
 * seferinde elle indirip kurmak yerine uygulama içinden güncelleme").
 * Backend'in KENDİ [RetrofitClient]'ından (AuthInterceptor ile bearer
 * token EKLEYEN) BİLEREK AYRI bir Retrofit örneği kullanır — o interceptor
 * bize ait API token'ını github.com'a göndermemeli (bkz. RetrofitClient.
 * createGithub() yorumu). Public repo + kimliksiz istek: rate limit
 * 60/saat/IP, bu ekran sadece kullanıcı elle "Kontrol Et"e basınca
 * çağrıldığı için fazlasıyla yeterli.
 */
interface GithubApi {
    @GET("repos/UmutErayAltay/Sosyal-Medya-Mobil/releases/latest")
    suspend fun getLatestRelease(): Response<GithubReleaseDto>
}

data class GithubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    val assets: List<GithubReleaseAssetDto>,
)

data class GithubReleaseAssetDto(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    // ISO-8601 — asset'in EN SON ne zaman değiştirildiğini (--clobber ile
    // yeniden yüklendiğini) gösterir. Bu proje versionCode/versionName'i
    // HER APK güncellemesinde BÜMEDİĞİ için (aynı "0.1.0" ile aylarca
    // yeniden yüklendi, bkz. .context/active_context.md) sürüm KARŞILAŞTIRMASI
    // BUNUN üzerinden yapılıyor — UpdatePreferenceStore'daki AYNI alan.
    @SerializedName("updated_at") val updatedAt: String,
    val size: Long,
)
