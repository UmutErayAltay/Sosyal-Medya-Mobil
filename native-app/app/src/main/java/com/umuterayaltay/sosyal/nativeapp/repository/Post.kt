package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.local.PostEntity
import com.umuterayaltay.sosyal.nativeapp.network.PostDto

/** Bir postun anketi — web'in poll_widget makrosunun gördüğü AYNI veri
 * (app/polls.py attach_polls()). Yüzdeler backend'de hesaplanır. */
data class PollOption(
    val id: String,
    val text: String,
    val votes: Int,
    val pct: Int,
)

data class Poll(
    val id: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    /** Kullanıcının oy verdiği seçenek id'si; oy yoksa null (3-yönlü toggle). */
    val myVote: String?,
)

/** RepostOfDto'nun sadeleştirilmiş domain karşılığı — Faz 5 sonrası eksik
 * giderme: repostOf ApiModels.kt'de ZATEN vardı ama buraya (domain modele)
 * hiç taşınmıyordu, bu yüzden repost atılan içeriksiz postlar (content boş)
 * PostCard'da TAMAMEN BOŞ görünüyordu. Poll ile AYNI gerekçeyle (bkz. Post.poll
 * yorumu) SADECE ağdan gelen postta dolu — Room cache'e (PostEntity) YAZILMAZ. */
data class RepostEmbed(
    val id: String?,
    val content: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val createdAt: String?,
    val username: String?,
    val avatarUrl: String?,
)

/** UI'nin gördüğü sade post modeli — hem ağdan hem Room cache'inden aynı şekilde üretilir. */
data class Post(
    val id: String,
    val userId: String,
    val content: String?,
    val imageUrl: String?,
    // 2026-08-09 (kullanıcı isteği: "1'den fazla görsel ekleme olsun,
    // instadaki gibi kaydırmalı olabilir") — PostDto zaten `image_urls`
    // taşıyordu (backend web tarafında ÇOKTAN vardı), sadece domain modele
    // hiç taşınmıyordu. Room cache'e (PostEntity) BİLİNÇLİ YAZILMAZ — poll ile
    // AYNI gerekçe (bkz. yukarıdaki Poll alan yorumu): offline post'larda
    // sadece ilk görsel (imageUrl) gösterilir, bu kabul edilebilir bir
    // degradasyon (görsel URL'leri değişmez ama offline carousel İCAT
    // EDİLMEDİ, tek doğruluk kaynağı ağdan gelen post).
    val imageUrls: List<String>? = null,
    val videoUrl: String?,
    val username: String?,
    val avatarUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val createdAt: String?,
    val poll: Poll? = null,
    // Faz 5 Dalga 2A: bu gönderinin bildirimlerini BEN sessize aldım mı — backend
    // _attach_post_metrics()/enrich_post_json()'un ZATEN döndürdüğü muted_by_me
    // alanı önceden buraya taşınmıyordu (PostDto'da vardı, domain modelde
    // düşürülüyordu). Feed'den GİZLEME anlamına GELMEZ (bkz. MutesRepository
    // yorumu) — sadece "Postu Sessize Al" menü metninin doğru göstermesi için.
    val mutedByMe: Boolean = false,
    // Faz 5 Dalga 3A: bu postu BEN kaydettim mi — _attach_post_metrics()/
    // enrich_post_json()'un döndürdüğü bookmarked_by_me alanı (mutedByMe ile
    // AYNI gerekçeyle önceden PostDto'da vardı, domain modelde düşürülüyordu).
    val bookmarkedByMe: Boolean = false,
    /** SADECE profil "Kaydedilenler" listesinde dolu (hangi koleksiyona ait). */
    val bookmarkCollectionId: String? = null,
    // Faz 5 sonrası eksik giderme: repost embed — SADECE ağdan gelen postta
    // dolu (bkz. yukarıdaki RepostEmbed yorumu), Room cache'inden gelende null.
    val repostOf: RepostEmbed? = null,
)

fun PostDto.toDomain(): Post = Post(
    id = id,
    userId = userId,
    content = content,
    imageUrl = imageUrl,
    imageUrls = imageUrls,
    videoUrl = videoUrl,
    username = profiles?.username,
    avatarUrl = profiles?.avatarUrl,
    likeCount = likeCount,
    commentCount = commentCount,
    likedByMe = likedByMe,
    createdAt = createdAt,
    mutedByMe = mutedByMe,
    bookmarkedByMe = bookmarkedByMe,
    bookmarkCollectionId = bookmarkCollectionId,
    repostOf = repostOf?.let { r ->
        RepostEmbed(
            id = r.id,
            content = r.content,
            imageUrl = r.imageUrl,
            videoUrl = r.videoUrl,
            createdAt = r.createdAt,
            username = r.profiles?.username,
            avatarUrl = r.profiles?.avatarUrl,
        )
    },
    poll = poll?.let { p ->
        Poll(
            id = p.id,
            options = (p.options ?: emptyList()).map {
                PollOption(id = it.id, text = it.text, votes = it.votes, pct = it.pct)
            },
            totalVotes = p.totalVotes,
            myVote = p.myVote,
        )
    },
)

fun PostDto.toEntity(cachedAt: Long): PostEntity = PostEntity(
    id = id,
    userId = userId,
    content = content,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    username = profiles?.username,
    avatarUrl = profiles?.avatarUrl,
    likeCount = likeCount,
    commentCount = commentCount,
    likedByMe = likedByMe,
    createdAt = createdAt,
    mutedByMe = mutedByMe,
    bookmarkedByMe = bookmarkedByMe,
    cachedAt = cachedAt,
)

fun PostEntity.toDomain(): Post = Post(
    id = id,
    userId = userId,
    content = content,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    username = username,
    avatarUrl = avatarUrl,
    likeCount = likeCount,
    commentCount = commentCount,
    likedByMe = likedByMe,
    createdAt = createdAt,
    // post_mute (bildirim susturma) anket gibi geçici/dinamik DEĞİL — kalıcı bir
    // kullanıcı tercihidir, bu yüzden anketin AKSİNE (poll = null yorumuna bkz.)
    // Room'da da cache'lenir.
    mutedByMe = mutedByMe,
    // bookmarkedByMe de mutedByMe ile AYNI gerekçeyle kalıcı bir kullanıcı
    // tercihi — Room'da cache'lenir (bkz. PostEntity/AppDatabase version 5).
    bookmarkedByMe = bookmarkedByMe,
    // Anket BİLEREK cache'lenmiyor (PostEntity'de poll kolonu YOK): oy sayıları
    // ve my_vote dinamik veridir — offline'da eski yüzdeleri göstermek yanıltıcı
    // olur, üstelik offline'ken oy verilemeyeceği için widget sadece yanlış bilgi
    // verirdi. Ağdan gelen post'ta poll dolu, cache'ten gelende null.
    poll = null,
)
