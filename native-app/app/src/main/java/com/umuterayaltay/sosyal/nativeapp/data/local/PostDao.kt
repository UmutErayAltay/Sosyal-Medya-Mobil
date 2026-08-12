package com.umuterayaltay.sosyal.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PostDao {
    // created_at ISO-8601 string olduğu için lexicographic DESC sıralama
    // kronolojik sıralamayla eşleşir (backend'in "created_at desc" sırasıyla aynı).
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    abstract fun getAll(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    abstract suspend fun clearAll()

    // Faz 4 — beğeni aksiyonu: FeedViewModel.posts, Room'dan gelen bir Flow
    // (stateIn) olduğu için doğrudan .value= ile güncellenemez; sunucudan
    // dönen GERÇEK count/liked burada cache'e yazılır, observeAll() otomatik
    // yeniden emit eder (Discover/Profil/Reels'teki .map{} deseninin Room karşılığı).
    @Query("UPDATE posts SET likeCount = :likeCount, likedByMe = :likedByMe WHERE id = :postId")
    abstract suspend fun updateLikeState(postId: String, likeCount: Int, likedByMe: Boolean)

    // Faz 5 Dalga 2A — post sessize alma: updateLikeState() ile AYNI desen
    // (sunucudan dönen GERÇEK durum cache'e yazılır, observeAll() otomatik
    // yeniden emit eder).
    @Query("UPDATE posts SET mutedByMe = :mutedByMe WHERE id = :postId")
    abstract suspend fun updateMuteState(postId: String, mutedByMe: Boolean)

    // Faz 5 Dalga 3A — kaydetme: updateMuteState() ile AYNI desen.
    @Query("UPDATE posts SET bookmarkedByMe = :bookmarkedByMe WHERE id = :postId")
    abstract suspend fun updateBookmarkState(postId: String, bookmarkedByMe: Boolean)

    // Madde 10 (önbellek tanı taraması, native görev tanımı): sonsuz kaydırma
    // (FeedRepository.loadMore()) tabloya sürekli satır ekler, refresh()
    // (pull-to-refresh) DIŞINDA hiçbir şey budamıyordu - bu yüzden uzun süre
    // aşağı kaydırılıp yenilenmeyen bir oturumda tablo sınırsız büyüyebiliyordu.
    // En yeni :keep satır (createdAt DESC) KORUNUR, geri kalanı silinir.
    @Query(
        "DELETE FROM posts WHERE id NOT IN (SELECT id FROM posts ORDER BY createdAt DESC LIMIT :keep)",
    )
    abstract suspend fun pruneOldest(keep: Int)

    // A2 (çoklu-yazma tek transaction): refresh() ÖNCEDEN clearAll()+insertAll()'ı
    // AYRI iki yazma olarak çağırıyordu — Room'un InvalidationTracker'ı arada
    // gerçek bir emptyList() emit ediyor, observePosts() bunu görüp TÜM
    // LazyColumn'u dispose ediyordu. @Transaction ile ikisi TEK commit'e düşer,
    // InvalidationTracker sadece en dıştaki transaction bitince haber verir.
    @Transaction
    open suspend fun replaceAll(posts: List<PostEntity>) {
        clearAll()
        insertAll(posts)
    }

    // A2 — loadMore() için AYNI gerekçe: insertAll()+pruneOldest() TEK
    // transaction'a toplanır, ara emisyon olmaz.
    @Transaction
    open suspend fun appendAndPrune(posts: List<PostEntity>, keep: Int) {
        insertAll(posts)
        pruneOldest(keep)
    }
}
