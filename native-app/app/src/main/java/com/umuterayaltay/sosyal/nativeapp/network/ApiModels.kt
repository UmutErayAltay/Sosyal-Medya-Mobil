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

// ---- Discover / Search (app/api_v1.py discover()/search()) ----

data class DiscoverResponse(
    val posts: List<PostDto>?,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)

/** profiles satırı — search()'teki "id, username, full_name, avatar_url, is_deactivated" select'i. */
data class UserSearchDto(
    val id: String,
    val username: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("is_deactivated") val isDeactivated: Boolean = false,
)

/** hashtags + post_hashtags'ten türetilen {tag, count} özeti. */
data class HashtagSearchDto(
    val tag: String,
    val count: Int = 0,
)

data class SearchHistoryItemDto(
    val id: String,
    @SerializedName("user_id") val userId: String?,
    val query: String,
    @SerializedName("created_at") val createdAt: String?,
)

data class SavedSearchItemDto(
    val id: String,
    @SerializedName("user_id") val userId: String?,
    val query: String,
    val label: String?,
    @SerializedName("created_at") val createdAt: String?,
)

data class SearchResponse(
    val users: List<UserSearchDto>?,
    val posts: List<PostDto>?,
    val hashtags: List<HashtagSearchDto>?,
    @SerializedName("recent_searches") val recentSearches: List<SearchHistoryItemDto>?,
    @SerializedName("saved_searches") val savedSearches: List<SavedSearchItemDto>?,
    val error: String? = null,
)

data class SaveSearchRequest(
    val q: String,
    val label: String?,
)

/** search/save, search/history/clear, search/history/{id}/delete, search/saved/{id}/delete — hepsi AYNI {ok}/{error} şekli. */
data class SimpleOkResponse(
    val ok: Boolean? = null,
    val error: String? = null,
)

// ---- Profil (app/api_v1.py satir ~607-1221: _serialize_profile_for_api,
// api_profile, _api_follow_list, api_insights, api_toggle_follow,
// api_list_follow_requests/accept/reject okunarak dogrulandi) ----

// Deaktif hesap yanitinda (deactivated=true) SADECE username/avatar_url dolu
// gelir - id/bio/created_at/is_private/is_deactivated/pinned_post_id hic yok.
// Bu yuzden id dahil cogu alan NULLABLE: Gson, Kotlin'in non-null tip
// garantisini reflection ile atlayip eksik alana null yazabilir (bilinen
// Gson+Kotlin tuzagi) - burada bilincli olarak nullable tutuldu.
data class ProfileDto(
    val id: String?,
    val username: String?,
    @SerializedName("full_name") val fullName: String?,
    val bio: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("is_deactivated") val isDeactivated: Boolean = false,
    @SerializedName("pinned_post_id") val pinnedPostId: String? = null,
    // SADECE is_self=true iken dolu gelir (_serialize_profile_for_api).
    val email: String? = null,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("hide_last_seen") val hideLastSeen: Boolean = false,
)

data class ProfileStatsDto(
    val posts: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val likes: Int = 0,
)

data class ProfileResponse(
    val profile: ProfileDto?,
    val posts: List<PostDto>? = null,
    @SerializedName("liked_posts") val likedPosts: List<PostDto>? = null,
    @SerializedName("bookmarked_posts") val bookmarkedPosts: List<PostDto>? = null,
    @SerializedName("archived_posts") val archivedPosts: List<PostDto>? = null,
    @SerializedName("is_self") val isSelf: Boolean = false,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    @SerializedName("is_pending_request") val isPendingRequest: Boolean = false,
    @SerializedName("is_private") val isPrivate: Boolean = false,
    @SerializedName("is_blocked_by_me") val isBlockedByMe: Boolean = false,
    @SerializedName("is_close_friend") val isCloseFriend: Boolean = false,
    @SerializedName("is_online") val isOnline: Boolean = false,
    @SerializedName("is_muted") val isMuted: Boolean = false,
    val deactivated: Boolean = false,
    val stats: ProfileStatsDto? = null,
    val error: String? = null,
)

// _api_follow_list()'in profiles satiri + is_following/is_self eklentisi;
// api_list_follow_requests() da AYNI sekli doner ama is_following alanini hic
// SET'lemez - bu yuzden isFollowing burada varsayilan false ile guvenli.
data class FollowUserDto(
    val id: String,
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    @SerializedName("is_self") val isSelf: Boolean = false,
)

data class FollowListResponse(
    val users: List<FollowUserDto>? = null,
    val title: String? = null,
    val error: String? = null,
)

data class DailyCountDto(
    val date: String,
    val count: Int = 0,
)

data class DayOfWeekCountDto(
    val day: String,
    val count: Int = 0,
)

// api_insights()'taki top_posts - TAM PostDto sekli DEGIL (sadece
// "id, content, created_at, likes(count), comments(count)" + turetilen
// like_count/comment_count/engagement) - bu yuzden ayri, daha dar bir DTO.
data class TopPostDto(
    val id: String,
    val content: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    val engagement: Int = 0,
)

data class InsightsResponse(
    val days: Int = 14,
    @SerializedName("total_posts") val totalPosts: Int = 0,
    @SerializedName("total_likes") val totalLikes: Int = 0,
    @SerializedName("total_comments") val totalComments: Int = 0,
    @SerializedName("likes_by_day") val likesByDay: List<DailyCountDto>? = null,
    @SerializedName("comments_by_day") val commentsByDay: List<DailyCountDto>? = null,
    @SerializedName("followers_by_day") val followersByDay: List<DailyCountDto>? = null,
    @SerializedName("top_posts") val topPosts: List<TopPostDto>? = null,
    @SerializedName("total_followers") val totalFollowers: Int = 0,
    @SerializedName("total_following") val totalFollowing: Int = 0,
    @SerializedName("avg_engagement") val avgEngagement: Double = 0.0,
    @SerializedName("day_of_week_stats") val dayOfWeekStats: List<DayOfWeekCountDto>? = null,
    @SerializedName("most_active_day") val mostActiveDay: String? = null,
    val error: String? = null,
)

data class ToggleFollowResponse(
    val following: Boolean = false,
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("is_pending") val isPending: Boolean = false,
    val error: String? = null,
)

data class FollowRequestsResponse(
    val users: List<FollowUserDto>? = null,
    val error: String? = null,
)
