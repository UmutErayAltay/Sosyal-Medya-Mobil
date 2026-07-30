package com.umuterayaltay.sosyal.nativeapp

import android.content.Context
import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.data.local.AppDatabase
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.DiscoverApi
import com.umuterayaltay.sosyal.nativeapp.network.FeedApi
import com.umuterayaltay.sosyal.nativeapp.network.MessagingApi
import com.umuterayaltay.sosyal.nativeapp.network.ProfileApi
import com.umuterayaltay.sosyal.nativeapp.network.ReelsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.repository.AuthRepository
import com.umuterayaltay.sosyal.nativeapp.repository.DiscoverRepository
import com.umuterayaltay.sosyal.nativeapp.repository.FeedRepository
import com.umuterayaltay.sosyal.nativeapp.repository.MessagingRepository
import com.umuterayaltay.sosyal.nativeapp.repository.ProfileRepository
import com.umuterayaltay.sosyal.nativeapp.repository.ReelsRepository

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
    lateinit var discoverRepository: DiscoverRepository
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var reelsRepository: ReelsRepository
        private set
    lateinit var messagingRepository: MessagingRepository
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext

        tokenStore = TokenStore(appContext)
        val retrofit = RetrofitClient.create(tokenStore)
        val authApi = retrofit.create(AuthApi::class.java)
        val feedApi = retrofit.create(FeedApi::class.java)
        val discoverApi = retrofit.create(DiscoverApi::class.java)
        val profileApi = retrofit.create(ProfileApi::class.java)
        val reelsApi = retrofit.create(ReelsApi::class.java)
        val messagingApi = retrofit.create(MessagingApi::class.java)
        val database = AppDatabase.getInstance(appContext)

        authRepository = AuthRepository(authApi, tokenStore)
        feedRepository = FeedRepository(feedApi, database.postDao())
        discoverRepository = DiscoverRepository(discoverApi)
        profileRepository = ProfileRepository(profileApi)
        reelsRepository = ReelsRepository(reelsApi)
        messagingRepository = MessagingRepository(messagingApi)

        initialized = true
    }
}
