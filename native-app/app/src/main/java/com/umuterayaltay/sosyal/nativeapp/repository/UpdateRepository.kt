package com.umuterayaltay.sosyal.nativeapp.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.umuterayaltay.sosyal.nativeapp.BuildConfig
import com.umuterayaltay.sosyal.nativeapp.data.UpdatePreferenceStore
import com.umuterayaltay.sosyal.nativeapp.network.GithubApi
import com.umuterayaltay.sosyal.nativeapp.network.GithubReleaseDto
import com.umuterayaltay.sosyal.nativeapp.update.ApkPatcher
import com.umuterayaltay.sosyal.nativeapp.update.BaseApkResult
import com.umuterayaltay.sosyal.nativeapp.update.InstalledApkLocator
import com.umuterayaltay.sosyal.nativeapp.update.PatchResult
import com.umuterayaltay.sosyal.nativeapp.update.SUPPORTED_PATCH_CODEC
import com.umuterayaltay.sosyal.nativeapp.update.UpdateManifestDto
import com.umuterayaltay.sosyal.nativeapp.update.UpdateStorage
import com.umuterayaltay.sosyal.nativeapp.update.parseUpdateManifest
import com.umuterayaltay.sosyal.nativeapp.update.sha256Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/** [patchUrl]/[patchSize]/[windowLog] `release.ps1`'in ürettiği
 * `update-manifest.json`'daki eşleşen yama kaydından gelir — bkz.
 * [SUPPORTED_PATCH_CODEC]. */
data class DeltaPlan(
    val patchUrl: String,
    val patchSize: Long,
    val windowLog: Int,
)

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val assetUpdatedAt: String,
    val sizeBytes: Long,
    /** `null` ise manifest yok/bayat — bugünkü (doğrulamasız) davranışa
     * düşülür, [deltaPlan] de her zaman `null` olur. */
    val expectedSha256: String?,
    val deltaPlan: DeltaPlan?,
)

sealed class UpdatePhase {
    data class DownloadingPatch(val done: Long, val total: Long) : UpdatePhase()
    data class DownloadingFull(val done: Long, val total: Long, val fellBackReason: String?) : UpdatePhase()
    data class ApplyingPatch(val done: Long, val total: Long) : UpdatePhase()
    data object Verifying : UpdatePhase()
}

sealed class PrepareResult {
    data class Ready(val file: File, val viaDelta: Boolean, val savedBytes: Long) : PrepareResult()
    data class Error(val message: String) : PrepareResult()
}

/**
 * GitHub Release'ten APK indirip kurulum akışını yönetir (2026-08-09,
 * kullanıcı isteği: "her seferinde elle indirip kurmak yerine uygulama
 * içinden güncelleme"; 2026-08-13 delta/yama desteği eklendi — kullanıcı
 * isteği: "sadece değişen kısımları indir"). [checkForUpdate]/[prepareUpdate]
 * backend'in KENDİ repository'lerinden (MessagingRepository vb.) BİLİNÇLİ
 * olarak AYRI bir desen — SendMessageResult gibi sealed class'lar burada da
 * var ama backend hata kodu SÖZLEŞMESİ (upload_failed vb.) YOK, çünkü karşı
 * taraf bizim backend'imiz DEĞİL, GitHub'ın kendi API'si.
 *
 * Delta akışı ADIM ADIM: [checkForUpdate] manifest'i okuyup kurulu APK'nın
 * SHA-256'sını hesaplar, eşleşen bir yama varsa [UpdateInfo.deltaPlan]
 * doldurur. [prepareUpdate] önce delta'yı dener; delta'nın HERHANGİ bir
 * adımı (indirme/uygulama/SHA doğrulama) başarısız olursa DAİMA tam
 * indirmeye düşer — kullanıcı güncellemeyi HER ZAMAN alır, delta sadece bir
 * optimizasyondur, asla tek yoldur.
 */
class UpdateRepository(
    private val githubApi: GithubApi,
    private val updatePreferenceStore: UpdatePreferenceStore,
) {

    /** APK asset adı — `release.ps1`'in yüklediği Release asset'iyle BİREBİR
     * eşleşmeli, bkz. proje `.context/active_context.md`'deki "gh release
     * upload ... --clobber" akışı. */
    private val apkAssetName = "sosyal-medya-native-debug.apk"
    private val manifestAssetName = "update-manifest.json"

    /** Önceden her indirmede AYRI bir OkHttpClient kuruluyordu (connection
     * pool/thread pool israfı) — tek, paylaşılan istemci. `readTimeout` 120
     * sn: büyük APK indirmesi için (`connectTimeout` 15 sn yeterli). */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val response = githubApi.getLatestRelease()
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext UpdateCheckResult.Error("GitHub'a ulaşılamadı, lütfen tekrar deneyin")
            }
            val apkAsset = body.assets.firstOrNull { it.name == apkAssetName }
                ?: return@withContext UpdateCheckResult.Error("Release'te APK dosyası bulunamadı")
            val manifestAsset = body.assets.firstOrNull { it.name == manifestAssetName }

            // Tazelik kuralı: manifest APK'dan DAHA ESKİ güncellenmişse
            // (yükleme sırası bozulmuş/yarım kalmışsa) BAYAT kabul edilir —
            // hiçbir hash doğrulaması yapılmadan bugünkü (manifestsiz)
            // davranışa düşülür. release.ps1 manifesti HER ZAMAN en son
            // yüklüyor, bu pencere normalde saniyeler.
            val manifest = manifestAsset
                ?.takeIf { it.updatedAt >= apkAsset.updatedAt }
                ?.let { fetchManifest(it.downloadUrl) }

            if (manifest != null) {
                val installedSha = installedApkSha256(context)
                if (installedSha != null && installedSha.equals(manifest.apk.sha256, ignoreCase = true)) {
                    updatePreferenceStore.setLastKnownAssetUpdatedAt(apkAsset.updatedAt)
                    return@withContext UpdateCheckResult.UpToDate
                }
                val deltaEntry = installedSha?.let { sha ->
                    manifest.patches.firstOrNull {
                        it.codec == SUPPORTED_PATCH_CODEC && it.fromSha256.equals(sha, ignoreCase = true)
                    }
                }
                val deltaPlan = deltaEntry?.let { patch ->
                    releaseAssetUrl(body, patch.asset)?.let { url ->
                        DeltaPlan(patchUrl = url, patchSize = patch.size, windowLog = patch.windowLog)
                    }
                }
                return@withContext UpdateCheckResult.Available(
                    UpdateInfo(
                        tagName = body.tagName,
                        downloadUrl = apkAsset.downloadUrl,
                        assetUpdatedAt = apkAsset.updatedAt,
                        sizeBytes = manifest.apk.size,
                        expectedSha256 = manifest.apk.sha256,
                        deltaPlan = deltaPlan,
                    ),
                )
            }

            // Manifest yok/bayat — BUGÜNKÜ davranış: sadece updated_at
            // karşılaştırması, SHA doğrulaması YOK, delta YOK.
            val lastKnown = updatePreferenceStore.getLastKnownAssetUpdatedAt()
            if (lastKnown == null) {
                // İLK KONTROL — bu cihaza hangi sürümün kurulu olduğunu
                // BİLMİYORUZ, bu asset'i baseline kabul edip kaydediyoruz.
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
                    expectedSha256 = null,
                    deltaPlan = null,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error("Bağlantı hatası — internet bağlantınızı kontrol edin")
        }
    }

    /** Delta varsa ÖNCE onu dener; HERHANGİ bir adımı başarısız olursa
     * (indirme, bellek/disk yetersizliği, uygulama, SHA doğrulama) sessizce
     * DEĞİL — [UpdatePhase.DownloadingFull.fellBackReason] ile görünür
     * şekilde tam indirmeye düşer. Tam indirmenin SONUNDA da (manifest
     * varsa) SHA-256 doğrulanır; tutmazsa dosya silinir ve HATA döner —
     * kurulum Intent'i hiçbir zaman doğrulanmamış bir dosya için
     * fırlatılmaz. */
    suspend fun prepareUpdate(
        context: Context,
        info: UpdateInfo,
        onPhase: (UpdatePhase) -> Unit,
    ): PrepareResult = withContext(Dispatchers.IO) {
        val workDir = UpdateStorage.workDir(context)
        val finalOut = File(workDir, apkAssetName)
        UpdateStorage.cleanStale(context, keepFileNames = emptySet())

        var fellBackReason: String? = null
        if (info.deltaPlan != null && info.expectedSha256 != null) {
            val deltaOutcome = tryDelta(context, info, info.deltaPlan, info.expectedSha256, workDir, finalOut, onPhase)
            if (deltaOutcome is PrepareResult.Ready) return@withContext deltaOutcome
            fellBackReason = (deltaOutcome as? PrepareResult.Error)?.message ?: "Parça güncelleme uygulanamadı"
        }

        downloadFullAndVerify(info, finalOut, fellBackReason, onPhase)
    }

    private suspend fun tryDelta(
        context: Context,
        info: UpdateInfo,
        plan: DeltaPlan,
        expectedSha256: String,
        workDir: File,
        finalOut: File,
        onPhase: (UpdatePhase) -> Unit,
    ): PrepareResult {
        val baseFile = when (val located = InstalledApkLocator.locate(context)) {
            is BaseApkResult.Found -> located.file
            is BaseApkResult.Unsupported -> {
                updatePreferenceStore.setLastDeltaOutcome("no_base: ${located.reason}")
                return PrepareResult.Error("Taban APK bulunamadı")
            }
        }
        if (!UpdateStorage.hasEnoughSpace(context, plan.patchSize + info.sizeBytes)) {
            updatePreferenceStore.setLastDeltaOutcome("low_disk")
            return PrepareResult.Error("Yetersiz disk alanı")
        }

        val patchFile = File(workDir, "patch.zst")
        val patchOk = downloadTo(plan.patchUrl, patchFile) { done, total ->
            onPhase(UpdatePhase.DownloadingPatch(done, total))
        }
        if (!patchOk) {
            patchFile.delete()
            updatePreferenceStore.setLastDeltaOutcome("patch_download_failed")
            return PrepareResult.Error("Yama indirilemedi")
        }

        onPhase(UpdatePhase.ApplyingPatch(0, info.sizeBytes))
        val patchResult = ApkPatcher.applyZstdPatch(
            baseApk = baseFile,
            patch = patchFile,
            outFile = finalOut,
            windowLog = plan.windowLog,
            expectedOutputSize = info.sizeBytes,
            expectedOutputSha256 = expectedSha256,
        )
        patchFile.delete()

        return when (patchResult) {
            is PatchResult.Success -> {
                updatePreferenceStore.setLastDeltaOutcome("ok")
                updatePreferenceStore.setLastKnownAssetUpdatedAt(info.assetUpdatedAt)
                PrepareResult.Ready(
                    file = patchResult.file,
                    viaDelta = true,
                    savedBytes = (info.sizeBytes - plan.patchSize).coerceAtLeast(0),
                )
            }
            is PatchResult.Failed -> {
                finalOut.delete()
                updatePreferenceStore.setLastDeltaOutcome(patchResult.reason)
                PrepareResult.Error(patchResult.reason)
            }
        }
    }

    private suspend fun downloadFullAndVerify(
        info: UpdateInfo,
        outFile: File,
        fellBackReason: String?,
        onPhase: (UpdatePhase) -> Unit,
    ): PrepareResult {
        val ok = downloadTo(info.downloadUrl, outFile) { done, total ->
            onPhase(UpdatePhase.DownloadingFull(done, total, fellBackReason))
        }
        if (!ok) {
            outFile.delete()
            return PrepareResult.Error("İndirme başarısız, lütfen tekrar deneyin")
        }
        if (info.expectedSha256 != null) {
            onPhase(UpdatePhase.Verifying)
            val actual = outFile.sha256Hex()
            if (!actual.equals(info.expectedSha256, ignoreCase = true)) {
                outFile.delete()
                return PrepareResult.Error("İndirilen dosyanın bütünlüğü doğrulanamadı, lütfen tekrar deneyin")
            }
        }
        updatePreferenceStore.setLastKnownAssetUpdatedAt(info.assetUpdatedAt)
        return PrepareResult.Ready(outFile, viaDelta = false, savedBytes = 0)
    }

    /** İptal edilebilir (coroutine Job iptal edilince [ensureActive] burada
     * [CancellationException] fırlatır — çağıran taraf `viewModelScope`'un
     * kendi Job'unu iptal ederek indirmeyi kesebilir, bkz. UpdateViewModel.
     * cancel()). */
    private suspend fun downloadTo(url: String, outFile: File, onProgress: (Long, Long) -> Unit): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val respBody = resp.body ?: return false
                val total = respBody.contentLength()
                outFile.outputStream().use { out ->
                    respBody.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                true
            }
        } catch (e: CancellationException) {
            outFile.delete()
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun fetchManifest(url: String): UpdateManifestDto? = try {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()?.let { parseUpdateManifest(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun releaseAssetUrl(release: GithubReleaseDto, assetName: String): String? =
        release.assets.firstOrNull { it.name == assetName }?.downloadUrl

    /** [UpdatePreferenceStore]'da dosya boyutu/mtime değişmediyse önbellekten
     * okur (58 MB'ı hash'lemek ~1 sn, her kontrolde tekrarlamaya gerek yok). */
    private suspend fun installedApkSha256(context: Context): String? {
        val located = InstalledApkLocator.locate(context) as? BaseApkResult.Found ?: return null
        val file = located.file
        val cached = updatePreferenceStore.getInstalledApkCache()
        if (cached != null && cached.second == file.length() && cached.third == file.lastModified()) {
            return cached.first
        }
        val sha = file.sha256Hex()
        updatePreferenceStore.setInstalledApkCache(sha, file.length(), file.lastModified())
        return sha
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
