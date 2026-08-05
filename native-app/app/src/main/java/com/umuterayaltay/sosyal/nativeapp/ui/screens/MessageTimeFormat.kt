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
// Madde 4 (performans taraması, kullanıcı raporu: "kasma") — formatClockTime()
// PostCard/MessageBubble'ın HER görünür satırında, HER recomposition'da
// çağrılıyor (liste kaydırmanın en sıcak yolu). ÖNCEKİ hâlde `.withZone(...)`
// HER ÇAĞRIDA yeni bir DateTimeFormatter nesnesi ALLOCATE ediyordu (immutable
// builder deseni - withZone() var olanı DEĞİŞTİRMEZ, YENİSİNİ döndürür) +
// ZoneId.systemDefault() her seferinde tekrar sorgulanıyordu. Zone'u BİR KEZ
// (sınıf yüklenirken) uygulayıp SONUCU saklamak bu tekrarlayan allocation'ı
// tamamen ORTADAN KALDIRIYOR. Bilinen sınır: kullanıcı UYGULAMA AÇIKKEN
// sistem saat dilimini değiştirirse (son derece nadir bir senaryo) yeni dilim
// bir sonraki uygulama başlatmasına kadar YANSIMAZ - önceki davranış (her
// çağrıda systemDefault() okuma) bunu anlık yansıtıyordu, ama bu MVP'de kasma
// riskini ortadan kaldırmanın kazancı bu son derece nadir kenar durumdan
// AĞIR BASIYOR.
private val CLOCK_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

internal fun formatClockTime(iso: String?): String {
    val instant = parseIsoTimestamp(iso) ?: return ""
    return CLOCK_FORMATTER.format(instant)
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
