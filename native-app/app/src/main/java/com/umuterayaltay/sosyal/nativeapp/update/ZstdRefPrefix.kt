package com.umuterayaltay.sosyal.nativeapp.update

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * zstd'nin gerçek `--patch-from` mekanizması (ZSTD_DCtx_refPrefix) — bkz.
 * delta güncelleme planı Aşama 0 ölçümü: `zstd-jni`'nin Java sarmalayıcısı
 * (`ZstdInputStream.setDict`) BAŞKA bir native fonksiyona (`ZSTD_DCtx_
 * loadDictionary`, küçük "sözlük" modu) denk geliyor ve gerçek APK'larda
 * ~700× daha kötü yama boyutu veriyor (24 MB'a karşı 26 KB, ölçüldü). Java
 * sarmalayıcı `refPrefix`'i HİÇ açmıyor (kaynak doğrulandı) — ama `zstd-jni`
 * bağımlılığının APK'ya zaten gömdüğü `.so`, tüm libzstd C API'sini
 * (`ZSTD_DCtx_refPrefix` dahil) dışa açık (`STB_GLOBAL`) sembol olarak
 * içeriyor (`pyelftools` ile ELF `.dynsym` üzerinden doğrulandı — Windows
 * derlemesinin aksine, Android/Linux derlemesi varsayılan olarak tüm
 * global sembolleri dışa açıyor). Bu sınıf o `.so`'ya JNA ile DOĞRUDAN
 * bağlanıp doğru fonksiyonu çağırıyor — ek bir native (.so/NDK) derlemesi
 * GEREKMİYOR, sadece JNA'nın dinamik sembol çözümlemesi.
 *
 * `Native.load("zstd-jni-1.5.7-13", ...)` — bu isim `zstd-jni`
 * bağımlılığının `System.loadLibrary()` ile zaten yüklediği AYNI `.so`;
 * JNA aynı süreçte zaten yüklü bir kütüphaneyi adıyla bulup ek sembollerine
 * erişebiliyor.
 */
private interface ZstdNative : Library {
    fun ZSTD_createDCtx(): Pointer
    fun ZSTD_freeDCtx(dctx: Pointer): Long
    fun ZSTD_DCtx_setParameter(dctx: Pointer, param: Int, value: Int): Long

    // Pointer/Memory ZORUNLU (byte[] DEĞİL): ZSTD_DCtx_refPrefix native
    // tarafta bir POINTER saklıyor, veriyi KOPYALAMIYOR — bu pointer'ın
    // ZSTD_decompressDCtx çağrısı TAMAMLANANA kadar geçerli kalması lazım.
    // byte[] parametreyle JNA marshaling'i belleği sadece O TEK çağrının
    // ömrü boyunca pinliyor; bir sonraki çağrıda sarkan (dangling) pointer'a
    // erişilip "Invalid memory access" ile çöküyor — bu masaüstü kanıt
    // kodunda GERÇEKTEN yaşandı, com.sun.jna.Memory kullanılarak (elle
    // serbest bırakılana kadar canlı, GC'den bağımsız native tahsis)
    // düzeltildi. ApplyPatch() bu deseni izliyor: aynı Memory hem
    // refPrefix'e hem decompressDCtx'e geçiriliyor.
    fun ZSTD_DCtx_refPrefix(dctx: Pointer, prefix: Pointer, prefixSize: Long): Long

    fun ZSTD_decompressDCtx(dctx: Pointer, dst: ByteArray, dstCapacity: Long, src: ByteArray, srcSize: Long): Long
    fun ZSTD_isError(code: Long): Int
    fun ZSTD_getErrorName(code: Long): String
}

/** zstd.h'deki ZSTD_dParameter enum'ından — kararlı genel API, sabit değer. */
private const val ZSTD_d_windowLogMax = 100

class ZstdPatchException(message: String) : Exception(message)

object ZstdRefPrefix {
    // Sürüm build.gradle.kts'teki zstd-jni bağımlılığıyla BİREBİR eşleşmeli
    // (.so dosya adına gömülü) — 1.5.7-13 DEĞİL, 1.5.7-6 (bkz. build.gradle.kts
    // yorumu: 1.5.7-13 minCompileSdk=37 istiyor).
    private val native: ZstdNative by lazy {
        Native.load("zstd-jni-1.5.7-6", ZstdNative::class.java)
    }

    /**
     * [baseApk] içeriğini "önek" (prefix) olarak kullanıp [patch]'i açar,
     * tam [expectedOutputSize] byte'lık sonucu döner. [windowLog] üretim
     * tarafının (`release.ps1`) kullandığı `--long=N` değeriyle BİREBİR
     * aynı olmalı — manifest'ten gelir.
     *
     * Bellek: [baseApk] boyutunda bir native `Memory` + [expectedOutputSize]
     * boyutunda bir Java `byte[]` aynı anda canlı olur (~2× taban APK boyutu,
     * arm64-only'de ~116 MiB). Çağıran taraf (ApkPatcher) bunu bellek ön
     * kontrolüyle sınırlar.
     */
    fun decompress(baseApk: ByteArray, patch: ByteArray, expectedOutputSize: Int, windowLog: Int): ByteArray {
        val prefixMem = Memory(baseApk.size.toLong())
        try {
            prefixMem.write(0, baseApk, 0, baseApk.size)

            val dctx = native.ZSTD_createDCtx()
                ?: throw ZstdPatchException("ZSTD_createDCtx null döndü")
            try {
                check(native.ZSTD_DCtx_setParameter(dctx, ZSTD_d_windowLogMax, windowLog), "DCtx_setParameter(windowLogMax)")
                check(native.ZSTD_DCtx_refPrefix(dctx, prefixMem, baseApk.size.toLong()), "DCtx_refPrefix")

                val out = ByteArray(expectedOutputSize)
                val written = native.ZSTD_decompressDCtx(dctx, out, out.size.toLong(), patch, patch.size.toLong())
                check(written, "decompressDCtx")
                if (written != expectedOutputSize.toLong()) {
                    throw ZstdPatchException("Beklenmeyen çıktı boyutu: $written (beklenen $expectedOutputSize)")
                }
                return out
            } finally {
                native.ZSTD_freeDCtx(dctx)
            }
        } finally {
            prefixMem.close()
        }
    }

    private fun check(code: Long, where: String) {
        if (native.ZSTD_isError(code) != 0) {
            throw ZstdPatchException("$where başarısız: ${native.ZSTD_getErrorName(code)}")
        }
    }
}
