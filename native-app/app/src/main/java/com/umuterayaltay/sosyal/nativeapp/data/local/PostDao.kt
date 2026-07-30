package com.umuterayaltay.sosyal.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    // created_at ISO-8601 string olduğu için lexicographic DESC sıralama
    // kronolojik sıralamayla eşleşir (backend'in "created_at desc" sırasıyla aynı).
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAll(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    // Faz 4 — beğeni aksiyonu: FeedViewModel.posts, Room'dan gelen bir Flow
    // (stateIn) olduğu için doğrudan .value= ile güncellenemez; sunucudan
    // dönen GERÇEK count/liked burada cache'e yazılır, observeAll() otomatik
    // yeniden emit eder (Discover/Profil/Reels'teki .map{} deseninin Room karşılığı).
    @Query("UPDATE posts SET likeCount = :likeCount, likedByMe = :likedByMe WHERE id = :postId")
    suspend fun updateLikeState(postId: String, likeCount: Int, likedByMe: Boolean)
}
