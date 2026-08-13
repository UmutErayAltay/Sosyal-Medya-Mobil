package com.umuterayaltay.sosyal.nativeapp.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

sealed class PatchResult {
    data class Success(val file: File) : PatchResult()

    /** Yama denendi ama başarısız oldu (bozuk yama, bellek yetersiz, SHA
     * tutmadı vb.) — çağıran taraf HER ZAMAN tam indirmeye düşer. */
    data class Failed(val reason: String) : PatchResult()
}

/**
 * [ZstdRefPrefix] üzerine ince orkestrasyon: bellek ön kontrolü → taban
 * APK'yı oku → aç → SHA-256 doğrula → dosyaya yaz. `UnsatisfiedLinkError`
 * dahil HER `Throwable` [PatchResult.Failed]'a çevrilir — bu fonksiyonun
 * hiçbir çağrı yolu exception fırlatmaz, çünkü [UpdateRepository] burası
 * başarısız olursa DAİMA tam indirmeye düşecek şekilde yazılmıştır (bkz.
 * delta güncelleme planı, "her hata yolu tam indirmeye düşer" kısıtı).
 */
object ApkPatcher {

    suspend fun applyZstdPatch(
        baseApk: File,
        patch: File,
        outFile: File,
        windowLog: Int,
        expectedOutputSize: Long,
        expectedOutputSha256: String,
    ): PatchResult = withContext(Dispatchers.Default) {
        try {
            if (expectedOutputSize <= 0 || expectedOutputSize > Int.MAX_VALUE) {
                return@withContext PatchResult.Failed("Geçersiz beklenen boyut: $expectedOutputSize")
            }
            // Kaba bellek ön kontrolü — taban APK + çıktı aynı anda bellekte
            // (bkz. ZstdRefPrefix KDoc'u). Yeterli yer yoksa hiç denemeden
            // tam indirmeye düş; OOM'la çökmek yerine kontrollü başarısızlık.
            val neededBytes = baseApk.length() + expectedOutputSize
            val rt = Runtime.getRuntime()
            val freeHeap = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
            if (freeHeap < neededBytes * 5 / 4) {
                return@withContext PatchResult.Failed(
                    "Yetersiz bellek: gerekli ~${neededBytes / 1024 / 1024} MiB, boş ~${freeHeap / 1024 / 1024} MiB",
                )
            }

            val baseBytes = baseApk.readBytes()
            val patchBytes = patch.readBytes()
            val outBytes = ZstdRefPrefix.decompress(baseBytes, patchBytes, expectedOutputSize.toInt(), windowLog)

            val actualSha = outBytes.sha256Hex()
            if (!actualSha.equals(expectedOutputSha256, ignoreCase = true)) {
                return@withContext PatchResult.Failed(
                    "SHA-256 tutmadı: beklenen $expectedOutputSha256, üretilen $actualSha",
                )
            }

            outFile.parentFile?.mkdirs()
            Files.write(outFile.toPath(), outBytes)
            PatchResult.Success(outFile)
        } catch (e: Throwable) {
            // UnsatisfiedLinkError dahil — .so bir cihazda hiç yüklenemezse
            // (örn. beklenmeyen bir ABI) bile burada yakalanır.
            PatchResult.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
