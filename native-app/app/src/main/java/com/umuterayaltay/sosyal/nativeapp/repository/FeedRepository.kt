package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.data.local.PostDao
import com.umuterayaltay.sosyal.nativeapp.network.FeedApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SuggestedUserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class FeedRefreshResult {
    // Madde 1 (sonsuz kaydırma): backend'in her zaman döndürdüğü has_next/
    // next_cursor artık burada da taşınıyor (öncesinde Success tek bir
    // data object'ti, bu ikisi TAMAMEN yok sayılıyordu). suggestedUsers
    // SADECE refresh()'te (cursor=0) dolu gelir, loadMore()'da her zaman null
    // (madde 2: backend sözleşmesi, bkz. ApiModels.kt SuggestedUserDto yorumu).
    data class Success(
        val hasNext: Boolean,
        val nextCursor: Int?,
        val suggestedUsers: List<SuggestedUserDto>?,
    ) : FeedRefreshResult()
    data class Error(val code: String?) : FeedRefreshResult()
}

private const val FIRST_PAGE_LIMIT = 20

// Madde 10 (önbellek tanı taraması): sonsuz kaydırma (loadMore) tabloya SÜREKLİ
// yeni satır ekler, refresh() (pull-to-refresh) DIŞINDA hiçbir şey onu budamaz
// - kullanıcı uzun süre aşağı kaydırıp pull-to-refresh yapmazsa "posts" tablosu
// sınırsız büyür. Web'in AKSİNE (server-side sayfalama, client hafızası
// tutmuyor) native cache'i bu üst sınırın ÜZERİNE çıkınca en eski satırlar
// budanır (bkz. PostDao.pruneOldest()).
private const val MAX_CACHED_POSTS = 200

/**
 * "Cache önce göster, sonra tazele" deseni: [observePosts] Room'daki mevcut
 * cache'i hemen emit eder (Flow), [refresh] ağdan ilk sayfayı çekip Room'u
 * günceller — Room güncellenince observePosts'a bağlı Compose ekranı otomatik
 * yeniden çizilir (tek yönlü veri akışı, ekstra callback gerekmiyor).
 */
class FeedRepository(
    private val feedApi: FeedApi,
    private val postDao: PostDao,
) {

    // A1 (akış performansı): entity->domain dönüşümü artık Dispatchers.Default'ta
    // (ana thread DEĞİL) çalışıyor — flowOn olmadan bu dönüşüm FeedViewModel'in
    // stateIn(viewModelScope, ...) çağrısını topladığı ana thread'de yapılıyordu,
    // her beğeni/kaydetme/mute TÜM listenin yeniden toDomain()'lenmesini ana
    // thread'de tetikliyordu. distinctUntilChanged Room'un aynı liste için
    // gereksiz emisyonlarını (referans farklı ama içerik aynı) eler.
    fun observePosts(): Flow<List<Post>> =
        postDao.getAll()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    /** InteractionsRepository.toggleLike() sunucu yanıtı geldikten SONRA
     * çağrılır (optimistic DEĞİL) — Room cache'i güncelleyip observePosts()'un
     * otomatik yeniden emit etmesini tetikler. */
    suspend fun updateLikeState(postId: String, likeCount: Int, likedByMe: Boolean) =
        withContext(Dispatchers.IO) { postDao.updateLikeState(postId, likeCount, likedByMe) }

    /** MutesRepository.toggleMutePost() sunucu yanıtı geldikten SONRA çağrılır —
     * updateLikeState() ile AYNI desen. */
    suspend fun updateMuteState(postId: String, mutedByMe: Boolean) =
        withContext(Dispatchers.IO) { postDao.updateMuteState(postId, mutedByMe) }

    /** BookmarksRepository.toggleBookmark() sunucu yanıtı geldikten SONRA çağrılır —
     * updateMuteState() ile AYNI desen. */
    suspend fun updateBookmarkState(postId: String, bookmarkedByMe: Boolean) =
        withContext(Dispatchers.IO) { postDao.updateBookmarkState(postId, bookmarkedByMe) }

    suspend fun refresh(): FeedRefreshResult = withContext(Dispatchers.IO) {
        try {
            val response = feedApi.getFeed(cursor = 0, limit = FIRST_PAGE_LIMIT)
            val body = response.body()
            if (response.isSuccessful && body?.posts != null) {
                val now = System.currentTimeMillis()
                val entities = body.posts.map { it.toEntity(cachedAt = now) }
                postDao.replaceAll(entities)
                FeedRefreshResult.Success(
                    hasNext = body.hasNext,
                    nextCursor = body.nextCursor,
                    suggestedUsers = body.suggestedUsers,
                )
            } else {
                val code = body?.error ?: RetrofitClient.parseErrorCode(response)
                FeedRefreshResult.Error(code)
            }
        } catch (e: IOException) {
            FeedRefreshResult.Error("network_error")
        } catch (e: Exception) {
            FeedRefreshResult.Error("unknown_error")
        }
    }

    /** Madde 1 (sonsuz kaydırma): refresh()'in AKSİNE Room'u TEMİZLEMEZ,
     * sadece yeni sayfayı EKLER — observePosts() zaten TÜM tabloyu createdAt
     * DESC sıralıyor, bu yüzden ekleme otomatik doğru sıraya düşer, ayrı bir
     * "sona ekle" mantığı GEREKMEZ. insertAll()'dan SONRA madde 10'un
     * pruneOldest() budaması çağrılır (tablo sınırsız büyümesin diye). */
    suspend fun loadMore(cursor: Int): FeedRefreshResult = withContext(Dispatchers.IO) {
        try {
            val response = feedApi.getFeed(cursor = cursor, limit = FIRST_PAGE_LIMIT)
            val body = response.body()
            if (response.isSuccessful && body?.posts != null) {
                val now = System.currentTimeMillis()
                val entities = body.posts.map { it.toEntity(cachedAt = now) }
                postDao.appendAndPrune(entities, MAX_CACHED_POSTS)
                FeedRefreshResult.Success(
                    hasNext = body.hasNext,
                    nextCursor = body.nextCursor,
                    suggestedUsers = body.suggestedUsers,
                )
            } else {
                val code = body?.error ?: RetrofitClient.parseErrorCode(response)
                FeedRefreshResult.Error(code)
            }
        } catch (e: IOException) {
            FeedRefreshResult.Error("network_error")
        } catch (e: Exception) {
            FeedRefreshResult.Error("unknown_error")
        }
    }
}
