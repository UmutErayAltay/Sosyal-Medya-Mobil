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
    val cachedAt: Long,
)
