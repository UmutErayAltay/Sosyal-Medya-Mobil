package com.umuterayaltay.sosyal.nativeapp.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Kaydedilenler + koleksiyon (Faz 5, Dalga 3A) — app/api_v1/bookmarks.py
 * api_toggle_bookmark()/api_list_collections()/api_create_collection()/
 * api_delete_collection()/api_set_bookmark_collection() ile birebir eşleşir.
 * MutesApi.toggleMutePost() ile AYNI desen: gövdeli POST'larda JSON body.
 */
interface BookmarksApi {
    @POST("posts/{postId}/bookmark")
    suspend fun toggleBookmark(
        @Path("postId") postId: String,
        @Body body: ToggleBookmarkRequest = ToggleBookmarkRequest(),
    ): Response<ToggleBookmarkResponse>

    @GET("collections")
    suspend fun getCollections(): Response<CollectionsResponse>

    @POST("collections")
    suspend fun createCollection(@Body body: CreateCollectionRequest): Response<CreateCollectionResponse>

    @POST("collections/{collectionId}/delete")
    suspend fun deleteCollection(@Path("collectionId") collectionId: String): Response<SimpleOkResponse>

    @POST("posts/{postId}/bookmark/collection")
    suspend fun setBookmarkCollection(
        @Path("postId") postId: String,
        @Body body: SetBookmarkCollectionRequest,
    ): Response<SimpleOkResponse>
}
