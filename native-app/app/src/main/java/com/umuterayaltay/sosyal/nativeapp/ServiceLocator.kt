package com.umuterayaltay.sosyal.nativeapp

import android.content.Context
import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.data.local.AppDatabase
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.FeedApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.repository.AuthRepository
import com.umuterayaltay.sosyal.nativeapp.repository.FeedRepository

/**
 * Bu MVP fazında Hilt/Dagger yerine elle (manual) DI — kotlinx.serialization'ı
 * atlama gerekçesiyle aynı ("ek compiler plugin karmaşası bu fazda gereksiz").
 * SosyalApplication.onCreate()'te bir kez init() edilir.
 */
object ServiceLocator {

    lateinit var tokenStore: TokenStore
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var feedRepository: FeedRepository
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext

        tokenStore = TokenStore(appContext)
        val retrofit = RetrofitClient.create(tokenStore)
        val authApi = retrofit.create(AuthApi::class.java)
        val feedApi = retrofit.create(FeedApi::class.java)
        val database = AppDatabase.getInstance(appContext)

        authRepository = AuthRepository(authApi, tokenStore)
        feedRepository = FeedRepository(feedApi, database.postDao())

        initialized = true
    }
}
