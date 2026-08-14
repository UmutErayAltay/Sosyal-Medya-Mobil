package com.umuterayaltay.sosyal.nativeapp.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Projede İLK JVM unit-test dosyası (2026-08-14, yayın öncesi denetim: "sıfır
 * otomatik test") — `app/src/test` (androidTest'in AKSİNE cihaz/emülatör
 * GEREKTİRMEZ, `./gradlew test` ile bu makinede doğrudan çalışır).
 *
 * formatClockTime() `internal` olduğu için (bkz. MessageTimeFormat.kt) AYNI
 * modüldeki bu test dosyasından erişilebilir — parseIsoTimestamp() `private`,
 * dolayısıyla onu DOLAYLI olarak (gerçek public yüzey üzerinden) test ediyoruz.
 */
class MessageTimeFormatTest {

    private fun expectedFor(instant: Instant): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)

    @Test
    fun `offset formatındaki backend zaman damgasını doğru ayrıştırır`() {
        // Supabase/PostgREST'in ASIL döndürdüğü format — bkz. dosyanın kendi
        // yorumu: "Instant.parse HER ÇAĞRIDA reddedip exception fırlatıyordu".
        val iso = "2026-08-14T10:30:00+00:00"
        val expected = expectedFor(Instant.parse("2026-08-14T10:30:00Z"))
        assertEquals(expected, formatClockTime(iso))
    }

    @Test
    fun `Z sonekli ISO formatını da ayrıştırır`() {
        val iso = "2026-08-14T10:30:00Z"
        val expected = expectedFor(Instant.parse(iso))
        assertEquals(expected, formatClockTime(iso))
    }

    @Test
    fun `null girdi icin bos string doner`() {
        assertEquals("", formatClockTime(null))
    }

    @Test
    fun `bos string icin bos string doner`() {
        assertEquals("", formatClockTime("  "))
    }

    @Test
    fun `gecersiz tarih string'i icin cokmeden bos string doner`() {
        // Kritik: hiçbir exception dışarı SIZMAMALI (bkz. iç içe try/catch).
        assertEquals("", formatClockTime("bu-bir-tarih-degil"))
    }

    @Test
    fun `donen deger HH-mm formatinda`() {
        val result = formatClockTime("2026-08-14T10:30:00+00:00")
        assertTrue("beklenen HH:mm biçimi, gelen: $result", result.matches(Regex("\\d{2}:\\d{2}")))
    }
}
