package com.umuterayaltay.sosyal.nativeapp.update

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import java.io.File

/** `cacheDir` DEĞİL `filesDir` — bkz. delta güncelleme planı: `cacheDir` OS
 * tarafından baskı altında tahliye edilebiliyor, 58 MB'lık bir yeniden kurma
 * işleminin ortasında bu ölümcül. `res/xml/file_paths.xml`'e karşılık gelen
 * `<files-path name="updates" .../>` girdisiyle birlikte kullanılır. */
object UpdateStorage {
    private const val DIR_NAME = "updates"

    fun workDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** API 26+ `StorageManager.allocateBytes` — gerçek boş yer var mı (sistem
     * önbelleklerini silmeye gerek kalmadan) kontrol eder. Yetersizse delta
     * VE tam indirme ikisi de başarısız olacağı için (tam indirme daha da
     * çok yer ister) burada tam indirmeye düşmenin anlamı yoktur — çağıran
     * taraf kullanıcıya net bir "yer açın" hatası göstermeli. */
    fun hasEnoughSpace(context: Context, neededBytes: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return workDir(context).usableSpace >= neededBytes
        }
        return try {
            val sm = context.getSystemService(StorageManager::class.java)
            val uuid = sm.getUuidForPath(workDir(context))
            sm.getAllocatableBytes(uuid) >= neededBytes
        } catch (e: Exception) {
            workDir(context).usableSpace >= neededBytes
        }
    }

    /** [workDir] altındaki, hedef SHA'ya ait OLMAYAN her şeyi siler — önceki
     * kurulumdan kalan yarım indirme/yama artıklarını temizler.
     * [keepFileNames] boşsa (hedef bilinmiyorsa) HEPSİ silinir. */
    fun cleanStale(context: Context, keepFileNames: Set<String> = emptySet()) {
        val dir = workDir(context)
        dir.listFiles()?.forEach { f ->
            if (f.name !in keepFileNames) f.delete()
        }
    }
}
