package com.umuterayaltay.sosyal.nativeapp.repository

import com.umuterayaltay.sosyal.nativeapp.network.PollsApi
import com.umuterayaltay.sosyal.nativeapp.network.RetrofitClient
import com.umuterayaltay.sosyal.nativeapp.network.VotePollRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Sunucudan dönen GÜNCEL anket durumunu ilgili posta yazar, diğer postlara
 * DOKUNMAZ. 5 ViewModel (Feed/Discover/Hashtag/Profile/PostDetail) aynı
 * `_posts.value.map { ... }` bloğunu kopyalamasın diye burada tek yerde
 * tanımlı — anket şekli değişirse tek noktadan güncellenir.
 */
fun Post.applyVote(postId: String, result: VotePollResult.Success): Post =
    if (id == postId && poll != null) {
        copy(
            poll = poll.copy(
                options = result.options,
                totalVotes = result.totalVotes,
                myVote = result.myVote,
            )
        )
    } else {
        this
    }

sealed class VotePollResult {
    data class Success(
        val options: List<PollOption>,
        val totalVotes: Int,
        val myVote: String?,
    ) : VotePollResult()

    data class Error(val code: String?) : VotePollResult()
}

/**
 * Anket oylaması — HashtagRepository'deki AYNI desen (Room cache YOK; anket
 * dinamik veri, offline gösterimi anlamsız — bkz. PostEntity.toDomain()
 * yorumu).
 *
 * OPTIMISTIC UI YOK: 3-yönlü toggle'da (sil/değiştir/ekle) yüzdeleri client'ta
 * yeniden hesaplamak hataya çok açık, backend zaten hesaplanmış GÜNCEL durumu
 * döndürüyor — ViewModel dönen değeri olduğu gibi posta yazar.
 */
class PollsRepository(
    private val pollsApi: PollsApi,
) {

    suspend fun vote(pollId: String, optionId: String): VotePollResult = withContext(Dispatchers.IO) {
        try {
            val response = pollsApi.votePoll(pollId, VotePollRequest(optionId = optionId))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                VotePollResult.Success(
                    options = (body.options ?: emptyList()).map {
                        PollOption(id = it.id, text = it.text, votes = it.votes, pct = it.pct)
                    },
                    totalVotes = body.totalVotes,
                    myVote = body.myVote,
                )
            } else {
                VotePollResult.Error(body?.error ?: RetrofitClient.parseErrorCode(response))
            }
        } catch (e: IOException) {
            VotePollResult.Error("network_error")
        } catch (e: Exception) {
            VotePollResult.Error("unknown_error")
        }
    }
}
