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
}
