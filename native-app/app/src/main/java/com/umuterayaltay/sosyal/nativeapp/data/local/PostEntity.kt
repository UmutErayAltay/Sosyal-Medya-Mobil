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
    val username: String?,
    val avatarUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: String?,
    val cachedAt: Long,
)
