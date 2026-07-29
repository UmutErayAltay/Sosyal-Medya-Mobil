package com.umuterayaltay.sosyal.nativeapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version 1 -> 2: PostEntity'ye videoUrl kolonu eklendi (Reels ekranı için,
// bkz. repository/Post.kt). Bu tablo SADECE bir cache (FeedRepository'nin
// "önce cache göster, sonra ağdan tazele" deseni) - gerçek kullanıcı verisi
// değil, bu yüzden migration yazmak yerine fallbackToDestructiveMigration()
// tercih edildi: şema uyuşmazlığında tablo silinip network'ten yeniden
// doldurulur, kalıcı veri kaybı riski yok.
@Database(entities = [PostEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sosyal_native.db",
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
