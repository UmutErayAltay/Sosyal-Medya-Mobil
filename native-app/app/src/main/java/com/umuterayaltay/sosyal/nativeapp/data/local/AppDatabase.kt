package com.umuterayaltay.sosyal.nativeapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// version 1 -> 2: PostEntity'ye videoUrl kolonu eklendi (Reels ekranı için,
// bkz. repository/Post.kt).
// version 2 -> 3: PostEntity'ye likedByMe kolonu eklendi (Faz 4 — beğeni
// aksiyonu, bkz. repository/Post.kt/InteractionsRepository).
// version 3 -> 4: PostEntity'ye mutedByMe kolonu eklendi (Faz 5 Dalga 2A —
// post sessize alma, bkz. repository/Post.kt PostEntity.toDomain() yorumu).
// version 4 -> 5: PostEntity'ye bookmarkedByMe kolonu eklendi (Faz 5 Dalga 3A
// — kaydetme, mutedByMe ile AYNI gerekçe).
// version 5 -> 6: PostEntity'ye imageUrls kolonu eklendi (kullanıcı raporu:
// "çoklu görseller ana sayfada tek görsel gözüküyor" — bkz. PostEntity.kt yorumu).
// Bu tablo SADECE bir cache (FeedRepository'nin "önce cache göster, sonra
// ağdan tazele" deseni) - gerçek kullanıcı verisi değil, bu yüzden migration
// yazmak yerine fallbackToDestructiveMigration() tercih edildi: şema
// uyuşmazlığında tablo silinip network'ten yeniden doldurulur, kalıcı veri
// kaybı riski yok.
@Database(entities = [PostEntity::class], version = 6, exportSchema = false)
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
                    // Room 2.8.4'e yükseltilince (bkz. app/build.gradle.kts yorumu —
                    // Kotlin 2.1.20 uyumluluğu için) no-arg overload deprecated oldu;
                    // dropAllTables=true ESKİ varsayılan davranışla BİREBİR aynı
                    // (şema uyuşmazlığında tüm tablolar silinip yeniden doldurulur).
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
