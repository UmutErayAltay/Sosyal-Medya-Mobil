package com.umuterayaltay.sosyal.nativeapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Feed cache'i — "önce cache göster, sonra ağdan tazele" deseni için (bkz. FeedRepository). */
@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val content: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val username: String?,
    val avatarUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val createdAt: String?,
    // version 3 -> 4: mutedByMe kolonu eklendi (Faz 5 Dalga 2A — post sessize
    // alma kalıcı bir tercih, anketin aksine offline'da da anlamlı, bkz.
    // repository/Post.kt PostEntity.toDomain() yorumu).
    val mutedByMe: Boolean = false,
    // version 4 -> 5: PostEntity'ye bookmarkedByMe kolonu eklendi (Faz 5 Dalga
    // 3A — kaydetme, mutedByMe ile AYNI gerekçeyle kalıcı bir tercih).
    val bookmarkedByMe: Boolean = false,
    // version 5 -> 6 (kullanıcı raporu: "ana sayfada çoklu görseller tek
    // görsel olarak gözüküyor, kaydıramıyorum") — kök neden: PostEntity hiç
    // imageUrls TAŞIMIYORDU, FeedRepository.observePosts() DAİMA Room'dan
    // okuduğu için (bkz. o dosyanın "cache önce göster" yorumu) ağdan taze
    // gelen çoklu-görselli bir post bile Room round-trip'inden SONRA tek
    // görsele düşüyordu (post.imageUrl'e). Room List<String> DOĞRUDAN
    // SAKLAYAMADIĞI için (TypeConverter İCAT ETMEK yerine) URL'ler TEK bir
    // metin kolonunda "|||" ayracıyla birleştiriliyor — bkz. Post.kt
    // toEntity()/toDomain() dönüşümü. URL'ler "|||" içeremeyeceği için bu
    // güvenli bir ayraç.
    val imageUrls: String? = null,
    val cachedAt: Long,
)
