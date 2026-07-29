package com.umuterayaltay.sosyal.nativeapp.network

import com.google.gson.annotations.SerializedName

/**
 * Backend JSON sözleşmesi — app/api_v1.py + app/routes/_common.py
 * (_attach_post_metrics/enrich_post_json) OKUNARAK doğrulandı, tahmin edilmedi.
 * Kaynak: sosyal-medya reposu app/api_v1.py (login/logout/me/feed) ve
 * tests/test_api_v1.py (gerçek response şekli assertion'ları).
 */

// ---- Auth ----

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String?,
)

data class UserDto(
    val id: String,
    val email: String?,
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
)

data class LoginResponse(
    val token: String?,
    val user: UserDto?,
    // Hata durumunda (401/403/429/500) backend {error: "..."} döner, token/user olmaz.
    val error: String?,
    val message: String?,
)

data class MeResponse(
    val user: UserDto?,
    val error: String?,
)

data class LogoutResponse(
    val ok: Boolean?,
    val error: String?,
)

// ---- Feed ----

/** posts.profiles!posts_user_id_fkey(username, avatar_url, is_private, is_deactivated) embed'i. */
data class ProfileEmbedDto(
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("is_private") val isPrivate: Boolean? = null,
    @SerializedName("is_deactivated") val isDeactivated: Boolean? = null,
)

/** attach_repost_of() ile eklenen orijinal post özeti (repost ise dolu). */
data class RepostOfDto(
    val id: String?,
    val content: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("image_urls") val imageUrls: List<String>?,
    @SerializedName("video_url") val videoUrl: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("user_id") val userId: String?,
    val profiles: ProfileEmbedDto?,
)

data class PostDto(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val content: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("image_urls") val imageUrls: List<String>?,
    @SerializedName("video_url") val videoUrl: String?,
    val visibility: String?,
    @SerializedName("created_at") val createdAt: String?,
    val profiles: ProfileEmbedDto?,
    // _attach_post_metrics() / enrich_post_json() sözleşmesi (RPC ve fallback
    // yolunda AYNI alan adları — bkz. app/routes/_common.py docstring'i):
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("liked_by_me") val likedByMe: Boolean = false,
    @SerializedName("my_reaction") val myReaction: String? = null,
    @SerializedName("bookmarked_by_me") val bookmarkedByMe: Boolean = false,
    @SerializedName("muted_by_me") val mutedByMe: Boolean = false,
    @SerializedName("repost_of") val repostOf: RepostOfDto? = null,
)

data class FeedResponse(
    val posts: List<PostDto>?,
    @SerializedName("has_next") val hasNext: Boolean = false,
    @SerializedName("next_cursor") val nextCursor: Int? = null,
    val error: String?,
)
