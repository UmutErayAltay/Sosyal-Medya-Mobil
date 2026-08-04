package com.umuterayaltay.sosyal.nativeapp

import android.content.Context
import com.umuterayaltay.sosyal.nativeapp.data.TokenStore
import com.umuterayaltay.sosyal.nativeapp.data.local.AppDatabase
import com.umuterayaltay.sosyal.nativeapp.network.AuthApi
import com.umuterayaltay.sosyal.nativeapp.network.DiscoverApi
import com.umuterayaltay.sosyal.nativeapp.network.FeedApi
import com.umuterayaltay.sosyal.nativeapp.network.HashtagApi
import com.umuterayaltay.sosyal.nativeapp.network.InteractionsApi
import com.umuterayaltay.sosyal.nativeapp.network.MessagingApi
import com.umuterayaltay.sosyal.nativeapp.network.MutesApi
import com.umuterayaltay.sosyal.nativeapp.network.NotificationsApi
import com.umuterayaltay.sosyal.nativeapp.network.ProfileApi
import com.umuterayaltay.sosyal.nativeapp.network.ReelsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SettingsApi
import com.umuterayaltay.sosyal.nativeapp.repository.AuthRepository
import com.umuterayaltay.sosyal.nativeapp.repository.DiscoverRepository
import com.umuterayaltay.sosyal.nativeapp.repository.FeedRepository
import com.umuterayaltay.sosyal.nativeapp.repository.HashtagRepository
import com.umuterayaltay.sosyal.nativeapp.repository.InteractionsRepository
import com.umuterayaltay.sosyal.nativeapp.repository.MessagingRepository
import com.umuterayaltay.sosyal.nativeapp.repository.NotificationsRepository
import com.umuterayaltay.sosyal.nativeapp.repository.ProfileRepository
import com.umuterayaltay.sosyal.nativeapp.repository.RealtimeConnectionManager
import com.umuterayaltay.sosyal.nativeapp.repository.ReelsRepository
import com.umuterayaltay.sosyal.nativeapp.repository.SettingsRepository
import com.umuterayaltay.sosyal.nativeapp.network.PollsApi
import com.umuterayaltay.sosyal.nativeapp.repository.PollsRepository
import com.umuterayaltay.sosyal.nativeapp.network.StickersApi
import com.umuterayaltay.sosyal.nativeapp.repository.StickersRepository
import com.umuterayaltay.sosyal.nativeapp.repository.MutesRepository
import com.umuterayaltay.sosyal.nativeapp.network.StoriesApi
import com.umuterayaltay.sosyal.nativeapp.repository.StoriesRepository
import com.umuterayaltay.sosyal.nativeapp.network.BookmarksApi
import com.umuterayaltay.sosyal.nativeapp.repository.BookmarksRepository
import com.umuterayaltay.sosyal.nativeapp.network.GifsApi
import com.umuterayaltay.sosyal.nativeapp.repository.GifsRepository
import com.umuterayaltay.sosyal.nativeapp.network.PushApi
import com.umuterayaltay.sosyal.nativeapp.repository.PushRepository
// FAZ5_IMPORTS_MARKER — her ajan kendi Api/Repository import'unu bu satırın
// HEMEN ÜSTÜNE ekler (dalga başına ayrı ajan çakışmasın diye).

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
    lateinit var notificationsRepository: NotificationsRepository
        private set
    lateinit var interactionsRepository: InteractionsRepository
        private set
    lateinit var hashtagRepository: HashtagRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var realtimeConnectionManager: RealtimeConnectionManager
        private set
    lateinit var pollsRepository: PollsRepository
        private set
    lateinit var stickersRepository: StickersRepository
        private set
    lateinit var mutesRepository: MutesRepository
        private set
    lateinit var storiesRepository: StoriesRepository
        private set
    lateinit var bookmarksRepository: BookmarksRepository
        private set
    lateinit var gifsRepository: GifsRepository
        private set
    lateinit var pushRepository: PushRepository
        private set
    // FAZ5_LATEINIT_MARKER — her ajan kendi `lateinit var xxxRepository`'sini
    // bu satırın HEMEN ÜSTÜNE ekler.

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
        val notificationsApi = retrofit.create(NotificationsApi::class.java)
        val interactionsApi = retrofit.create(InteractionsApi::class.java)
        val settingsApi = retrofit.create(SettingsApi::class.java)
        val hashtagApi = retrofit.create(HashtagApi::class.java)
        val database = AppDatabase.getInstance(appContext)

        authRepository = AuthRepository(authApi, tokenStore)
        feedRepository = FeedRepository(feedApi, database.postDao())
        discoverRepository = DiscoverRepository(discoverApi)
        profileRepository = ProfileRepository(profileApi)
        reelsRepository = ReelsRepository(reelsApi)
        messagingRepository = MessagingRepository(messagingApi)
        notificationsRepository = NotificationsRepository(notificationsApi)
        interactionsRepository = InteractionsRepository(interactionsApi)
        settingsRepository = SettingsRepository(settingsApi)
        hashtagRepository = HashtagRepository(hashtagApi)
        // authRepository'ye bağımlı — /realtime-token onun üzerinden çağrılıyor
        // (bkz. RealtimeConnectionManager sınıf yorumu).
        realtimeConnectionManager = RealtimeConnectionManager(authRepository)

        val pollsApi = retrofit.create(PollsApi::class.java)
        pollsRepository = PollsRepository(pollsApi)

        val stickersApi = retrofit.create(StickersApi::class.java)
        stickersRepository = StickersRepository(stickersApi)

        val mutesApi = retrofit.create(MutesApi::class.java)
        mutesRepository = MutesRepository(mutesApi)

        val storiesApi = retrofit.create(StoriesApi::class.java)
        storiesRepository = StoriesRepository(storiesApi)

        val bookmarksApi = retrofit.create(BookmarksApi::class.java)
        bookmarksRepository = BookmarksRepository(bookmarksApi)

        val gifsApi = retrofit.create(GifsApi::class.java)
        gifsRepository = GifsRepository(gifsApi)

        val pushApi = retrofit.create(PushApi::class.java)
        pushRepository = PushRepository(pushApi)

        // FAZ5_INIT_MARKER — her ajan kendi `val xxxApi = retrofit.create(...)`
        // + `xxxRepository = XxxRepository(xxxApi)` satırlarını bu satırın
        // HEMEN ÜSTÜNE ekler.

        initialized = true
    }
}
