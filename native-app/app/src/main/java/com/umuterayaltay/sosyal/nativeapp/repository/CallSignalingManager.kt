package com.umuterayaltay.sosyal.nativeapp.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// GEÇİCİ TANI LOGLAMASI (kullanıcı raporu: gerçek cihazda gelen arama HÂLÂ
// gelmiyor / kabul sonrası arayan takılı kalıyor, 2. tur teorik fix'ten SONRA
// bile devam ediyor) — projede önceden HİÇ logging altyapısı yoktu (grep ile
// doğrulandı). Bu TAG'lerle `adb logcat -s CallSignaling:* CallSession:*`
// çalıştırılıp gerçek cihazda hangi adımda kopuk olduğu (connect mi, subscribe
// mi, broadcast hiç gelmiyor mu, sendSignal mı başarısız) NET görülebilir —
// tahmine devam etmek yerine ampirik veri toplama kararı.
private const val TAG = "CallSignaling"

private const val SUBSCRIBE_TIMEOUT_MS = 10_000L

// RealtimeConnectionManager (mesajlaşma polling yedeği) ile AYNI 20 dakikalık
// pay gerekçesi — bkz. o dosyanın TOKEN_REFRESH_INTERVAL_MS yorumu.
private const val TOKEN_REFRESH_INTERVAL_MS = 20 * 60 * 1000L

// RealtimeConnectionManager.watchConnectionHealth() ile AYNI değerler/gerekçe.
private const val HEALTH_CHECK_INTERVAL_MS = 5_000L
private const val CONNECTION_UNHEALTHY_THRESHOLD_MS = 45_000L

// Bağlantı kurulamazsa/koparsa önce bunu bekleyip TEKRAR dener — RealtimeConnectionManager'ın
// AKSİNE (o başarısızlıkta polling'e "düşer", bir daha KENDİSİ retry etmez;
// çağıran ConversationViewModel her ekran açılışında yeniden connect() çağırarak
// doğal bir retry sağlar) çağrı sinyalleşmesinin ARKASINDA hiçbir yedek YOK —
// tek bir başarısız deneme/kopma SESSİZCE tüm oturum boyunca "gelen arama hiç
// gelmiyor" anlamına gelirdi (kullanıcı raporu, gerçek cihaz: "web'den arayınca
// mobile gelmiyor"). Bu yüzden burada SÜREKLİ kendi kendini iyileştiren bir
// döngü var.
private const val RECONNECT_DELAY_MS = 4_000L

/**
 * 1:1 sesli/görüntülü arama sinyalleşme protokolü — web'in app/static/js/call.js
 * dosyasının BİREBİR native karşılığı (bkz. görev notu). Web SADECE Supabase
 * Realtime broadcast kullanıyor (backend'de bu iş için hiç REST endpoint YOK):
 * her kullanıcı KENDİ arama kanalına abone olur ve `call-signal` broadcast
 * event'ini dinler; birine sinyal göndermek için HEDEFİN kanalı açılıp AYNI
 * event'e broadcast yapılır. Kanal adı KULLANICI ID'Sİ DEĞİL — Supabase'in
 * private kanal yetkilendirmesi platform tarafında bozuk olduğu için
 * (bkz. sosyal-medya deposu .context/active_context.md "2026-08-07 devam
 * 6/7") kanallar public'e alındı, adlar sunucunun ürettiği tahmin edilemez
 * HMAC (bkz. AuthRepository.getRealtimeToken() -> myCallsTopic, api_v1/
 * messaging.py -> other_call_topic). Sinyal payload'ı web'de düz JS
 * objesi olduğu için burada da kotlinx.serialization.json.JsonObject
 * kullanılıyor (ARZ EDİLEN "Retrofit+Gson, kotlinx.serialization DEĞİL"
 * kararına AYKIRI değil — bkz. RealtimeConnectionManager sınıf yorumu:
 * JsonObject'in KENDİ serializer'ı kotlinx-serialization-json kütüphanesinin
 * içine ÖNCEDEN derlenmiş durumda, bu modülün kotlin.serialization compiler
 * plugin'ine İHTİYACI YOK — RealtimeChannel.broadcast(event, JsonObject) da
 * temel arayüz metodu zaten JsonObject alıyor, reified/plugin gerektiren
 * generic aşırı yüklemesi KULLANILMIYOR).
 */
sealed class CallSignal {
    abstract val from: String
    // Gönderenin KENDİ arama kanalı adı — web'in fromTopic'iyle AYNI gerekçe
    // (2026-08-07): kanallar artık public, adları tahmin edilemez HMAC (bkz.
    // app/realtime_topics.py). Cevap veren taraf arayanın kanal adını BAŞKA
    // TÜRLÜ öğrenemez (gelen arama herhangi bir ekranda olabilir, otherUserId
    // sadece görüntüleme için kalır, YÖNLENDİRME artık BUNUNLA yapılıyor).
    abstract val fromTopic: String
    abstract val conversationId: String?

    data class Offer(
        override val from: String,
        override val fromTopic: String,
        override val conversationId: String?,
        val sdp: String,
        val video: Boolean,
        val callerName: String,
        val callerAvatar: String?,
    ) : CallSignal()

    data class Answer(
        override val from: String,
        override val fromTopic: String,
        override val conversationId: String?,
        val sdp: String,
    ) : CallSignal()

    data class Ice(
        override val from: String,
        override val fromTopic: String,
        override val conversationId: String?,
        val candidate: String,
        val sdpMLineIndex: Int,
        val sdpMid: String?,
    ) : CallSignal()

    data class Hangup(override val from: String, override val fromTopic: String, override val conversationId: String?) : CallSignal()
    data class Reject(override val from: String, override val fromTopic: String, override val conversationId: String?) : CallSignal()
}

private fun CallSignal.toPayload(): JsonObject = buildJsonObject {
    put("from", from)
    put("fromTopic", fromTopic)
    conversationId?.let { put("conversation_id", it) }
    when (val signal = this@toPayload) {
        is CallSignal.Offer -> {
            put("type", "offer")
            put("sdp", signal.sdp)
            put("video", signal.video)
            put("callerName", signal.callerName)
            signal.callerAvatar?.let { put("callerAvatar", it) }
        }
        is CallSignal.Answer -> {
            put("type", "answer")
            put("sdp", signal.sdp)
        }
        is CallSignal.Ice -> {
            put("type", "ice")
            put("candidate", signal.candidate)
            put("sdpMLineIndex", signal.sdpMLineIndex)
            signal.sdpMid?.let { put("sdpMid", it) }
        }
        is CallSignal.Hangup -> put("type", "hangup")
        is CallSignal.Reject -> put("type", "reject")
    }
}

private fun parseSignal(json: JsonObject): CallSignal? {
    val type = json["type"]?.jsonPrimitive?.contentOrNull ?: return null
    val from = json["from"]?.jsonPrimitive?.contentOrNull ?: return null
    // Boşa düşerse (eski/beklenmeyen payload) SESSİZCE "" — cevap gönderimi
    // o zaman sendSignal'in "hedef kanal adı boş" korumasına takılıp no-op
    // olur, tüm sinyali reddetmek yerine (bkz. sendSignal).
    val fromTopic = json["fromTopic"]?.jsonPrimitive?.contentOrNull ?: ""
    val conversationId = json["conversation_id"]?.jsonPrimitive?.contentOrNull
    return when (type) {
        "offer" -> {
            val sdp = json["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Offer(
                from = from,
                fromTopic = fromTopic,
                conversationId = conversationId,
                sdp = sdp,
                video = json["video"]?.jsonPrimitive?.booleanOrNull ?: false,
                callerName = json["callerName"]?.jsonPrimitive?.contentOrNull ?: "Birisi",
                callerAvatar = json["callerAvatar"]?.jsonPrimitive?.contentOrNull,
            )
        }
        "answer" -> {
            val sdp = json["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Answer(from, fromTopic, conversationId, sdp)
        }
        "ice" -> {
            val candidate = json["candidate"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Ice(
                from = from,
                fromTopic = fromTopic,
                conversationId = conversationId,
                candidate = candidate,
                sdpMLineIndex = json["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                sdpMid = json["sdpMid"]?.jsonPrimitive?.contentOrNull,
            )
        }
        "hangup" -> CallSignal.Hangup(from, fromTopic, conversationId)
        "reject" -> CallSignal.Reject(from, fromTopic, conversationId)
        else -> null
    }
}

/**
 * Uygulama ömrü boyunca AÇIK kalan tek bir KENDİ arama kanalı dinleyicisi +
 * hedef başına (kısa ömürlü, önbelleklenen) giden sinyal kanalları. Web'in
 * `initGlobalCallListener`/`sendSignal`/`getOutboundChannel` üçlüsünün AYNISI
 * — bkz. call.js.
 *
 * KULLANICI RAPORU (gerçek cihaz, 2. tur) — "web'den arayınca mobile gelen
 * arama hiç gelmiyor" + "karşı taraf kabul ediyor ama aramaya geçmiyor": kök
 * neden, İLK sürümün `connect()`'i TEK SEFERLİK olmasıydı — başarısız olursa
 * (token isteği/network hıçkırığı, ÖZELLİKLE soğuk başlangıçta gerçekleşme
 * ihtimali yüksek) veya bağlantı SONRADAN koparsa, hiçbir yerden TEKRAR
 * denenmiyordu; [RealtimeConnectionManager]'ın (mesaj polling yedeği) AKSİNE
 * bunun arkasında bir "polling'e düş" yedeği de YOK — tek bir hıçkırık o
 * OTURUM boyunca "gelen arama hiç gelmiyor" demekti. [startListening] artık
 * [RealtimeConnectionManager.watchConnectionHealth]'İN AYNI disiplinini
 * (durum örnekleme + eşik) kullanan SÜREKLİ bir retry/health-watch döngüsü.
 * Ayrıca giden sinyal gönderimi ([sendSignal]) artık hedefin kanalına GERÇEKTEN
 * abone olunduğunu doğruluyor (önceden `subscribe()` zaman aşımına uğrasa
 * bile kanal SESSİZCE "hazır" sayılıp broadcast deneniyordu — bu, kabul
 * sinyalinin arayana hiç ULAŞMAMASININ olası ikinci bir kök nedeniydi).
 */
class CallSignalingManager(private val authRepository: AuthRepository) {

    private var supabase: SupabaseClient? = null
    private var inboundChannel: RealtimeChannel? = null
    private var connectionScope: CoroutineScope? = null
    private var supervisorJob: Job? = null
    private var listeningUserId: String? = null

    private val outboundMutex = Mutex()
    private val outboundChannels = mutableMapOf<String, RealtimeChannel>()

    // Art arda başarısız connectOnce() denemesi sayacı — bir JWT imza anahtarı
    // rotasyonu SONRASI backend'in normal (saat bazlı) yenileme kısayolu
    // bozuk-ama-henüz-süresi-dolmamış token'ı sonsuza kadar sunabilir (bkz.
    // getRealtimeToken(force) yorumu); İLK başarısız denemeden SONRA bir
    // sonraki tur force=true göndererek backend'i GERÇEK bir Supabase
    // yenilemesine zorluyoruz, kullanıcı sekmeyi/uygulamayı kapatıp açmak
    // ZORUNDA kalmadan kendi kendine iyileşsin diye (canlı bulgu — bkz. görev
    // notu, aksi halde "Unauthorized" süresiz döngüye giriyordu).
    private var consecutiveFailures = 0

    private val _incoming = MutableSharedFlow<CallSignal>(extraBufferCapacity = 16)
    val incoming: SharedFlow<CallSignal> = _incoming.asSharedFlow()

    val isConnected: Boolean get() = supabase != null

    // Kendi arama kanalı adı — /realtime-token'dan gelir (bkz. connectOnce),
    // giden HER sinyalin fromTopic'i olarak taşınır ki karşı taraf bize
    // cevap verebilsin (topic'i başka türlü öğrenemezdi).
    @Volatile
    var myTopic: String? = null
        private set

    /**
     * [scope]'ta SÜREKLİ çalışan bir döngü başlatır: bağlan → (başarılıysa)
     * sağlığı izle → kopunca/başarısızsa [RECONNECT_DELAY_MS] bekleyip TEKRAR
     * dene — sonsuza kadar. idempotent: aynı userId için tekrar çağrı no-op —
     * [forceRestart] = true bu korumayı ATLAR (2026-08-08, bkz. FcmService.kt
     * "incoming_call_wake" işleyicisi): mevcut döngü şu an [RECONNECT_DELAY_MS]
     * bekliyor olabilir (bağlantı arka planda bayatlamış), FCM ile uyandırılan
     * bir cihazın bu bekleyişin bitmesini bekletmeden HEMEN yeniden denemesi
     * gerekir — mevcut supervisorJob iptal edilip döngü sıfırdan başlatılır.
     */
    fun startListening(userId: String, scope: CoroutineScope, forceRestart: Boolean = false) {
        if (!forceRestart && supervisorJob != null && listeningUserId == userId) return
        supervisorJob?.cancel()
        listeningUserId = userId
        supervisorJob = scope.launch {
            while (true) {
                val connected = connectOnce(userId)
                if (connected) {
                    watchHealth()
                }
                disconnectResources()
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    /** Tek bir bağlantı KURMA denemesi — başarılı/başarısız Boolean döner,
     * artık SESSİZCE yutmuyor (çağıran [startListening] retry kararını verir). */
    private suspend fun connectOnce(userId: String): Boolean {
        try {
            val forceTokenRefresh = consecutiveFailures > 0
            Log.d(TAG, "connectOnce: başlıyor, userId=$userId, force=$forceTokenRefresh")
            val tokenResult = authRepository.getRealtimeToken(force = forceTokenRefresh)
            val token = when (tokenResult) {
                is RealtimeTokenResult.Success -> tokenResult
                is RealtimeTokenResult.Error -> {
                    Log.w(TAG, "connectOnce: getRealtimeToken başarısız (kod=${tokenResult.code})")
                    consecutiveFailures++
                    return false
                }
            }
            val myTopicValue = token.myCallsTopic
            if (myTopicValue.isNullOrBlank()) {
                Log.w(TAG, "connectOnce: backend my_calls_topic döndürmedi, dinleyici kurulamıyor")
                consecutiveFailures++
                return false
            }
            Log.d(TAG, "connectOnce: realtime token alındı, url=${token.supabaseUrl}, myTopic=$myTopicValue")

            val client = createSupabaseClient(
                supabaseUrl = token.supabaseUrl,
                supabaseKey = token.supabasePublishableKey,
            ) {
                httpEngine = OkHttp.create()
                install(Realtime)
            }
            supabase = client
            client.realtime.setAuth(token.accessToken)

            // private:true KALDIRILDI (2026-08-07): Supabase'in private kanal
            // yetkilendirmesi platform tarafında bozuk — JOIN'de veritabanına
            // hiç sorulmadan "Unauthorized" dönüyordu (kanıt: sosyal-medya
            // deposu .context/active_context.md "2026-08-07 devam 6"). Kanal
            // artık public, güvenlik adın tahmin edilemezliğinde (bkz.
            // app/realtime_topics.py — myTopicValue backend'in ürettiği HMAC).
            val ch = client.channel(myTopicValue) {
                broadcast { receiveOwnBroadcasts = false }
            }
            inboundChannel = ch
            myTopic = myTopicValue

            val cs = CoroutineScope(Dispatchers.IO + SupervisorJob())
            connectionScope = cs

            ch.broadcastFlow<JsonObject>("call-signal").onEach { payload ->
                Log.d(TAG, "connectOnce: HAM broadcast alındı topic=$myTopicValue payload=$payload")
                val parsed = parseSignal(payload)
                if (parsed == null) {
                    Log.w(TAG, "connectOnce: payload parseSignal ile çözülemedi: $payload")
                } else {
                    Log.d(TAG, "connectOnce: sinyal çözüldü, incoming'e emit ediliyor: $parsed")
                    _incoming.emit(parsed)
                }
            }.launchIn(cs)

            val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                ch.subscribe(blockUntilSubscribed = true)
                true
            }
            if (subscribed != true || ch.status.value != RealtimeChannel.Status.SUBSCRIBED) {
                Log.w(TAG, "connectOnce: subscribe başarısız, status=${ch.status.value}, timedOut=${subscribed != true}")
                consecutiveFailures++
                return false
            }
            Log.d(TAG, "connectOnce: SUBSCRIBED, $myTopicValue dinleniyor")
            consecutiveFailures = 0

            cs.launch { periodicTokenRefresh(client) }
            return true
        } catch (e: Exception) {
            consecutiveFailures++
            Log.e(TAG, "connectOnce: beklenmeyen hata", e)
            return false
        }
    }

    /** [RealtimeConnectionManager.watchConnectionHealth] ile AYNI desen —
     * kanal UNSUBSCRIBED'a düşerse ANINDA, socket kesintisiz CONNECTED-dışı
     * kalırsa eşik aşılınca döner (çağıran [startListening] o zaman
     * reconnect döngüsüne düşer). */
    private suspend fun watchHealth() {
        val client = supabase ?: return
        val ch = inboundChannel ?: return
        var unhealthyMs = 0L
        while (true) {
            delay(HEALTH_CHECK_INTERVAL_MS)
            if (ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED) {
                Log.w(TAG, "watchHealth: kanal UNSUBSCRIBED'a düştü, reconnect döngüsüne dönülüyor")
                return
            }
            if (client.realtime.status.value == Realtime.Status.CONNECTED) {
                unhealthyMs = 0L
            } else {
                unhealthyMs += HEALTH_CHECK_INTERVAL_MS
                if (unhealthyMs >= CONNECTION_UNHEALTHY_THRESHOLD_MS) {
                    Log.w(TAG, "watchHealth: socket ${unhealthyMs}ms boyunca CONNECTED değildi (son durum=${client.realtime.status.value}), reconnect döngüsüne dönülüyor")
                    return
                }
            }
        }
    }

    private suspend fun periodicTokenRefresh(client: SupabaseClient) {
        while (true) {
            delay(TOKEN_REFRESH_INTERVAL_MS)
            when (val result = authRepository.getRealtimeToken()) {
                is RealtimeTokenResult.Success -> {
                    try {
                        client.realtime.setAuth(result.accessToken)
                    } catch (e: Exception) {
                        return
                    }
                }
                is RealtimeTokenResult.Error -> return
            }
        }
    }

    /** Hedef kanala (artık kullanıcı id'si DEĞİL, sunucudan gelen tahmin
     * edilemez HMAC ADI) sinyal gönderir — web'in sendSignal()'ı ile AYNI:
     * kanal yoksa/subscribe değilse önce kurulur (kısa ömürlü, hedef başına
     * önbelleklenir). Artık Boolean DÖNÜYOR (önceden sessizce yutuyordu) —
     * kritik sinyaller (ör. acceptIncoming()'in Answer'ı) gönderilemezse
     * çağıran taraf artık BUNU BİLİP arayan hiç haberdar olmadan "bağlandı"
     * göstermek yerine hatayı yansıtabilir. */
    suspend fun sendSignal(targetTopic: String, signal: CallSignal): Boolean {
        if (targetTopic.isBlank()) {
            Log.w(TAG, "sendSignal: hedef kanal adı boş, gönderilemedi: $signal")
            return false
        }
        val client = supabase ?: run {
            Log.w(TAG, "sendSignal: supabase client YOK (henüz bağlanılmadı), gönderilemedi: $signal -> $targetTopic")
            return false
        }
        return try {
            val ch = getOrCreateOutboundChannel(client, targetTopic)
            if (ch == null) {
                Log.w(TAG, "sendSignal: $targetTopic kanalına subscribe edilemedi, gönderilemedi: $signal")
                return false
            }
            ch.broadcast("call-signal", signal.toPayload())
            Log.d(TAG, "sendSignal: gönderildi -> $targetTopic: $signal")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendSignal: broadcast() istisna fırlattı -> $targetTopic: $signal", e)
            false
        }
    }

    /** ÖNCEDEN: subscribe() zaman aşımına uğrasa/başarısız olsa bile kanal
     * yine de "hazır" sayılıp döndürülüyordu — broadcast() o zaman ya
     * sessizce hiçbir yere gitmeyen ya da hata fırlatıp sendSignal'ın
     * try/catch'inde yutulan bir çağrı oluyordu; en kötüsü, ÇAĞIRAN TARAF
     * (ör. acceptIncoming()) bunu HİÇ bilmeden UI'da "bağlandı" gösteriyordu.
     * Şimdi: gerçekten SUBSCRIBED olduğu doğrulanmadan bir kanal ASLA
     * döndürülmüyor, bir kez başarısız olursa (kısa bir ağ hıçkırığı
     * ihtimaline karşı) YENİDEN denenir. */
    private suspend fun getOrCreateOutboundChannel(client: SupabaseClient, targetTopic: String): RealtimeChannel? =
        outboundMutex.withLock {
            outboundChannels[targetTopic]?.let { existing ->
                if (existing.status.value == RealtimeChannel.Status.SUBSCRIBED) return@withLock existing
                outboundChannels.remove(targetTopic)
            }
            var result: RealtimeChannel? = null
            repeat(2) {
                if (result != null) return@repeat
                // private:true KALDIRILDI — bkz. connectOnce() yorumu.
                val ch = client.channel(targetTopic) {
                    broadcast { receiveOwnBroadcasts = false }
                }
                val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                    ch.subscribe(blockUntilSubscribed = true)
                    true
                }
                if (subscribed == true && ch.status.value == RealtimeChannel.Status.SUBSCRIBED) {
                    result = ch
                    Log.d(TAG, "getOrCreateOutboundChannel: $targetTopic SUBSCRIBED")
                } else {
                    Log.w(TAG, "getOrCreateOutboundChannel: $targetTopic subscribe başarısız (status=${ch.status.value}, timedOut=${subscribed != true}), tekrar denenecek")
                    try { ch.unsubscribe() } catch (e: Exception) { /* en iyi-çaba temizlik */ }
                }
            }
            result?.let { outboundChannels[targetTopic] = it }
            result
        }

    /** Tek bir bağlantı denemesinin kaynaklarını temizler ([startListening]
     * döngüsü her reconnect turunda çağırır) — [supervisorJob]/[listeningUserId]'a
     * DOKUNMAZ, o üst döngünün sorumluluğu. */
    private suspend fun disconnectResources() {
        connectionScope?.cancel()
        connectionScope = null
        inboundChannel = null
        myTopic = null
        outboundMutex.withLock { outboundChannels.clear() }
        val savedClient = supabase
        supabase = null
        try {
            savedClient?.close()
        } catch (e: Exception) {
            // En iyi-çaba temizlik.
        }
    }
}
