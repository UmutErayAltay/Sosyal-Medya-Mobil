package com.umuterayaltay.sosyal.nativeapp.repository

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

private const val SUBSCRIBE_TIMEOUT_MS = 10_000L

// RealtimeConnectionManager (mesajlaşma polling yedeği) ile AYNI 20 dakikalık
// pay gerekçesi — bkz. o dosyanın TOKEN_REFRESH_INTERVAL_MS yorumu.
private const val TOKEN_REFRESH_INTERVAL_MS = 20 * 60 * 1000L

/**
 * 1:1 sesli/görüntülü arama sinyalleşme protokolü — web'in app/static/js/call.js
 * dosyasının BİREBİR native karşılığı (bkz. görev notu). Web SADECE Supabase
 * Realtime broadcast kullanıyor (backend'de bu iş için hiç REST endpoint YOK):
 * her kullanıcı KENDİ `calls:<userId>` kanalına abone olur ve `call-signal`
 * broadcast event'ini dinler; birine sinyal göndermek için HEDEFİN kanalı
 * açılıp AYNI event'e broadcast yapılır. Sinyal payload'ı web'de düz JS
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
    abstract val conversationId: String?

    data class Offer(
        override val from: String,
        override val conversationId: String?,
        val sdp: String,
        val video: Boolean,
        val callerName: String,
        val callerAvatar: String?,
    ) : CallSignal()

    data class Answer(
        override val from: String,
        override val conversationId: String?,
        val sdp: String,
    ) : CallSignal()

    data class Ice(
        override val from: String,
        override val conversationId: String?,
        val candidate: String,
        val sdpMLineIndex: Int,
        val sdpMid: String?,
    ) : CallSignal()

    data class Hangup(override val from: String, override val conversationId: String?) : CallSignal()
    data class Reject(override val from: String, override val conversationId: String?) : CallSignal()
}

private fun CallSignal.toPayload(): JsonObject = buildJsonObject {
    put("from", from)
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
    val conversationId = json["conversation_id"]?.jsonPrimitive?.contentOrNull
    return when (type) {
        "offer" -> {
            val sdp = json["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Offer(
                from = from,
                conversationId = conversationId,
                sdp = sdp,
                video = json["video"]?.jsonPrimitive?.booleanOrNull ?: false,
                callerName = json["callerName"]?.jsonPrimitive?.contentOrNull ?: "Birisi",
                callerAvatar = json["callerAvatar"]?.jsonPrimitive?.contentOrNull,
            )
        }
        "answer" -> {
            val sdp = json["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Answer(from, conversationId, sdp)
        }
        "ice" -> {
            val candidate = json["candidate"]?.jsonPrimitive?.contentOrNull ?: return null
            CallSignal.Ice(
                from = from,
                conversationId = conversationId,
                candidate = candidate,
                sdpMLineIndex = json["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                sdpMid = json["sdpMid"]?.jsonPrimitive?.contentOrNull,
            )
        }
        "hangup" -> CallSignal.Hangup(from, conversationId)
        "reject" -> CallSignal.Reject(from, conversationId)
        else -> null
    }
}

/**
 * Uygulama ömrü boyunca AÇIK kalan tek bir `calls:<meId>` dinleyicisi +
 * hedef başına (kısa ömürlü, önbelleklenen) giden sinyal kanalları. Web'in
 * `initGlobalCallListener`/`sendSignal`/`getOutboundChannel` üçlüsünün AYNISI
 * — bkz. call.js. RealtimeConnectionManager'ın AKSİNE (o TEK bir konuşma
 * için, ekran/ViewModel ömrüyle sınırlı) bu sınıf CallSessionManager
 * tarafından SÜREKLİ açık tutulur (bkz. o sınıfın startGlobalListening()'i,
 * AppNavHost.kt kök seviyesinde LaunchedEffect(Unit) ile tetiklenir) —
 * gelen aramanın uygulamanın HANGİ ekranında olursa olsun yakalanması bunu
 * gerektiriyor (web'in de sayfa geneli davranışı, bkz. call.js dosya başı
 * yorumu).
 */
class CallSignalingManager(private val authRepository: AuthRepository) {

    private var supabase: SupabaseClient? = null
    private var inboundChannel: RealtimeChannel? = null
    private var scope: CoroutineScope? = null
    private var connectedUserId: String? = null

    private val outboundMutex = Mutex()
    private val outboundChannels = mutableMapOf<String, RealtimeChannel>()

    private val _incoming = MutableSharedFlow<CallSignal>(extraBufferCapacity = 16)
    val incoming: SharedFlow<CallSignal> = _incoming.asSharedFlow()

    val isConnected: Boolean get() = supabase != null

    /** Realtime hiçbir zaman ÇEKİRDEK bir özellik değildir (bkz.
     * RealtimeConnectionManager sınıf yorumu, AYNI felsefe) — kurulum
     * başarısız olursa (token/network) SESSİZCE hiçbir şey yapılmaz, arama
     * ÖZELLİĞİ bu oturumda çalışmaz ama uygulamanın geri kalanı ETKİLENMEZ. */
    suspend fun connect(userId: String) {
        if (connectedUserId == userId && supabase != null) return
        disconnect()
        connectedUserId = userId

        try {
            val tokenResult = authRepository.getRealtimeToken()
            val token = when (tokenResult) {
                is RealtimeTokenResult.Success -> tokenResult
                is RealtimeTokenResult.Error -> return
            }

            val client = createSupabaseClient(
                supabaseUrl = token.supabaseUrl,
                supabaseKey = token.supabasePublishableKey,
            ) {
                httpEngine = OkHttp.create()
                install(Realtime)
            }
            supabase = client
            client.realtime.setAuth(token.accessToken)

            // private:true + receiveOwnBroadcasts=false — web'in calls:<meId>
            // kanalıyla AYNI RLS sözleşmesi (bkz. sql/migration_realtime_calls_
            // select_fix.sql yorumu, call.js initGlobalCallListener).
            val ch = client.channel("calls:$userId") {
                broadcast { receiveOwnBroadcasts = false }
                isPrivate = true
            }
            inboundChannel = ch

            val cs = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = cs

            ch.broadcastFlow<JsonObject>("call-signal").onEach { payload ->
                parseSignal(payload)?.let { _incoming.emit(it) }
            }.launchIn(cs)

            val subscribed = withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) {
                ch.subscribe(blockUntilSubscribed = true)
                true
            }
            if (subscribed != true || ch.status.value != RealtimeChannel.Status.SUBSCRIBED) {
                disconnect()
                return
            }

            cs.launch { periodicTokenRefresh(client) }
        } catch (e: Exception) {
            disconnect()
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

    /** Hedef kullanıcının kanalına sinyal gönderir — web'in sendSignal()'ı ile
     * AYNI: kanal yoksa/subscribe değilse önce kurulur (kısa ömürlü, hedef
     * başına önbelleklenir). Herhangi bir hata SESSİZCE yutulur (bkz. çağıran
     * taraf CallSessionManager: arama zaten 30sn'lik "cevap yok" timeout'uyla
     * kendini toparlıyor, web'de de AYNI "sessizce logla" davranışı var). */
    suspend fun sendSignal(targetUserId: String, signal: CallSignal) {
        val client = supabase ?: return
        try {
            val ch = getOrCreateOutboundChannel(client, targetUserId)
            ch.broadcast("call-signal", signal.toPayload())
        } catch (e: Exception) {
            // Sinyal gönderilemedi — kritik değil, bkz. yukarıdaki fonksiyon yorumu.
        }
    }

    private suspend fun getOrCreateOutboundChannel(client: SupabaseClient, targetUserId: String): RealtimeChannel =
        outboundMutex.withLock {
            outboundChannels[targetUserId]?.let { existing ->
                if (existing.status.value == RealtimeChannel.Status.SUBSCRIBED) return@withLock existing
                outboundChannels.remove(targetUserId)
            }
            val ch = client.channel("calls:$targetUserId") {
                broadcast { receiveOwnBroadcasts = false }
                isPrivate = true
            }
            withTimeoutOrNull(SUBSCRIBE_TIMEOUT_MS) { ch.subscribe(blockUntilSubscribed = true) }
            outboundChannels[targetUserId] = ch
            ch
        }

    suspend fun disconnect() {
        connectedUserId = null
        val savedClient = supabase
        scope?.cancel()
        scope = null
        inboundChannel = null
        outboundChannels.clear()
        supabase = null
        try {
            savedClient?.close()
        } catch (e: Exception) {
            // En iyi-çaba temizlik.
        }
    }
}
