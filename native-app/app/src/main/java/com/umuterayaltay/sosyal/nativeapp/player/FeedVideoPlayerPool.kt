package com.umuterayaltay.sosyal.nativeapp.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Feed'de aynı anda EN FAZLA bir video oynatılır (Reels/Instagram'daki AYNI
 * "sadece en görünür video" davranışı) — bu yüzden TEK, süreç ömrü boyunca
 * bir KEZ inşa edilen ExoPlayer yeterli. build() (reflective codec keşfi,
 * thread/track selector/loader kurulumu — asıl pahalı kısım) SADECE BİR KEZ
 * yapılır; aktif video değiştikçe setMediaItem()+prepare() ile (ucuz, AYNI
 * örnek yeniden kullanılır) geçiş yapılır. Feed'in ÖNCEKİ "her scroll-in'de
 * ExoPlayer.Builder().build()" davranışının (PostCard.kt PostVideoPlayer)
 * kök nedeniydi. Sessiz/muted varsayılan — Instagram/TikTok/Reels endüstri
 * standardı, hiçbir video ekranının bugüne kadar ses yönetimi YOKTU.
 */
class FeedVideoPlayerPool(context: Context) {
    private val appContext = context.applicationContext
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // sessiz otomatik oynatma — dokununca açılabilir (PlayerView controller'ı zaten ses ikonu sağlar)
        }
    }
    private var activePostId: String? = null

    /** ExoPlayer'ı ŞİMDİ (çağıranın thread'inde) inşa eder — `by lazy`'nin
     * kendisi thread-safe ama BU çağrının HANGİ thread'de yapıldığı önemli:
     * ExoPlayer, build() edildiği thread'e "sahip" olur (Looper affinity) —
     * sonraki TÜM `playerFor()` çağrıları da AYNI thread'den (ana thread,
     * Compose'un AndroidView update lambda'sı) geldiği için bu fonksiyon da
     * ana thread'den çağrılmalı (bkz. SosyalApplication.onCreate()).
     *
     * 2026-08-14 (kullanıcı raporu: "ilk açılışta akış yüklenirken kaydırma
     * takılıyor, sonra düzeliyor") — ÖNCEDEN `player` ilk kez `playerFor()`
     * içinde, yani kullanıcı akışta İLK videoya geldiğinde (tam kaydırma
     * ortasında) dokunuluyordu — ExoPlayer.Builder().build() (reflective
     * codec keşfi, renderer/track-selector/load-control kurulumu) gerçek
     * cihazlarda onlarca ms sürebilen, ANA THREAD'i bloklayan bir işlem.
     * `warmUp()` bunu uygulama SOĞUK BAŞLARKEN (ilk frame çizilmeden ÖNCE,
     * kullanıcı zaten kısa bir başlangıç gecikmesine alışkın olduğu an)
     * yapıp akış ilk kez kaydırılana kadar maliyeti bitirmiş oluyor. */
    fun warmUp() { player }

    /** postId zaten aktifse SADECE mevcut player'ı döner (setMediaItem
     * TEKRARLANMAZ — video baştan sarılmasın). */
    fun playerFor(postId: String, videoUrl: String): ExoPlayer {
        if (activePostId != postId) {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            activePostId = postId
        }
        player.playWhenReady = true
        return player
    }

    fun releaseIfActive(postId: String) {
        if (activePostId == postId) {
            player.pause()
            activePostId = null
        }
    }

    fun pauseAll() { player.pause() }
    fun resumeIfActive() { if (activePostId != null) player.playWhenReady = true }
    fun release() { player.release() }
}
