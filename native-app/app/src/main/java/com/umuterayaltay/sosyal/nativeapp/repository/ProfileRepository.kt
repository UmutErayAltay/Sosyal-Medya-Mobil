package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.BlockedUserDto
import com.umuterayaltay.sosyal.nativeapp.network.FollowListResponse
import com.umuterayaltay.sosyal.nativeapp.network.FollowUserDto
import com.umuterayaltay.sosyal.nativeapp.network.InsightsResponse
import com.umuterayaltay.sosyal.nativeapp.network.ProfileApi
import com.umuterayaltay.sosyal.nativeapp.network.ProfileResponse
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.SimpleOkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

sealed class ProfileResult {
    data class Success(val data: ProfileResponse) : ProfileResult()
    data class Error(val code: String?) : ProfileResult()
}

sealed class FollowListResult {
    data class Success(val users: List<FollowUserDto>, val title: String?) : FollowListResult()
    data class Error(val code: String?) : FollowListResult()
}

sealed class InsightsResult {
    data class Success(val data: InsightsResponse) : InsightsResult()
    data class Error(val code: String?) : InsightsResult()
}

sealed class ToggleFollowResult {
    data class Success(val following: Boolean, val followersCount: Int, val isPending: Boolean) : ToggleFollowResult()
    data class Error(val code: String?) : ToggleFollowResult()
}

sealed class FollowRequestsResult {
    data class Success(val users: List<FollowUserDto>) : FollowRequestsResult()
    data class Error(val code: String?) : FollowRequestsResult()
}

/** Kabul/red gibi ikincil eylemler icin ortak sonuc - DiscoverRepository'deki
 * SearchActionResult ile AYNI desen. */
sealed class FollowActionResult {
    data object Success : FollowActionResult()
    data class Error(val code: String?) : FollowActionResult()
}

sealed class ToggleBlockResult {
    data class Success(val blocked: Boolean) : ToggleBlockResult()
    data class Error(val code: String?) : ToggleBlockResult()
}

sealed class BlockedUsersResult {
    data class Success(val users: List<BlockedUserDto>) : BlockedUsersResult()
    data class Error(val code: String?) : BlockedUsersResult()
}

/**
 * Profil goruntuleme + takipci/takip listeleri + insights + takip et/birak +
 * takip istegi kabul/red icin repository. DiscoverRepository ile AYNI gerekce
 * ile Room cache YOK (canli veri, offline-first ihtiyaci yok).
 */
class ProfileRepository(
    private val profileApi: ProfileApi,
) {

    suspend fun getProfile(username: String): ProfileResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.getProfile(username)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ProfileResult.Success(body)
            } else {
                ProfileResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ProfileResult.Error("network_error")
        } catch (e: Exception) {
            ProfileResult.Error("unknown_error")
        }
    }

    suspend fun getFollowers(username: String): FollowListResult =
        fetchFollowList { profileApi.getFollowers(username) }

    suspend fun getFollowing(username: String): FollowListResult =
        fetchFollowList { profileApi.getFollowing(username) }

    private suspend fun fetchFollowList(
        call: suspend () -> Response<FollowListResponse>,
    ): FollowListResult = withContext(Dispatchers.IO) {
        try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                FollowListResult.Success(body.users ?: emptyList(), body.title)
            } else {
                FollowListResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            FollowListResult.Error("network_error")
        } catch (e: Exception) {
            FollowListResult.Error("unknown_error")
        }
    }

    suspend fun getInsights(days: Int): InsightsResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.getInsights(days)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                InsightsResult.Success(body)
            } else {
                InsightsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            InsightsResult.Error("network_error")
        } catch (e: Exception) {
            InsightsResult.Error("unknown_error")
        }
    }

    suspend fun toggleFollow(username: String): ToggleFollowResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.toggleFollow(username)
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                ToggleFollowResult.Success(body.following, body.followersCount, body.isPending)
            } else {
                ToggleFollowResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleFollowResult.Error("network_error")
        } catch (e: Exception) {
            ToggleFollowResult.Error("unknown_error")
        }
    }

    suspend fun getFollowRequests(): FollowRequestsResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.getFollowRequests()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                FollowRequestsResult.Success(body.users ?: emptyList())
            } else {
                FollowRequestsResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            FollowRequestsResult.Error("network_error")
        } catch (e: Exception) {
            FollowRequestsResult.Error("unknown_error")
        }
    }

    suspend fun acceptFollowRequest(followerId: String): FollowActionResult =
        runAction { profileApi.acceptFollowRequest(followerId) }

    suspend fun rejectFollowRequest(followerId: String): FollowActionResult =
        runAction { profileApi.rejectFollowRequest(followerId) }

    /** POST /block/{username} toggle — dönen `blocked` alanı YENİ durumu
     * taşır, çağıran taraf (ProfileViewModel/BlockedUsersViewModel) bunu
     * yorumlar (bkz. ilgili ViewModel yorumları). */
    suspend fun toggleBlock(username: String): ToggleBlockResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.toggleBlock(username)
            val body = response.body()
            if (response.isSuccessful && body?.ok == true) {
                ToggleBlockResult.Success(body.blocked)
            } else {
                ToggleBlockResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            ToggleBlockResult.Error("network_error")
        } catch (e: Exception) {
            ToggleBlockResult.Error("unknown_error")
        }
    }

    suspend fun getBlockedUsers(): BlockedUsersResult = withContext(Dispatchers.IO) {
        try {
            val response = profileApi.getBlockedUsers()
            val body = response.body()
            if (response.isSuccessful && body != null && body.error == null) {
                BlockedUsersResult.Success(body.users ?: emptyList())
            } else {
                BlockedUsersResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            BlockedUsersResult.Error("network_error")
        } catch (e: Exception) {
            BlockedUsersResult.Error("unknown_error")
        }
    }

    /** DiscoverRepository.runAction ile AYNI desen - ok/error sekilli tum
     * ikincil eylem endpoint'leri icin tek ortak yol. */
    private suspend fun runAction(call: suspend () -> Response<SimpleOkResponse>): FollowActionResult =
        withContext(Dispatchers.IO) {
            try {
                val response = call()
                val body = response.body()
                if (response.isSuccessful && body?.ok == true) {
                    FollowActionResult.Success
                } else {
                    FollowActionResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
                }
            } catch (e: IOException) {
                FollowActionResult.Error("network_error")
            } catch (e: Exception) {
                FollowActionResult.Error("unknown_error")
            }
        }
}
