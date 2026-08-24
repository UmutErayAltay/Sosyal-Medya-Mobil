package com.umuterayaltay.sosyal.nativeapp.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Hikayeler (Stories) — app/api_v1/stories.py ile birebir eşleşir (Faz 5,
 * Dalga 2C). app/stories.py'nin (web) BİREBİR mirror'ı, yeni bir davranış
 * İCAT EDİLMEDİ. Route seçimi: backend `POST /stories` (web'in `/stories/
 * new`'i yerine `/posts`, `/reels` ile TUTARLI api_v1 konvansiyonu).
 *
 * `createStory` multipart/form-data — InteractionsApi.createPost() ile AYNI
 * kodlama gerekçesi (opsiyonel görsel/video dosyası içeriyor). `image`/`video`
 * null geçilirse Retrofit bu parçayı isteğe hiç eklemez.
 */
interface StoriesApi {

    @GET("stories/bar")
    suspend fun getStoriesBar(): Response<StoryBarResponse>

    @GET("stories/user/{userId}")
    suspend fun getUserStories(@Path("userId") userId: String): Response<UserStoriesResponse>

    // 2026-08-22 (kullanıcı isteği: "storyler 24 saat sonra siliniyor ama
    // paylaşan kişi arşivinde görsün") — çağıranın KENDİ süresi dolmuş
    // hikayeleri, user_id parametresi YOK (her zaman kendi arşivi).
    // Response şekli getUserStories ile AYNI ({"stories":[...]}), sadece
    // username/avatar_url/is_mine alanları YOK — UserStoriesResponse'un
    // hepsi varsayılan değerli olduğu için AYNI DTO güvenle reuse edilir.
    @GET("stories/archive")
    suspend fun getStoryArchive(): Response<UserStoriesResponse>

    @Multipart
    @POST("stories")
    suspend fun createStory(
        @Part("caption") caption: RequestBody,
        @Part image: MultipartBody.Part?,
        @Part video: MultipartBody.Part?,
        @Part("visibility") visibility: RequestBody,
        @Part("background_color") backgroundColor: RequestBody?,
        @Part("caption_position_x") captionPositionX: RequestBody?,
        @Part("caption_position_y") captionPositionY: RequestBody?,
        // 2026-08-11 (kullanıcı isteği: "metin stili/rengi seçenekleri") —
        // null/absent = klasik (backend zaten böyle davranıyor), "pill_light"/
        // "pill_dark" dışındaki değerleri backend fail-open null'a düşürür.
        @Part("caption_style") captionStyle: RequestBody?,
        // 2026-08-22 (kullanıcı isteği: "yazının kendi rengi seçilebilsin") —
        // captionStyle'dan BAĞIMSIZ, background_color ile AYNI serbest hex
        // regex'i (bkz. app/api_v1/stories.py) — sabit bir liste YOK, native
        // UI'da 9 renk sunuluyor ama backend bunu zorlamıyor.
        @Part("caption_color") captionColor: RequestBody?,
        @Part("poll_option_1") pollOption1: RequestBody?,
        @Part("poll_option_2") pollOption2: RequestBody?,
        @Part("poll_option_3") pollOption3: RequestBody?,
        @Part("poll_option_4") pollOption4: RequestBody?,
        @Part("poll_position_x") pollPositionX: RequestBody?,
        @Part("poll_position_y") pollPositionY: RequestBody?,
        @Part("poll_scale") pollScale: RequestBody?,
        // 2026-08-10 (kullanıcı raporu: "2.ye tıklayınca öncekini siliyor")
        // — İLK sürüm (2026-08-09) TEKİL overlay_image_url/position_x/
        // position_y/scale alanlarıydı, çoklu GIF/sticker'ı DESTEKLEMİYORDU.
        // Backend'de o alanlar için henüz gerçek veri yokken temiz
        // değiştirme yapıldı: artık TEK bir `overlay_elements` alanı, JSON-
        // KODLANMIŞ bir dizi string'i (her eleman {url,position_x,position_y,
        // scale}) — dosya DEĞİL, düz metin (gif_url'in post paylaşımındaki
        // AYNI muamelesi). Backend en fazla 3 elemanı kabul ediyor, fazlası
        // sessizce atılıyor (upload_images max_count deseniyle AYNI).
        @Part("overlay_elements") overlayElements: RequestBody?,
    ): Response<CreateStoryResponse>

    @POST("stories/{id}/react")
    suspend fun reactToStory(
        @Path("id") storyId: String,
        @Body body: ReactToStoryRequest,
    ): Response<ReactToStoryResponse>

    @POST("stories/{id}/reply")
    suspend fun replyToStory(
        @Path("id") storyId: String,
        @Body body: ReplyToStoryRequest,
    ): Response<ReplyToStoryResponse>

    @POST("stories/{id}/delete")
    suspend fun deleteStory(@Path("id") storyId: String): Response<SimpleOkResponse>

    // 2026-08-11 (kullanıcı isteği: "hikayeyi kim izledi listesi") — SADECE
    // hikaye sahibi çağırabilir (backend 403 döner değilse), story_views
    // tablosu zaten HER görüntülemede yazılıyordu (halka rengi için), bu
    // sadece o veriyi OKUYAN ilk endpoint.
    @GET("stories/{id}/viewers")
    suspend fun getStoryViewers(@Path("id") storyId: String): Response<StoryViewersResponse>

    @POST("stories/{id}/save-highlight")
    suspend fun saveHighlight(
        @Path("id") storyId: String,
        @Body body: SaveHighlightRequest,
    ): Response<SaveHighlightResponse>

    @GET("stories/highlights/{userId}")
    suspend fun getHighlights(@Path("userId") userId: String): Response<HighlightsResponse>

    @GET("stories/highlights/{id}/view")
    suspend fun viewHighlight(@Path("id") highlightId: String): Response<HighlightViewResponse>

    @POST("stories/highlights/{id}/delete")
    suspend fun deleteHighlight(@Path("id") highlightId: String): Response<SimpleOkResponse>

    /** app/api_v1/stories.py api_update_highlight() ile birebir eşleşir —
     * title/coverUrl'den en az biri dolu olmalı (backend nothing_to_update
     * ile 400 döner). Native UI'dan bu turda SADECE title gönderiliyor (kapak
     * değiştirme kapsam dışı, bkz. HighlightsScreen.kt yorumu). */
    @POST("stories/highlights/{id}/update")
    suspend fun updateHighlight(
        @Path("id") highlightId: String,
        @Body body: UpdateHighlightRequest,
    ): Response<SimpleOkResponse>
}
