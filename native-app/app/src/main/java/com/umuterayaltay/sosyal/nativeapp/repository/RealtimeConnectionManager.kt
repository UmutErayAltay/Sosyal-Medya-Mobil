package com.umuterayaltay.sosyal.nativeapp.repository

import com.google.gson.Gson
import com.umuterayaltay.sosyal.nativeapp.network.MessageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

private const val SUBSCRIBE_TIMEOUT_MS = 10_000L

// Sunucu ~1 saatte bir JWT'yi süresi doldurmadan yenilemesi gerektiğini
// söylüyor (bkz. app/api_v1.py api_realtime_token() — 5 dakika kala backend
// otomatik yeniliyor); native bu uç noktayı 20 dakikada bir tekrar çağırıp
// setAuth() ile GÜNCEL token'ı zaten açık olan kanala push'lar (bkz.
// RealtimeImpl.setAuth() kaynağı — SUBSCRIBED kanallara "access_token" mesajı
// gönderiyor, YENİDEN JOIN gerekmiyor). 5 dakikalık payın çok altında, web'in
// AYNI amaçla kullandığı periyodik yenilemeyle (bkz. app/auth.py
// refresh_session_tokens() docstring'i, "Realtime kimliksiz kalıp mesaj
// olayları sessizce kesiliyordu" — burada AYNI hatanın native'de tekrarlanmaması
// için pay bilinçli geniş tutuldu.
private const val TOKEN_REFRESH_INTERVAL_MS = 20 * 60 * 1000L

// Bağlantı sağlığı, HEALTH_CHECK_INTERVAL_MS'de bir örneklenir (StateFlow'u
// reaktif dinlemek yerine — kütüphanenin socket seviyesi otomatik reconnect'i
// zaten var, bkz. RealtimeImpl.reconnect()/connect(), bu yüzden HER anlık
// DISCONNECTED/CONNECTING geçişinde değil, KESİNTİSİZ CONNECTED-olmayan bir
// süre CONNECTION_UNHEALTHY_THRESHOLD_MS'i AŞARSA gerçek bir kopma sayılır).
private const val HEALTH_CHECK_INTERVAL_MS = 5_000L
private const val CONNECTION_UNHEALTHY_THRESHOLD_MS = 45_000L

/**
 * Gerçek Supabase Realtime bağlantısını yönetir — ConversationViewModel'in
 * 5 saniyelik polling'ine (bkz. o dosyanın başındaki yorum) ekli, BAŞARISIZ
 * olursa/koparsa polling'e SESSİZCE düşen bir katman (Realtime hiçbir zaman
 * ÇEKİRDEK bir özellik değildir — bkz. app/api_v1.py api_realtime_token()
 * docstring'i, backend tarafında AYNI "fail-open" felsefesi).
 *
 * Bu proje İLK KEZ Supabase'e DOĞRUDAN bağlanıyor (o ana kadar her şey Flask
 * /api/v1 REST katmanı üzerinden gidiyordu, bkz. RetrofitClient). Kullanılan
 * TEK supabase-kt modülü realtime-kt — auth-kt/postgrest-kt YOK: JWT kendi
 * Flask backend'imizin /realtime-token uç noktasından alınıyor (bkz.
 * AuthRepository.getRealtimeToken()), Supabase Auth'a native'den HİÇ ayrıca
 * login olunmuyor.
 *
 * kotlinx.serialization BİLİNÇLİ olarak eklenmedi (projenin "Retrofit+Gson,
 * kotlinx.serialization DEĞİL" kararıyla tutarlı kalınsın diye — bkz.
 * ServiceLocator.kt yorumu): postgres_changes olayının [PostgresAction.Insert.record]
 * alanı zaten bir kotlinx.serialization.json.JsonObject (realtime-kt'nin
 * kendi iç protokolü için transitive olarak sınıf yoluna geliyor, AYRI bir
 * bağımlılık/eklenti gerekmiyor) — decodeRecord<T>() kullanmak yerine
 * `record.toString()` (geçerli JSON metni) doğrudan [Gson] ile [MessageDto]'ya
 * çevriliyor, MessageDto'ya @Serializable eklemeye HİÇ gerek kalmıyor.
 *
 * BİLİNÇLİ SINIR: postgres_changes payload'ı `messages` tablosunun ÇIPLAK
 * satırıdır — Flask'ın REST serialization'ının yaptığı `profiles`/`reply_to`/
 * `reactions` JOIN'leri postgres'in WAL tabanlı replikasyonunda YOKTUR (web
 * tarafı da AYNI sınırı taşıyor — bkz. app/static/js/chat.js satır ~1228:
 * `payload.new`'da profiles YOK, grup mesajlarında gönderen adı yerel
 * `memberMap`'ten çözülüyor). Native tarafta [MessageBubble] zaten
 * `message.profiles`'ı HİÇ render ETMİYOR (sadece senderId==myUserId ile
 * mine/theirs ayrımı yapıyor) — tek pratik etkisi: realtime'dan gelen bir
 * YANIT (reply) mesajının "şuna yanıt" alıntı rozeti [MessageDto.replyTo] null
 * kaldığı için o mesaj için görünmeyebilir (ekran yeniden açılınca/loadInitial()
 * sonrası REST'ten tam halinde gelir). Bu tradeoff'u ortadan kaldırmak
 * realtime'ın kendisini polling'e çevirirdi (her mesaj için ekstra REST turu),
 * bu yüzden BİLİNÇLİ olarak kabul edildi.
 */
class RealtimeConnectionManager(
    private val authRepository: AuthRepository,
) {
    private val gson = Gson()

    private var supabase: SupabaseClient? = null
    private var channel: RealtimeChannel? = null
    private var scope: CoroutineScope? = null
    private val failureReported = AtomicBoolean(false)

    // ServiceLocator'da TEK bir singleton (mevcut repository desenleriyle
    // tutarlı — bkz. ServiceLocator.kt), ama her ConversationViewModel KENDİ
    // conversationId'siyle connect() çağırıyor. Kullanıcı hızlıca sohbet
    // A'dan çıkıp B'yi açarsa, A'nın ViewModel.onCleared()'ından (async,
    // Dispatchers.IO'da) tetiklenen GECİKMİŞ bir disconnect(conversationId="A")
    // çağrısı, B ZATEN bağlanmışken gelirse B'nin bağlantısını YANLIŞLIKLA
    // kapatmasın diye — disconnect(conversationId) SADECE hâlâ "aktif" sayılan
    // conversationId'yle eşleşirse gerçek temizliği yapar. connect() KENDİSİ
    // her zaman koşulsuz temizler (o an aktif olan HER NEYSE, artık BİZ
    // devralıyoruz demektir). NOT: aynı conversationId'nin art arda çok hızlı
    // yeniden açılması (aynı ekranı anında kapatıp aynı sohbeti tekrar açma)
    // gibi çok dar bir kenar durum bu korumayla TAM kapanmıyor — pratikte
    // network round-trip süresi bu yarışı imkansıza yakın kılıyor, bilinçli
    // kabul edildi (generation-token gibi daha ağır bir mekanizma bu düşük
    // ihtimalli senaryo için gereksiz karmaşıklık sayıldı).
    private var activeConversationId: String? = null

    /**
     * Realtime'ı KURMAYI dener. Herhangi bir aşamada başarısız olursa
     * (/realtime-token 503/401/network, client oluşturma hatası, kanal
     * abonelik zaman aşımı) [onFailure] çağrılır ve fonksiyon döner —
     * ConversationViewModel bunu görüp startPolling()'e düşer. Başarılıysa
     * [onNewMessage] her yeni INSERT'te (dedupe/append mantığı ÇAĞIRAN
     * tarafın sorumluluğu — pollNewest()'teki AYNI helper reuse edilir).
     */
    suspend fun connect(
        conversationId: String,
        onNewMessage: (MessageDto) -> Unit,
        onFailure: () -> Unit,
    ) {
        // Önceki bağlantı (başka bir sohbetten kalmış olabilir) koşulsuz
        // temizlenir — artık BU conversationId'nin sorumluluğu üstleniliyor.
        disconnectInternal()
        activeConversationId = conversationId
        failureReported.set(false)

        try {
            val tokenResult = authRepository.getRealtimeToken()
            val token = when (tokenResult) {
                is RealtimeTokenResult.Success -> tokenResult
                is RealtimeTokenResult.Error -> {
                    onFailure()
                    return
                }
            }

            val client = createSupabaseClient(
                supabaseUrl = token.supabaseUrl,
                supabaseKey = token.supabasePublishableKey,
            ) {
                httpEngine = OkHttp.create()
                install(Realtime)
            }
            supabase = client

            // Kanala katılmadan ÖNCE auth ayarlanır — subscribe() sırasında
            // gönderilen join payload'ı GÜNCEL access_token'ı taşısın diye
            // (bkz. RealtimeChannelImpl.subscribe() kaynağı: accessToken()
            // realtime.setAuth() ile set edilen değeri okuyor).
            client.realtime.setAuth(token.accessToken)

            // Kanal adı SERBEST (web'deki "messages:{id}" ile AYNI olması
            // GEREKMEZ — her client kendi bağlantısını açar, kanal adı
            // sadece BU client'ın yerel referansı). private:true de GEREKMEZ:
            // postgres_changes zaten `messages` tablosunun KENDİ RLS'i
            // üzerinden korunuyor (bkz. sql/migration_realtime_messages_channel_rls.sql
            // yorumu: private:true politikaları SADECE extension='broadcast'
            // için yazıldı, postgres_changes'e dokunmuyor) — filtre (conversation_id)
            // AYNI olmak zorunda, o da aşağıda ayarlanıyor.
            val ch = client.channel("native-messages-$conversationId")
            channel = ch

            // postgresChangeFlow, subscribe()'DAN ÖNCE çağrılmalı (kütüphane
            // SUBSCRIBED durumdaki bir kanala yeni postgres_changes eklemeyi
            // reddediyor — kaynak okunarak doğrulandı). NOT: PostgresChangeFilter.filter
            // (property) bu sürümde private set — doğrudan atama YAPILAMIYOR
            // (context7/GitHub master dokümanındaki örnek DAHA YENİ bir sürüme
            // ait, 3.1.4'te derleme hatası verdi, gerçek build ile doğrulandı) —
            // bunun yerine filter(column, operator, value) fonksiyonu kullanılıyor.
            val changeFlow = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
                filter("conversation_id", FilterOperator.EQ, conversationId)
            }

            val cs = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = cs

            changeFlow.onEach { action ->
                try {
                    val message = gson.fromJson(action.record.toString(), MessageDto::class.java)
                    if (message != null) onNewMessage(message)
                } catch (e: Exception) {
                    // Beklenmeyen/çevrilemeyen bir satır — tüm bağlantıyı
                    // bozmadan sessizce atla (bir sonraki poll/refresh zaten
                    // tamamlar).
                }
            }.launchIn(cs)

            val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                ch.subscribe(blockUntilSubscribed = true)
                true
            }
            if (subscribed != true || ch.status.value != RealtimeChannel.Status.SUBSCRIBED) {
                reportFailure(onFailure)
                return
            }

            cs.launch { watchConnectionHealth(client, ch, onFailure) }
            cs.launch { periodicTokenRefresh(client, onFailure) }
        } catch (e: Exception) {
            // Beklenmeyen HERHANGİ bir hata (ör. geçersiz URL, kütüphane iç
            // hatası) — Realtime çekirdek bir özellik olmadığı için burada
            // asla crash olunmaz, polling'e düşülür.
            reportFailure(onFailure)
        }
    }

    /**
     * Kurulumdan SONRA bağlantı sağlığını izler. [RErrorEvent] (kütüphane
     * kaynağı okunarak doğrulandı: Realtime/event/RErrorEvent.kt) SADECE
     * log basıyor — programatik bir "kanal hatası" callback'i YOK, bu yüzden
     * iki sinyal KENDİMİZ periyodik örnekleniyor:
     *  1) Kanal durumu UNSUBSCRIBED'a düşerse (sunucu kanalı KENDİSİ kapattı,
     *     ör. auth artık geçersiz) — ANINDA hata sayılır.
     *  2) Socket durumu (client.realtime.status) kesintisiz CONNECTED-DIŞI
     *     kalırsa (kütüphanenin kendi otomatik reconnect'i başarısız oluyor
     *     demektir — bkz. RealtimeImpl.reconnect()) — eşik aşılınca hata
     *     sayılır. Kısa/geçici blip'lerde (kütüphane zaten kendi kendine
     *     reconnect() ediyor) YANLIŞLIKLA tetiklenmemesi için eşik var.
     */
    private suspend fun watchConnectionHealth(
        client: SupabaseClient,
        ch: RealtimeChannel,
        onFailure: () -> Unit,
    ) {
        var unhealthyMs = 0L
        while (true) {
            delay(HEALTH_CHECK_INTERVAL_MS)
            if (ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED) {
                reportFailure(onFailure)
                return
            }
            if (client.realtime.status.value == Realtime.Status.CONNECTED) {
                unhealthyMs = 0L
            } else {
                unhealthyMs += HEALTH_CHECK_INTERVAL_MS
                if (unhealthyMs >= CONNECTION_UNHEALTHY_THRESHOLD_MS) {
                    reportFailure(onFailure)
                    return
                }
            }
        }
    }

    /** ~20 dakikada bir /realtime-token'ı tekrar çağırıp setAuth() ile GÜNCEL
     * JWT'yi zaten SUBSCRIBED olan kanala push'lar (bkz. sınıf yorumu ve
     * TOKEN_REFRESH_INTERVAL_MS). Yenileme başarısız olursa (503/401/network —
     * Supabase oturumu kalıcı öldü demektir) PROAKTİF olarak polling'e
     * düşülür — aksi halde JWT süresi dolunca kanal SESSİZCE kimliksiz kalırdı
     * (bkz. app/auth.py refresh_session_tokens() docstring'i, web'de yaşanan
     * ORİJİNAL sorun). */
    private suspend fun periodicTokenRefresh(client: SupabaseClient, onFailure: () -> Unit) {
        while (true) {
            delay(TOKEN_REFRESH_INTERVAL_MS)
            when (val result = authRepository.getRealtimeToken()) {
                is RealtimeTokenResult.Success -> {
                    try {
                        client.realtime.setAuth(result.accessToken)
                    } catch (e: Exception) {
                        reportFailure(onFailure)
                        return
                    }
                }
                is RealtimeTokenResult.Error -> {
                    reportFailure(onFailure)
                    return
                }
            }
        }
    }

    /** [onFailure] TAM OLARAK bir kez tetiklenir (AtomicBoolean guard —
     * watchConnectionHealth/periodicTokenRefresh/connect() aynı anda farklı
     * coroutine'lerden çağırabilir). Bağlantı BURADA tamamen kapatılır.
     * disconnectInternal() kullanılır (guard'sız) — biz KENDİ kurduğumuz
     * bağlantıyı kapatıyoruz, activeConversationId eşleşmesi kontrolüne
     * gerek yok (zaten biziz). */
    private suspend fun reportFailure(onFailure: () -> Unit) {
        if (failureReported.compareAndSet(false, true)) {
            disconnectInternal()
            onFailure()
        }
    }

    /**
     * Kanalı/client'ı temiz kapatır — ExoPlayer.release() ile AYNI disiplin:
     * ConversationViewModel.onCleared()'da MUTLAKA çağrılmalı, aksi halde
     * WebSocket bağlantısı sızar. [conversationId] o ViewModel'in KENDİ
     * conversationId'si — [activeConversationId] ile eşleşmiyorsa (bkz. sınıf
     * başındaki yorum: başka bir ekran zaten devralmış demektir) sessizce
     * NO-OP yapılır, canlı bağlantıya DOKUNULMAZ.
     */
    suspend fun disconnect(conversationId: String) {
        if (activeConversationId != conversationId) return
        disconnectInternal()
    }

    /**
     * Gerçek temizlik — guard YOK, koşulsuz kapatır. scope.cancel() ÖNCE
     * çağrılır ki watchConnectionHealth/periodicTokenRefresh/mesaj-akışı
     * coroutine'leri bu satırdan SONRA asla tetiklenmesin (kendi kendini
     * iptal eden bir coroutine'in [reportFailure] içinden burayı çağırması
     * GÜVENLİDİR — Kotlin'in kooperatif iptal modeli, mevcut çağrı çerçevesi
     * zaten tamamlanana kadar çalışmaya devam eder).
     */
    private suspend fun disconnectInternal() {
        activeConversationId = null
        val savedClient = supabase
        scope?.cancel()
        scope = null
        channel = null
        supabase = null
        try {
            savedClient?.close()
        } catch (e: Exception) {
            // Zaten kapanıyor/kapalı olabilir — en iyi-çaba temizlik, sorun değil.
        }
    }
}
