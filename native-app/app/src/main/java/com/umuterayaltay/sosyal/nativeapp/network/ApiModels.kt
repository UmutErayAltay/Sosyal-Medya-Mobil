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
    // 2FA aktif hesapta ilk deneme code'suz 403 mfa_required döner; client AYNI
    // email+password'ü code ekleyerek TEKRAR gönderir. Varsayılan null — mevcut
    // çağrı yerlerini BOZMAZ.
    val code: String? = null,
)

/** POST /api/v1/auth/register gövdesi. */
data class RegisterRequest(
    val email: String,
    val password: String,
    val username: String,
    @SerializedName("device_name") val deviceName: String?,
)

/** POST /api/v1/auth/google gövdesi — idToken Android Credential Manager'ın
 * ürettiği Google ID token'ı (bkz. GoogleSignInHelper.kt). nonce bu iterasyonda
 * BİLİNÇLİ olarak kullanılmıyor (kapsam dışı: replay-koruması), code LoginRequest
 * ile AYNI 2FA-retry deseni (mfa_required → AYNI idToken code eklenerek tekrar). */
data class GoogleLoginRequest(
    @SerializedName("id_token") val idToken: String,
    val nonce: String? = null,
    val code: String? = null,
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

/** GET /api/v1/realtime-token yanıtı (app/api_v1.py api_realtime_token() —
 * Faz 4 sonu, gerçek Supabase Realtime — bkz. RealtimeConnectionManager).
 * error İKİ farklı anlam taşıyabilir: "unavailable" (Realtime bu cihaz için
 * hiç kurulmamış/şifreleme kapalı, 503) veya "relogin_required" (Supabase
 * oturumu kesin ölmüş, 401) — HER İKİSİNDE de native SESSİZCE polling'e
 * düşer, ANA bearer token'a (bu isteği doğrulayan token) DOKUNULMAZ. */
data class RealtimeTokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("supabase_url") val supabaseUrl: String? = null,
    @SerializedName("supabase_publishable_key") val supabasePublishableKey: String? = null,
    val error: String? = null,
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
    // attach_polls() her post'a ekliyor (anketi olmayan postta null) — bu alan
    // eklenene kadar Gson JSON'daki `poll`u SESSİZCE atıyordu (Faz 5, Dalga 1A).
    val poll: PollDto? = null,
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

// ---- Engelleme (app/api_v1.py satır ~3251-3313: api_toggle_block()/
// api_blocked_list() okunarak doğrulandı — Faz 4 sonrası eksik giderme, native
// Android. is_blocked_by_me ZATEN ProfileResponse'ta var (yukarıda) — burada
// SADECE toggle + liste endpoint'lerinin DTO'ları. Backend engelleyince
// karşılıklı takip ilişkisini de koparıyor (her iki yönde) — bu yüzden native
// tarafta blok BAŞARILI olunca isFollowing/isPendingRequest de local olarak
// sıfırlanır, bkz. ProfileViewModel.toggleBlock().)

data class ToggleBlockResponse(
    val ok: Boolean = false,
    val blocked: Boolean = false,
    val error: String? = null,
)

/** GET /blocked satırı — api_blocked_list()'in "profiles!blocks_blocked_id_fkey
 * (id, username, avatar_url, full_name)" select'i, UserSearchDto'dan FARKLI
 * (is_deactivated alanı yok) - bu yüzden ayrı bir DTO. */
data class BlockedUserDto(
    val id: String,
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("full_name") val fullName: String?,
)

data class BlockedUsersResponse(
    val users: List<BlockedUserDto>? = null,
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

// ---- Reels (app/api_v1.py api_reels() — DiscoverResponse ile AYNI şekil) ----

data class ReelsResponse(
    val posts: List<PostDto>?,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)

// ---- Mesajlaşma (app/api_v1.py satır ~1289-1577: _serialize_conversation_summary,
// api_message_conversations, api_message_conversation_detail, api_send_message,
// api_start_conversation, api_mark_conversation_read okunarak doğrulandı).
// Metin + GÖRSEL gönderme (multipart/form-data) destekleniyor (Faz 4, native
// Android — bkz. MessagingApi.sendMessage()). BİLİNÇLİ SINIR: mesaj düzenleme/
// silme/sabitleme/iletme/tepki VERME/ses-sticker-GIF gönderme/WebRTC yok —
// grup yönetimi AYRICA yapıldı (bkz. aşağıdaki "Grup yönetimi" bölümü).
// Gerçek Supabase Realtime YOK, native taraf basit polling yapar (bkz.
// ConversationViewModel).

/** _serialize_conversation_summary() çıktısı — inbox satırı. */
data class ConversationSummaryDto(
    val id: String,
    @SerializedName("is_group") val isGroup: Boolean = false,
    // 1:1'de karşı tarafın username'i, grupta grup adı.
    val name: String?,
    // 1:1'de karşı tarafın avatarı, grupta HER ZAMAN null.
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("last_message_preview") val lastMessagePreview: String?,
    @SerializedName("last_message_at") val lastMessageAt: String?,
    // "unread" alanı SOHBET bazlı bool'dur (mesaj sayısı değil), gruplarda hep false.
    @SerializedName("has_unread") val hasUnread: Boolean = false,
    // SADECE gruplarda dolu (üye sayısı) — Faz 4 grup yönetimi, 1:1'de null.
    @SerializedName("member_count") val memberCount: Int? = null,
)

data class ConversationsResponse(
    val conversations: List<ConversationSummaryDto>? = null,
    val error: String? = null,
)

/** messages tablosundaki profiles!messages_sender_id_fkey(username, avatar_url) embed'i. */
data class MessageSenderDto(
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
)

/** reply_to — TEK toplu IN sorgusuyla çözülen alıntılanan mesaj özeti
 * (id, content, image_url, sender_id, profiles(username)) — mevcutsa dolu,
 * yoksa (silinmiş/başka konuşmadan) backend zaten null döner. */
data class ReplyToDto(
    val id: String,
    val content: String?,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("sender_id") val senderId: String?,
    val profiles: MessageSenderDto? = null,
)

/** message_reactions'tan {reaction, count, mine} özetine indirgenmiş satır —
 * bu turda RENDER edilmiyor (kapsam dışı: tepki VERME), ama gelen veri
 * modellenmeden bırakılmıyor. */
data class MessageReactionDto(
    val reaction: String,
    val count: Int = 0,
    val mine: Boolean = false,
)

/** Tek mesaj satırı. `sticker` bu turda hiç render edilmiyor (kapsam dışı:
 * sticker GÖNDERME) — bu yüzden bilinçli olarak dar tipli (Any?), sadece
 * null/dolu ayrımı JSON'dan bozulmadan geçsin diye. */
data class MessageDto(
    val id: String,
    @SerializedName("sender_id") val senderId: String,
    val content: String?,
    @SerializedName("reply_to_id") val replyToId: String? = null,
    @SerializedName("read_at") val readAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val profiles: MessageSenderDto? = null,
    @SerializedName("reply_to") val replyTo: ReplyToDto? = null,
    val reactions: List<MessageReactionDto>? = null,
    val sticker: Any? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    // Faz 5 Dalga 1B: düzenle/sabitle/ilet — okuma yolunda backend değişikliği
    // GEREKMEDİ (api_message_conversation_detail() zaten select("*") kullanıyor),
    // burada alan eklemek yeterli (bkz. app/api_v1/messaging.py yorumu).
    @SerializedName("edited_at") val editedAt: String? = null,
    @SerializedName("pinned_at") val pinnedAt: String? = null,
    @SerializedName("is_forwarded") val isForwarded: Boolean = false,
)

/** api_message_conversation_detail()'in "conversation" alanı — özet DTO'dan
 * (ConversationSummaryDto) FARKLI: last_message_preview/at ve has_unread yok. */
data class ConversationInfoDto(
    val id: String,
    @SerializedName("is_group") val isGroup: Boolean = false,
    val name: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
)

data class ConversationDetailResponse(
    val messages: List<MessageDto>? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val conversation: ConversationInfoDto? = null,
    val error: String? = null,
)

// NOT: SendMessageRequest (JSON gövde) artık KULLANILMIYOR — send endpoint'i
// multipart/form-data'ya geçti (bkz. MessagingApi.sendMessage()), content/
// reply_to_id doğrudan RequestBody olarak taşınıyor.

data class SendMessageResponse(
    val message: MessageDto? = null,
    val error: String? = null,
)

data class StartConversationResponse(
    @SerializedName("conversation_id") val conversationId: String? = null,
    val error: String? = null,
)

// ---- Grup yönetimi (app/api_v1.py messages/group/* — Faz 4, native Android.
// Oluşturma/rename/üye listeleme-ekleme-çıkarma/admin-toggle/ayrılma. Grup
// KEŞFİ/oluşturmadan ÖNCEKİ arama kısmı YENİ bir endpoint İCAT ETMEZ, mevcut
// DiscoverRepository.search(type="users") reuse edilir — bkz. GroupCreateViewModel/
// GroupManageViewModel.) BİLİNÇLİ SINIR: grup avatarı/fotoğrafı, sistem mesajları
// ("X gruba eklendi"), grup sesli/görüntülü arama bu turun kapsamı DIŞI.

/** GET .../group/{id}/members satırı — admin önce, sonra username sıralı (backend). */
data class GroupMemberDto(
    val id: String,
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
)

data class GroupMembersResponse(
    val members: List<GroupMemberDto>? = null,
    val error: String? = null,
)

data class CreateGroupRequest(
    val name: String,
    @SerializedName("user_ids") val userIds: List<String>,
)

data class CreateGroupResponse(
    @SerializedName("conversation_id") val conversationId: String? = null,
    val error: String? = null,
)

data class RenameGroupRequest(
    val name: String,
)

data class RenameGroupResponse(
    val ok: Boolean? = null,
    val name: String? = null,
    val error: String? = null,
)

data class AddGroupMembersRequest(
    @SerializedName("user_ids") val userIds: List<String>,
)

data class AddGroupMembersResponse(
    val ok: Boolean? = null,
    val added: List<GroupMemberDto>? = null,
    val error: String? = null,
)

// NOT: remove/leave {"ok":true}/{"error":"..."} şekli döner — SimpleOkResponse
// (yukarıda tanımlı) reuse edilir, ayrı bir yanıt DTO'su İCAT EDİLMEDİ.

/** toggle-admin, SimpleOkResponse'tan FARKLI olarak güncel is_admin durumunu da
 * döner (native taraf local state'i sunucudan gelen değerle senkron tutar). */
data class ToggleAdminResponse(
    val ok: Boolean? = null,
    @SerializedName("is_admin") val isAdmin: Boolean? = null,
    val error: String? = null,
)

// ---- Etkileşimler: beğeni + yorum (app/api_v1.py satır ~1582-1801:
// api_toggle_like/api_post_detail/api_add_comment okunarak doğrulandı — Faz 4,
// native Android). BİLİNÇLİ SINIR: sticker/GIF yorum GÖNDERME, yorum düzenleme/
// silme, yorum TEPKİSİ (comment_reactions) ve yorum BEĞENME (comment_likes)'e
// aksiyon yok — bunlar VAR OLAN veri olarak modellenir (sticker/reactions
// alanları) ama bu turda render edilmez.

data class LikeRequest(
    val reaction: String? = null,
)

data class ToggleLikeResponse(
    val liked: Boolean = false,
    val reaction: String? = null,
    val count: Int = 0,
    val error: String? = null,
)

/** stickers tablosundan {id, image_url} özeti — bu turda render edilmiyor. */
data class CommentStickerDto(
    val id: String?,
    @SerializedName("image_url") val imageUrl: String?,
)

/** comment_reactions'tan {reaction, count, mine} özetine indirgenmiş satır —
 * MessageReactionDto ile AYNI şekil, bu turda RENDER edilmiyor (kapsam dışı:
 * yorum tepkisi VERME). */
data class CommentReactionDto(
    val reaction: String,
    val count: Int = 0,
    val mine: Boolean = false,
)

/** api_post_detail()'in yorum satırı — `replies` kendi kendine referans verir
 * (Gson bunu native destekler), ama yanıtların KENDİ replies'ı yok (backend
 * sadece TEK seviye iç içe geçirir — bkz. top_comments/tc["replies"] mantığı). */
data class CommentDto(
    val id: String,
    @SerializedName("post_id") val postId: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    val content: String?,
    @SerializedName("parent_comment_id") val parentCommentId: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val profiles: MessageSenderDto? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("liked_by_me") val likedByMe: Boolean = false,
    val sticker: CommentStickerDto? = null,
    val reactions: List<CommentReactionDto>? = null,
    val replies: List<CommentDto>? = null,
)

data class PostDetailResponse(
    val post: PostDto? = null,
    val comments: List<CommentDto>? = null,
    val error: String? = null,
)

data class AddCommentRequest(
    val content: String,
    @SerializedName("parent_comment_id") val parentCommentId: String? = null,
)

data class AddCommentResponse(
    val comment: CommentDto? = null,
    val error: String? = null,
)

// ---- Post oluşturma (app/api_v1.py api_create_post() — Faz 4, native Android
// içerik-oluşturma. BİLİNÇLİ SINIR: çoklu görsel/video/GIF/anket/taslak/konum
// YOK — sadece metin + TEK opsiyonel görsel + görünürlük.) ----

/** POST /api/v1/posts (multipart/form-data) yanıtı — PostDto ile AYNI şekil. */
data class CreatePostResponse(
    val post: PostDto? = null,
    val error: String? = null,
)

// ---- Profil Ayarları (app/api_v1.py "# ----------------------- PROFİL AYARLARI"
// başlığı altındaki api_profile_edit()/api_notification_preferences()/
// api_close_friends_list()/api_add_close_friend()/api_remove_close_friend()/
// api_deactivate_account() okunarak doğrulandı — Faz 4, native Android.
// BİLİNÇLİ SINIR (backend'de de aynı sınır): 2FA enroll/verify/disable, aktif
// oturum listesi/uzaktan-çıkış, şifre değiştirme/sıfırlama YOK. ----

/** POST /profile/edit (multipart/form-data) yanıtı — ProfileDto var olan tipi
 * reuse eder, alan adları zaten uyumlu (bkz. _serialize_profile_for_api ile
 * BİREBİR aynı 7 alanı döndüren api_profile_edit() jsonify'ı). */
data class EditProfileResponse(
    val ok: Boolean = false,
    val profile: ProfileDto? = null,
    val error: String? = null,
)

/** GET/POST /notifications/preferences gövdesi — NOTIFICATION_TYPES'taki (app/
 * notifications.py) 13 kolonun BİREBİR aynısı, snake_case @SerializedName ile. */
data class NotificationPreferencesDto(
    @SerializedName("notify_like") val notifyLike: Boolean = true,
    @SerializedName("notify_comment") val notifyComment: Boolean = true,
    @SerializedName("notify_reply") val notifyReply: Boolean = true,
    @SerializedName("notify_comment_like") val notifyCommentLike: Boolean = true,
    @SerializedName("notify_comment_reaction") val notifyCommentReaction: Boolean = true,
    @SerializedName("notify_follow") val notifyFollow: Boolean = true,
    @SerializedName("notify_follow_request") val notifyFollowRequest: Boolean = true,
    @SerializedName("notify_follow_accept") val notifyFollowAccept: Boolean = true,
    @SerializedName("notify_message") val notifyMessage: Boolean = true,
    @SerializedName("notify_mention") val notifyMention: Boolean = true,
    @SerializedName("notify_hashtag_post") val notifyHashtagPost: Boolean = true,
    @SerializedName("notify_story_reaction") val notifyStoryReaction: Boolean = true,
    @SerializedName("notify_repost") val notifyRepost: Boolean = true,
)

data class NotificationPreferencesResponse(
    val preferences: NotificationPreferencesDto? = null,
    val error: String? = null,
)

/** GET /close-friends yanıtı — profiles satırı UserSearchDto ile AYNI şekil
 * (id/username/avatar_url/full_name), ayrı bir DTO İCAT EDİLMEDİ. */
data class CloseFriendsResponse(
    val users: List<UserSearchDto>? = null,
    val error: String? = null,
)

data class AddCloseFriendRequest(
    @SerializedName("user_id") val userId: String,
)

data class DeactivateAccountRequest(
    val password: String? = null,
)

// NOT: /notifications/preferences POST, /close-friends/add, /close-friends/
// {id}/remove ve /profile/deactivate hepsi {"ok": true}/{"error": "..."} şekli
// döner — SimpleOkResponse (yukarıda tanımlı) reuse edilir, ayrı bir yanıt
// DTO'su İCAT EDİLMEDİ.

// ---- Aktif Oturumlar (native cihazlar) (app/api_v1.py GET /sessions,
// POST /sessions/{id}/revoke, POST /sessions/revoke-others — backend ZATEN
// tamamlandı/pushlandı (commit c96072f), bu SADECE native tüketim tarafı.
// ÖNEMLİ KAVRAMSAL FARK: web'in "Aktif Oturumlar" (user_sessions, tarayıcı
// session'ları) özelliğinin KAVRAMSAL karşılığı ama FARKLI bir mekanizma —
// bu, native'in KENDİ bearer token sistemi (api_tokens, her satır = bir
// cihazın girişi) üzerinde çalışır. Yani "hesabına giriş yapmış BAŞKA NATIVE
// CİHAZLAR" listesi. revoke/revoke-others zaten var olan SimpleOkResponse'u
// reuse eder (sadece ok/error dönüyorlar) — use_logout/forbidden gibi hata
// kodları için ayrı bir tip İCAT EDİLMEDİ, error alanı yeterli.

data class SessionDto(
    val id: String,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("last_used_at") val lastUsedAt: String?,
    @SerializedName("is_current") val isCurrent: Boolean = false,
)

data class SessionsResponse(
    val sessions: List<SessionDto>? = null,
    val error: String? = null,
)

// ---- 2FA (TOTP) yönetimi (app/api_v1.py 2fa/status, 2fa/enroll,
// 2fa/enroll/verify, 2fa/disable — backend sözleşmesi görev tanımından birebir
// alındı, Faz 4 native Android "Güvenlik (2FA)" ekranı). BİLİNÇLİ SINIR: login
// akışının 2FA-kod isteme kısmı (LoginRequest.code, mfa_required) AYRI bir
// iterasyonda zaten yapıldı — bu SADECE Ayarlar'dan enroll/disable. qr_code
// alanı gerçek Supabase'den dönen ~360KB'lık data:image/svg+xml SVG string'i —
// BU İTERASYONDA render edilmiyor (kapsam dışı: yeni bir coil-svg bağımlılığı +
// büyük SVG parse riski), sadece secret metni okunabilir biçimde gösterilir.

data class TwoFactorStatusResponse(
    val enabled: Boolean? = null,
    val error: String? = null,
)

data class TwoFactorEnrollRequest(
    val password: String,
)

/** [qrCode] JSON'dan kayıpsız geçsin diye modele alınıyor ama hiçbir UI kodu bu
 * alanı OKUMAZ/render ETMEZ — sadece [secret] kullanılır (bkz. yukarıdaki
 * bölüm notu). */
data class TwoFactorEnrollResponse(
    @SerializedName("factor_id") val factorId: String? = null,
    val secret: String? = null,
    @SerializedName("qr_code") val qrCode: String? = null,
    val error: String? = null,
)

data class TwoFactorVerifyRequest(
    val password: String,
    @SerializedName("factor_id") val factorId: String,
    val code: String,
)

/** disable, password VE code'u AYNI istekte BİRLİKTE ister — Supabase'in "AAL2
 * required to unenroll" kısıtlaması yüzünden password-only asla çalışmaz,
 * backend bunu tek istekte çözer, native taraf sadece iki alanı birlikte
 * gönderir (iki ayrı adım DEĞİL). */
data class TwoFactorDisableRequest(
    val password: String,
    val code: String,
)

// ---- Bildirimler (app/api_v1.py satır ~2785-2833: api_list_notifications()/
// api_unread_notifications_count() okunarak doğrulandı — backend zaten
// commit 55833b2'de tamamlandı, bu SADECE native tüketim tarafı). KRİTİK
// SAPMA: web'in _group_notifications()'ı bir `target_url` (Flask url_for()
// string'i) üretir, native bunu KULLANAMAZ — bu yüzden native uç noktası
// ("/notifications"nin AYNISI ama farklı jsonify alanları) `target_url`
// yerine HAM alanları (post_id/username/conversation_id/hashtag) döner,
// navigasyon kararını native kendi route'larıyla verir (bkz. AppNavHost.kt
// "notifications" route'u + NotificationsScreen.kt resolveTarget()).
//
// Alanlardan SADECE tipine uygun olanlar dolu gelir, diğerleri null:
// post_id -> like/comment/reply/comment_like/comment_reaction/hashtag_post/
// repost/mention(post'a aitse); username -> SADECE follow/follow_accept/
// story_reaction (profile'a gidilecek türler); conversation_id -> message/
// mention(post'a ait değilse); hashtag -> hashtag_post (etiket adı — hashtag
// sayfası eklendiğinden beri bu tip "hashtag/{tag}" route'una gider, bkz.
// NotificationsScreen.kt resolveNotificationTarget()); follow_request tipinde
// yukarıdakilerin HİÇBİRİ dolu değil, tıklanınca native'in zaten var olan
// "followRequests" route'una gidilir.
data class NotificationDto(
    val type: String,
    @SerializedName("actor_summary") val actorSummary: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val text: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("is_read") val isRead: Boolean = false,
    @SerializedName("post_id") val postId: String? = null,
    val username: String? = null,
    @SerializedName("conversation_id") val conversationId: String? = null,
    val hashtag: String? = null,
)

data class NotificationsResponse(
    val notifications: List<NotificationDto>? = null,
    @SerializedName("has_next") val hasNext: Boolean = false,
    val error: String? = null,
)

/** unread-count 20sn TTL cache'li (bkz. backend docstring'i) — error alanı
 * pratikte hiç dolmaz (sadece 401 unauthorized errorBody'den ayrı parse edilir,
 * bkz. RetrofitClient.parseErrorCode), ama diğer *Response DTO'larıyla AYNI
 * şekil tutarlılığı için burada da tutuldu. */
data class UnreadCountResponse(
    val count: Int = 0,
    val error: String? = null,
)

// ---- Hashtag detay + gündem (Faz 4 sonrası eksik giderme SONUNCUSU, native
// Android) — app/api_v1.py api_hashtag_posts()/api_toggle_hashtag_follow()/
// api_trending() ile birebir eşleşir. Postlar PostDto ile AYNI şekil (feed/
// discover sözleşmesi), ayrı bir model YOK. ----

data class HashtagPostsResponse(
    val posts: List<PostDto>? = null,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    val error: String? = null,
)

// ============================================================
// Faz 5 (2026-07-31) — aşağıdaki bölge işaretleri, paralel ajanların AYNI dosyada
// çakışmadan çalışabilmesi için ayrıldı. Her ajan SADECE kendi işaretli bölgesine
// ekleme yapar; ana oturum dalga kapanışında birleştirir. Bölgeler geniş
// aralıklı bırakıldı (Git diff'lerinin ayrı hunk olarak temiz birleşmesi için).
// ============================================================

// ---- FAZ5: Anket oy verme (Dalga 1A) ----

/** app/polls.py attach_polls()'un ürettiği options[] elemanı — `pct` backend'de
 * ZATEN yuvarlanmış int (round(votes/total*100)), client yeniden HESAPLAMAZ. */
data class PollOptionDto(
    val id: String,
    val text: String,
    val votes: Int = 0,
    val pct: Int = 0,
)

/** attach_polls()'un post'a eklediği `poll` alanı (anketi olmayan postta null).
 * enrich_post_json() RPC yolu da BİREBİR aynı şekli üretir. */
data class PollDto(
    val id: String,
    val options: List<PollOptionDto>? = null,
    @SerializedName("total_votes") val totalVotes: Int = 0,
    @SerializedName("my_vote") val myVote: String? = null,
)

/** POST /polls/{id}/vote yanıtı — `poll` sarmalayıcısı YOK, alanlar düz gelir
 * (api_v1/polls.py `jsonify(options=..., total_votes=..., my_vote=...)`). */
data class VotePollResponse(
    val options: List<PollOptionDto>? = null,
    @SerializedName("total_votes") val totalVotes: Int = 0,
    @SerializedName("my_vote") val myVote: String? = null,
    val error: String? = null,
)

/** POST /polls/{id}/vote gövdesi — web form-encoded, api_v1 JSON (tek sapma). */
data class VotePollRequest(
    @SerializedName("option_id") val optionId: String,
)

// ---- FAZ5: Mesaj gelişmiş işlemleri (Dalga 1B) ----
// app/api_v1/messaging.py "Mesaj gelişmiş işlemleri (Faz 5 Dalga 1B)" bölümü
// (delete/edit/react/pin/forward-targets/forward/mute/search) ile birebir
// eşleşir — durum kodları/hata metinleri o dosyanın docstring'lerinde
// belgelendi, burada TEKRARLANMADI. Konuşma sessize alma (mute) BİLİNÇLİ
// olarak burada (ayrı bir "Dalga 2A" bölgesi değil) — backend'de AYNI dosya/
// dalgada uygulandığı için native tarafta da birlikte taşındı.

/** POST /messages/{id}/edit gövdesi. */
data class EditMessageRequest(
    val content: String,
)

/** POST /messages/{id}/edit yanıtı — ok=false ise error dolu (empty/too_long/
 * not_found), ok=true ise content/editedAt her zaman dolu (editedAt SADECE
 * migration uygulanmamışsa null kalabilir, bkz. backend docstring'i). */
data class EditMessageResponse(
    val ok: Boolean = false,
    val content: String? = null,
    @SerializedName("edited_at") val editedAt: String? = null,
    val error: String? = null,
)

/** POST /messages/{id}/react gövdesi. */
data class ReactRequest(
    val reaction: String,
)

/** POST /messages/{id}/react yanıtı — `reaction` null ise tepki KALDIRILDI
 * (aynı emoji tekrar gönderilince 3'lü toggle'ın "sil" dalı), dolu ise
 * eklendi/değiştirildi demektir. Katılımcı olmayana/yoksa 403 DEĞİL 404
 * döner (bkz. backend docstring'i — bilinçli enumeration koruması, native
 * tarafta SADECE hata metnine çevrilir, "düzeltilmez"). */
data class ReactResponse(
    val ok: Boolean = false,
    val reaction: String? = null,
    val error: String? = null,
)

/** POST /messages/{id}/pin yanıtı — yetki GÖNDEREN değil konuşmanın HERHANGİ
 * bir katılımcısı (bkz. backend docstring'i). */
data class PinResponse(
    val ok: Boolean = false,
    val pinned: Boolean = false,
    val error: String? = null,
)

/** POST /messages/conversations/{id}/mute yanıtı — SADECE çağıranın kendi
 * katılımcı satırını değiştirir (başkasını sessize almak diye bir şey yok). */
data class MuteConversationResponse(
    val ok: Boolean = false,
    val muted: Boolean = false,
    val error: String? = null,
)

/** GET /messages/forward-targets satırı — name grup adı / karşı kullanıcı
 * adı / "Bilinmeyen" (backend _build_convos() üzerinden türetilir). */
data class ForwardTargetDto(
    val id: String,
    val name: String?,
)

data class ForwardTargetsResponse(
    val targets: List<ForwardTargetDto>? = null,
    val error: String? = null,
)

/** POST /messages/{id}/forward gövdesi. */
data class ForwardMessageRequest(
    @SerializedName("target_conversation_id") val targetConversationId: String,
)

/** POST /messages/{id}/forward yanıtı — SimpleOkResponse'tan FARKLI olarak
 * conversation_id de döner (native, iletilen hedef sohbete gidebilsin diye) —
 * bu yüzden ayrı bir DTO. */
data class ForwardMessageResponse(
    val ok: Boolean = false,
    @SerializedName("conversation_id") val conversationId: String? = null,
    val error: String? = null,
)

/** Sohbet-içi (GET /messages/conversations/{id}/search) VE global (GET
 * /messages/search) arama satırı — AYNI şekil, TEK FARK: conversationId
 * SADECE global aramada dolu gelir (backend _serialize_search_row()'un
 * with_conversation bayrağı), sohbet-içi aramada hiç yoktur -> nullable. */
data class MessageSearchResultDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String? = null,
    val content: String?,
    @SerializedName("created_at") val createdAt: String?,
    val mine: Boolean = false,
    val sender: String?,
)

data class MessageSearchResponse(
    val results: List<MessageSearchResultDto>? = null,
    @SerializedName("has_next") val hasNext: Boolean = false,
    @SerializedName("next_offset") val nextOffset: Int? = null,
    val error: String? = null,
)

// ---- FAZ5: Şifre sıfırlama + Sticker backend/API (Dalga 1C) ----

/** POST /auth/forgot-password gövdesi — yanıt HER ZAMAN {"ok":true}
 * (kullanıcı-enumeration koruması, bkz. api_v1/auth.py api_forgot_password),
 * bu yüzden AYRI bir yanıt DTO'su YOK: SimpleOkResponse reuse edilir.
 * Kullanıcı sıfırlamayı e-postadaki linkle MEVCUT WEB SAYFASINDA (mobil
 * tarayıcı) tamamlar — uygulama-içi sıfırlama (deep link) kapsam DIŞI. */
data class ForgotPasswordRequest(
    val email: String,
)

/** GET /stickers/mine elemanı. `mine_created` SUNUCUDA hesaplanır
 * (creator_id == me) — client creator_id'yi hiç görmez. GET /stickers/{id}
 * de AYNI tipe parse edilir, orada `mine_created` gelmez (false kalır —
 * o uç nokta sadece {id, image_url} döner). */
data class StickerDto(
    val id: String,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("mine_created") val mineCreated: Boolean = false,
)

data class StickersResponse(
    val stickers: List<StickerDto>? = null,
    val error: String? = null,
)

/** POST /stickers/new yanıtı (201) — {"id","image_url"}; `mine_created` YOK
 * (oluşturan zaten sensin), bu yüzden StickerDto'ya parse EDİLMEZ. */
data class CreateStickerResponse(
    val id: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    val error: String? = null,
)

// NOT: save/remove {"ok":true}/{"error":"..."} şekli döner — SimpleOkResponse
// (yukarıda tanımlı) reuse edilir, ayrı bir yanıt DTO'su İCAT EDİLMEDİ.

// ---- FAZ5: Sessize alma (Dalga 2A) ----

// ---- FAZ5: Video/Reel paylaşma (Dalga 2B) ----

// ---- FAZ5: Hikayeler (Dalga 2C) ----

// ---- FAZ5: Bookmark + Koleksiyon (Dalga 3A) ----

// ---- FAZ5: GIF + Sticker tüketicileri (Dalga 3B) ----

// ---- FAZ5: Push/FCM (Dalga 4A) ----

data class ToggleHashtagFollowResponse(
    val following: Boolean = false,
    val error: String? = null,
)

/** GET /trending — {tag, count} şekli HashtagSearchDto (arama sonuçlarındaki)
 * ile AYNI, ayrı bir DTO İCAT EDİLMEDİ. */
data class TrendingResponse(
    val tags: List<HashtagSearchDto>? = null,
    val error: String? = null,
)
