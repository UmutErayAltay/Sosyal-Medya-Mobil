package com.umuterayaltay.sosyal.nativeapp.service

/**
 * Şu an EKRANDA açık olan konuşmanın id'si (2026-08-21, kullanıcı raporu:
 * "sohbet açıkken o sohbetin bildirimi gelmesin, sohbete girince okundu
 * olsun") — `ui/screens/ConversationScreen.kt` bir `DisposableEffect` ile
 * ekrana girince/çıkınca günceller, [FcmService] (push bildirimi geldiğinde
 * AYRI bir yaşam döngüsünde tetiklenir, Compose state'ine erişemez) bunu
 * okuyup gelen mesaj bildirimini GÖSTERİP göstermeyeceğine karar verir.
 *
 * `@Volatile` YETERLİ — basit bir tekil referans okuma/yazma, karmaşık bir
 * kilitleme GEREKMİYOR (en kötü ihtimalde bir yarış durumunda tek bir
 * bildirim yanlışlıkla gösterilir/bastırılır, kritik bir veri kaybı DEĞİL).
 */
object ActiveConversationTracker {
    @Volatile
    var activeConversationId: String? = null

    /** [FcmService.showNotification]'ın mesaj bildirimi için kullandığı ID
     * İLE `ConversationScreen`'in bildirim iptali AYNI formülü kullanmalı —
     * bir konuşmanın TÜM push bildirimleri AYNI (stabil) sistem bildirimine
     * düşer/üzerine yazar (diğer bildirim türlerinde kullanılan zaman
     * damgalı/rastgele ID'lerin AKSİNE, bkz. FcmService.showNotification()). */
    fun notificationIdFor(conversationId: String): Int = "message_$conversationId".hashCode()
}
