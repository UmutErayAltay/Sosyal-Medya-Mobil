package com.umuterayaltay.sosyal.nativeapp.update

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {

    @Test
    fun `bos byte dizisinin bilinen SHA-256 degerini uretir`() {
        // NIST/genel bilinen test vektörü — boş girdinin SHA-256'sı.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ByteArray(0).sha256Hex(),
        )
    }

    @Test
    fun `abc dizisinin bilinen SHA-256 degerini uretir`() {
        // NIST FIPS 180-2 örnek vektörü.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".toByteArray(Charsets.UTF_8).sha256Hex(),
        )
    }

    @Test
    fun `hex cikti kucuk harfli ve 64 karakter`() {
        val hex = "delta güncelleme testi".toByteArray(Charsets.UTF_8).sha256Hex()
        assert(hex.length == 64) { "beklenen 64 karakter, gelen ${hex.length}" }
        assert(hex == hex.lowercase()) { "hex çıktı küçük harfli olmalı" }
    }
}
