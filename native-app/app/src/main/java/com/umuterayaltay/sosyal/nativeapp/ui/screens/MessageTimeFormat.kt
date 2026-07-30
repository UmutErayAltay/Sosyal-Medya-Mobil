package com.umuterayaltay.sosyal.nativeapp.ui.screens

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * InboxScreen (son mesaj zamanı) ve ConversationScreen (mesaj balonu saati)
 * ARASINDA paylaşılan basit zaman biçimlendirici — backend created_at/
 * last_message_at ISO-8601 string döner (Supabase/Postgres timestamptz),
 * "...Z" veya "...+00:00" biçimlerinin İKİSİNİ de destekler.
 *
 * BİLİNÇLİ SINIR: "3 dakika önce" gibi göreli bir format YOK, sadece yerel
 * saat (HH:mm) gösterilir — bu MVP turunda yeterli (spesifikasyon kapsamı
 * dışı bir detay, basitlik tercih edildi).
 */
private val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatClockTime(iso: String?): String {
    val instant = parseIsoTimestamp(iso) ?: return ""
    return CLOCK_FORMATTER.withZone(ZoneId.systemDefault()).format(instant)
}

private fun parseIsoTimestamp(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso)
    } catch (e: Exception) {
        try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (e2: Exception) {
            null
        }
    }
}
