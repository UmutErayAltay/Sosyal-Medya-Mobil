package com.umuterayaltay.sosyal.nativeapp.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `release.ps1`'in ürettiği update-manifest.json'ı ayrıştıran
 * [parseUpdateManifest] için gerçek regresyon testleri (2026-08-14).
 *
 * Doğrudan bu oturumdaki GERÇEK bir prod hatasıyla motive edildi:
 * `release.ps1` bir ara `fromSha256` alanına (dosya adı için kullanılan)
 * 12 KARAKTERLİK KISALTMAYI yazıyordu, tam 64 karakterlik SHA-256 yerine —
 * delta güncellemesi bu yüzden HİÇBİR ZAMAN eşleşmiyordu ama hiçbir hata da
 * fırlatmıyordu (sessiz veri hatası, sözdizimi hatası DEĞİL). Bu sınıf o
 * SINIFTAN hataları yakalayacak bir test içeriyor (fromSha256 uzunluğu).
 */
class UpdateManifestTest {

    private val validJson = """
        {
          "schema": 1,
          "apk": {
            "name": "sosyal-medya-native-debug.apk",
            "sha256": "a9d835a00675ede9e36ecd2f1a7e0d106ed88471d0ed0ef7fa6f89d78de24443",
            "size": 58403585,
            "abi": "arm64-v8a",
            "buildNumber": 0
          },
          "patches": [
            {
              "codec": "zstd-patch-from-v1",
              "fromSha256": "597632ac6197fa4d302e253b9bd12e9449608454d4f58b81a05c9872acbbd0b9",
              "toSha256": "a9d835a00675ede9e36ecd2f1a7e0d106ed88471d0ed0ef7fa6f89d78de24443",
              "asset": "patch-zstd-597632ac6197.zst",
              "size": 1543211,
              "windowLog": 26
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `gecerli manifesti dogru ayristirir`() {
        val dto = parseUpdateManifest(validJson)
        assertNotNull(dto)
        assertEquals(1, dto!!.schema)
        assertEquals("arm64-v8a", dto.apk.abi)
        assertEquals(1, dto.patches.size)
    }

    @Test
    fun `fromSha256 TAM 64 karakter olmali - kisaltma REGRESYONU`() {
        // release.ps1 hatasının aynısı: dosya adı kısaltmasını (12 karakter)
        // fromSha256'ya yazmak SESSİZCE parse ediliyor (sözdizimi hatası
        // değil) ama cihaz TAM SHA-256 ile karşılaştırdığı için eşleşme
        // hiçbir zaman tutmuyor. Bu test, gelecekte AYNI hata release.ps1'e
        // geri gelirse en azından manifest ŞEKLİNİN beklendiği gibi
        // kaldığını doğruluyor — gerçek uzunluk ihlalini yakalayan asıl
        // savunma release.ps1'in kendisinde (bkz. Get-Sha256Hex kullanımı).
        val dto = parseUpdateManifest(validJson)
        val fromSha = dto!!.patches[0].fromSha256
        assertEquals(
            "fromSha256 tam SHA-256 (64 hex karakter) olmalı, kısaltma DEĞİL",
            64, fromSha.length,
        )
    }

    @Test
    fun `bilinmeyen codec sessizce parse edilir, cagiran taraf eler`() {
        val json = validJson.replace("zstd-patch-from-v1", "file-by-file-v1")
        val dto = parseUpdateManifest(json)
        assertNotNull(dto)
        assertEquals("file-by-file-v1", dto!!.patches[0].codec)
        assertTrue(dto.patches[0].codec != SUPPORTED_PATCH_CODEC)
    }

    @Test
    fun `schema uyusmazsa null doner`() {
        val json = validJson.replace("\"schema\": 1", "\"schema\": 2")
        assertNull(parseUpdateManifest(json))
    }

    @Test
    fun `apk alani eksikse null doner`() {
        val json = """{"schema": 1, "patches": []}"""
        assertNull(parseUpdateManifest(json))
    }

    @Test
    fun `bozuk JSON exception firlatmadan null doner`() {
        assertNull(parseUpdateManifest("{bu gecerli json degil"))
    }

    @Test
    fun `bos string exception firlatmadan null doner`() {
        assertNull(parseUpdateManifest(""))
    }

    @Test
    fun `patches alani opsiyonel - yoksa bos liste`() {
        val json = """
            {"schema": 1, "apk": {"name": "x.apk", "sha256": "abc", "size": 1, "abi": "arm64-v8a"}}
        """.trimIndent()
        val dto = parseUpdateManifest(json)
        assertNotNull(dto)
        assertEquals(0, dto!!.patches.size)
    }
}
