package com.umuterayaltay.sosyal.nativeapp.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
import com.umuterayaltay.sosyal.nativeapp.data.UpdatePreferenceStore
import com.umuterayaltay.sosyal.nativeapp.network.GithubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val assetUpdatedAt: String,
    val sizeBytes: Long,
)

sealed class DownloadResult {
    data class Success(val file: File) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

/**
 * GitHub Release'ten APK indirip kurulum akışını yönetir (2026-08-09,
 * kullanıcı isteği: "her seferinde elle indirip kurmak yerine uygulama
 * içinden güncelleme"). [checkForUpdate]/[downloadApk] backend'in KENDİ
 * repository'lerinden (MessagingRepository vb.) BİLİNÇLİ olarak AYRI bir
 * desen — SendMessageResult gibi sealed class'lar burada da var ama backend
 * hata kodu SÖZLEŞMESİ (upload_failed vb.) YOK, çünkü karşı taraf bizim
 * backend'imiz DEĞİL, GitHub'ın kendi API'si.
 */
class UpdateRepository(
    private val githubApi: GithubApi,
    private val updatePreferenceStore: UpdatePreferenceStore,
) {

    /** APK asset adı — build-apk.mjs/CI'nin (ana oturum, elle) yüklediği
     * Release asset'iyle BİREBİR eşleşmeli, bkz. proje `.context/
     * active_context.md`'deki "gh release upload ... --clobber" akışı. */
    private val apkAssetName = "sosyal-medya-native-debug.apk"

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val response = githubApi.getLatestRelease()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext UpdateCheckResult.Error("GitHub'a ulaşılamadı, lütfen tekrar deneyin")
            }
            val apkAsset = body.assets.firstOrNull { it.name == apkAssetName }
                ?: return@withContext UpdateCheckResult.Error("Release'te APK dosyası bulunamadı")

            val lastKnown = updatePreferenceStore.getLastKnownAssetUpdatedAt()
            if (lastKnown == null) {
                // İLK KONTROL — bu cihaza hangi sürümün kurulu olduğunu
                // BİLMİYORUZ (kullanıcı APK'yı elle indirip kurmuş olabilir,
                // biz o anı hiç görmedik). Kullanıcıya HEMEN bir güncelleme
                // dayatmak yerine BU asset'i baseline kabul edip kaydediyoruz —
                // bir SONRAKİ kontrolde GERÇEK bir fark varsa yakalanır.
                updatePreferenceStore.setLastKnownAssetUpdatedAt(apkAsset.updatedAt)
                return@withContext UpdateCheckResult.UpToDate
            }
            if (apkAsset.updatedAt == lastKnown) {
                return@withContext UpdateCheckResult.UpToDate
            }
            UpdateCheckResult.Available(
                UpdateInfo(
                    tagName = body.tagName,
                    downloadUrl = apkAsset.downloadUrl,
                    assetUpdatedAt = apkAsset.updatedAt,
                    sizeBytes = apkAsset.size,
                ),
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error("Bağlantı hatası — internet bağlantınızı kontrol edin")
        }
    }

    /** [context.cacheDir]/updates/ altına indirir — AndroidManifest.xml'deki
     * FileProvider + res/xml/file_paths.xml'deki "updates" cache-path ile
     * AYNI dizin (bkz. o dosyaların yorumu). İndirme bitince [UpdatePreferenceStore]
     * GÜNCELLENİR (kurulumun GERÇEKTEN tamamlandığını izlemenin bir yolu YOK —
     * sistem paket yükleyicisi bizim süreç dışımızda çalışır — bu yüzden
     * "indirildi" ANI baz alınıyor, kullanıcı kurulumu ERTELESE bile bir
     * SONRAKİ kontrolde AYNI sürüm tekrar "güncelleme var" diye ÇIKMASIN diye). */
    suspend fun downloadApk(context: Context, info: UpdateInfo): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(info.downloadUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext DownloadResult.Error("İndirme başarısız (${resp.code})")
                }
                val body = resp.body ?: return@withContext DownloadResult.Error("İndirme başarısız")
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val outFile = File(updatesDir, apkAssetName)
                outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                updatePreferenceStore.setLastKnownAssetUpdatedAt(info.assetUpdatedAt)
                DownloadResult.Success(outFile)
            }
        } catch (e: Exception) {
            DownloadResult.Error("İndirme başarısız, lütfen tekrar deneyin")
        }
    }

    /** API 26+'da "Bilinmeyen kaynaklardan yükle" — Play Store'un AKSİNE
     * kendi indirdiğimiz bir APK'yı kurdurmak için bu izin AÇIKÇA (kullanıcı
     * Ayarlar'a gidip) verilmeli, runtime dialog YOK. */
    fun hasInstallPermission(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Kullanıcıyı "Bilinmeyen kaynaklardan yükle" ayar ekranına götürür —
     * SADECE BU UYGULAMA için (package: URI'si), sistem geneli DEĞİL. */
    fun buildInstallPermissionSettingsIntent(context: Context): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    fun buildInstallIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
