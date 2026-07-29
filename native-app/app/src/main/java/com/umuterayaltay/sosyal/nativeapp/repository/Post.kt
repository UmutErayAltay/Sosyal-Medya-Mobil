package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.local.PostEntity
import com.umuterayaltay.sosyal.nativeapp.network.PostDto

/** UI'nin gördüğü sade post modeli — hem ağdan hem Room cache'inden aynı şekilde üretilir. */
data class Post(
    val id: String,
    val userId: String,
    val content: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val username: String?,
    val avatarUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val createdAt: String?,
)

fun PostDto.toDomain(): Post = Post(
    id = id,
    userId = userId,
    content = content,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    username = profiles?.username,
    avatarUrl = profiles?.avatarUrl,
    likeCount = likeCount,
    commentCount = commentCount,
    createdAt = createdAt,
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
    createdAt = createdAt,
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
    createdAt = createdAt,
)
