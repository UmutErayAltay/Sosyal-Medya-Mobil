package com.umuterayaltay.sosyal.nativeapp.update

import android.content.Context
import java.io.File

sealed class BaseApkResult {
    data class Found(val file: File) : BaseApkResult()
    data class Unsupported(val reason: String) : BaseApkResult()
}

/**
 * Kurulu APK'nın kendisini bulur — [Context.getApplicationInfo].sourceDir,
 * kurulum sırasında sistem tarafından `/data/app/.../base.apk`'ye BİREBİR
 * kopyalanır ve sonradan yeniden yazılmaz (dexopt çıktıları ayrı `oat/`
 * dizinine gider), kendi paketimiz için izin gerekmez.
 *
 * Buradaki kontroller bir GÜVENCE değil — asıl güvence [ApkPatcher]'ın
 * yamayı uyguladıktan SONRA SHA-256 doğrulamasıdır. Split/sistem APK'sı gibi
 * durumları erken elemek sadece gereksiz bir yama denemesini (ve onun
 * indirme/CPU maliyetini) atlamak için — bu kontroller kaçırılsa bile en
 * kötü sonuç SHA doğrulamasının başarısız olup tam indirmeye düşmesidir.
 */
object InstalledApkLocator {
    fun locate(context: Context): BaseApkResult {
        val appInfo = context.applicationInfo
        if (!appInfo.splitSourceDirs.isNullOrEmpty()) {
            return BaseApkResult.Unsupported("Split/app-bundle kurulumu — base.apk taban için yeterli değil")
        }
        val sourceDir = appInfo.sourceDir
            ?: return BaseApkResult.Unsupported("sourceDir null")
        val file = File(sourceDir)
        if (!file.exists() || !file.canRead() || file.length() <= 0L) {
            return BaseApkResult.Unsupported("APK dosyası okunamıyor: $sourceDir")
        }
        return BaseApkResult.Found(file)
    }
}
